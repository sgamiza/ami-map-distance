package com.example.mapdistance;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 多机交换轨迹：带设备备注的 JSON 包，按 uid 合并，已有的不重复。 */
public final class SyncPack {
    private static final long AUTO_GAP_MS = 45_000L;

    private SyncPack() {
    }

    public static final class Meta {
        public String deviceId = "";
        public String nick = "";
        public String model = "";
    }

    public static final class Delta {
        public int added;
        public int skipped;
    }

    public static String fileName(Context ctx) {
        return "sync_" + Prefs.deviceId(ctx) + ".json";
    }

    public static boolean hasFolder(Context ctx) {
        String t = Prefs.backupTree(ctx);
        return t != null && !t.isEmpty();
    }

    public static String statusText(Context ctx) {
        String last = Prefs.lastSyncText(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("多机同步：同一 WiFi / 热点或蓝牙点「附近同步」即可，不用网盘。");
        if (hasFolder(ctx)) {
            sb.append(" 也可共用备份文件夹。");
        }
        if (last != null && !last.isEmpty()) {
            sb.append(" 最近：").append(last);
        }
        return sb.toString();
    }

    public static String infoText(Context ctx) {
        return "id=" + Prefs.deviceId(ctx)
                + "\nnick=" + PhoneNotes.selfName(ctx)
                + "\nmodel=" + (Build.MODEL == null ? "" : Build.MODEL.trim())
                + "\n";
    }

    public static String taggedJson(Context ctx, TrackStore store) {
        store.ensureUids();
        JSONObject root = new JSONObject();
        try {
            root.put("v", 1);
            root.put("device", Prefs.deviceId(ctx));
            root.put("nick", PhoneNotes.selfName(ctx));
            root.put("model", model());
            JSONArray arr = new JSONArray();
            for (TrackSession s : store.list()) {
                arr.put(s.toJson());
            }
            root.put("sessions", arr);
        } catch (Exception ignored) {
        }
        String model = model();
        String nick = PhoneNotes.selfName(ctx);
        String tag = "# amiceju-sync device=" + Prefs.deviceId(ctx)
                + " model=" + model
                + " nick=" + nick + "\n";
        return tag + root.toString();
    }

    public static Meta parseMeta(String text) {
        Meta m = new Meta();
        if (text == null || text.isEmpty()) {
            return m;
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String raw : lines) {
            String t = raw.trim();
            if (t.startsWith("#")) {
                t = t.substring(1).trim();
            }
            if (t.startsWith("amiceju-sync ")) {
                t = t.substring("amiceju-sync ".length()).trim();
                int nickAt = t.indexOf(" nick=");
                int modelAt = t.indexOf(" model=");
                int deviceAt = t.indexOf("device=");
                if (deviceAt >= 0) {
                    int end = modelAt >= 0 ? modelAt : (nickAt >= 0 ? nickAt : t.length());
                    m.deviceId = t.substring(deviceAt + 7, end).trim();
                }
                if (modelAt >= 0) {
                    int end = nickAt >= 0 ? nickAt : t.length();
                    m.model = t.substring(modelAt + 7, end).trim();
                }
                if (nickAt >= 0) {
                    m.nick = t.substring(nickAt + 6).trim();
                }
                continue;
            }
            if (t.startsWith("id=")) {
                m.deviceId = t.substring(3).trim();
            } else if (t.startsWith("device=")) {
                m.deviceId = t.substring(7).trim();
            } else if (t.startsWith("nick=")) {
                m.nick = t.substring(5).trim();
            } else if (t.startsWith("model=")) {
                m.model = t.substring(6).trim();
            }
        }
        JSONObject json = extractJson(text);
        if (json != null) {
            if (m.deviceId.isEmpty()) {
                m.deviceId = json.optString("device", "");
            }
            if (m.nick.isEmpty()) {
                m.nick = json.optString("nick", "");
            }
            if (m.model.isEmpty()) {
                m.model = json.optString("model", "");
            }
        }
        return m;
    }

    public static List<TrackSession> parseSessions(String text) {
        List<TrackSession> incoming = new ArrayList<>();
        JSONObject root = extractJson(text);
        if (root == null) {
            return incoming;
        }
        JSONArray arr = root.optJSONArray("sessions");
        if (arr == null) {
            return incoming;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) {
                incoming.add(TrackSession.fromJson(o));
            }
        }
        return incoming;
    }

    public static Delta mergeJson(TrackStore store, String text) {
        TrackStore.Delta d = store.mergeIncoming(parseSessions(text));
        Delta out = new Delta();
        out.added = d.added;
        out.skipped = d.skipped;
        return out;
    }

    public static String mergeResult(Delta d, TrackStore store, String from) {
        StringBuilder sb = new StringBuilder();
        sb.append("已与 ").append(from).append(" 同步");
        if (d.added > 0) {
            sb.append("，新增 ").append(d.added).append(" 段");
        } else {
            sb.append("（没有新记录）");
        }
        if (d.skipped > 0) {
            sb.append("，跳过已有 ").append(d.skipped).append(" 段");
        }
        sb.append("。本机现有 ").append(store.count()).append(" 段。");
        return sb.toString();
    }

    public static String syncFolder(Context ctx, TrackStore store, boolean force) {
        if (!hasFolder(ctx)) {
            return "还没选共享文件夹。设置里点「同步文件夹」，各手机都选同一个能互相看到的目录。";
        }
        if (!force) {
            long last = Prefs.lastSyncMs(ctx);
            if (last > 0 && System.currentTimeMillis() - last < AUTO_GAP_MS) {
                String prev = Prefs.lastSyncText(ctx);
                return prev == null || prev.isEmpty() ? "刚刚已同步" : prev;
            }
        }
        try {
            store.ensureUids();
            String mine = fileName(ctx);
            List<SyncFolder.TreeDoc> files = SyncFolder.listTree(ctx, "sync_", ".json");
            int phones = 0;
            int added = 0;
            int skipped = 0;
            int failed = 0;
            for (SyncFolder.TreeDoc doc : files) {
                if (mine.equals(doc.name)) {
                    continue;
                }
                phones++;
                try {
                    byte[] bytes = SyncFolder.readTree(ctx, doc.uri);
                    Delta d = mergeJson(store, decode(bytes));
                    added += d.added;
                    skipped += d.skipped;
                } catch (Exception e) {
                    failed++;
                }
            }
            String extra = "";
            if (store.count() > 0) {
                extra = SyncFolder.writeToTree(ctx, taggedJson(ctx, store), mine);
            }
            String msg;
            if (phones == 0) {
                msg = "已把本机轨迹写成 " + mine
                        + "。其他手机选同一文件夹并同步后，就能看到这边的记录。";
            } else {
                msg = "已从 " + phones + " 台手机合并"
                        + (added > 0 ? "，新增 " + added + " 段" : "（没有新记录）")
                        + (skipped > 0 ? "，跳过已有 " + skipped + " 段" : "")
                        + "。本机现有 " + store.count() + " 段，已写回 " + mine + "。";
                if (failed > 0) {
                    msg += " 有 " + failed + " 个文件读失败，网盘刷完后再同步一次。";
                }
            }
            if (extra != null && extra.contains("失败")) {
                msg += extra;
            }
            Prefs.setLastSync(ctx, System.currentTimeMillis(), msg);
            return msg;
        } catch (Exception e) {
            String fail = "同步失败: " + e.getMessage();
            Prefs.setLastSync(ctx, System.currentTimeMillis(), fail);
            return fail;
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    public static String decode(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static JSONObject extractJson(String text) {
        if (text == null) {
            return null;
        }
        int brace = text.indexOf('{');
        if (brace < 0) {
            return null;
        }
        try {
            return new JSONObject(text.substring(brace));
        } catch (Exception e) {
            return null;
        }
    }

    private static String model() {
        String model = Build.MODEL == null ? "" : Build.MODEL.replace('\n', ' ').trim();
        if (model.length() > 40) {
            model = model.substring(0, 40);
        }
        return model;
    }
}
