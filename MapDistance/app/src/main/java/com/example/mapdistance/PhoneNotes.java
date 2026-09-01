package com.example.mapdistance;

import android.content.Context;
import android.os.Build;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 本机显示名，以及给 WiFi/蓝牙对端做的本地备注。 */
final class PhoneNotes {
    private PhoneNotes() {
    }

    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > 24) {
            s = s.substring(0, 24);
        }
        return s;
    }

    static String selfName(Context ctx) {
        String n = sanitize(Prefs.phoneNick(ctx));
        if (!n.isEmpty()) {
            return n;
        }
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        return model.isEmpty() ? "未命名手机" : model;
    }

    static String display(Context ctx, String advertised, String fallback, String... keys) {
        String note = note(ctx, keys);
        if (!note.isEmpty()) {
            return note;
        }
        String nick = sanitize(advertised);
        if (!nick.isEmpty()) {
            return nick;
        }
        return fallback == null || fallback.isEmpty() ? "未知手机" : fallback;
    }

    static String note(Context ctx, String... keys) {
        JSONObject book = Prefs.peerNotes(ctx);
        if (keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            String v = book.optString(key, "");
            if (v != null && !v.trim().isEmpty()) {
                return sanitize(v);
            }
        }
        return "";
    }

    static void setNote(Context ctx, String note, String... keys) {
        String n = sanitize(note);
        JSONObject book = Prefs.peerNotes(ctx);
        try {
            if (keys != null) {
                for (String key : keys) {
                    if (key == null || key.isEmpty()) {
                        continue;
                    }
                    if (n.isEmpty()) {
                        book.remove(key);
                    } else {
                        book.put(key, n);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        Prefs.setPeerNotes(ctx, book);
    }

    static void remember(Context ctx, SyncPack.Meta meta, String extraKey) {
        if (meta == null) {
            return;
        }
        String idKey = (meta.deviceId == null || meta.deviceId.isEmpty())
                ? "" : "id:" + meta.deviceId;
        String existing = note(ctx, idKey, extraKey);
        if (existing.isEmpty()) {
            existing = sanitize(meta.nick);
        }
        if (existing.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        if (!idKey.isEmpty()) {
            keys.add(idKey);
        }
        if (extraKey != null && !extraKey.isEmpty()) {
            keys.add(extraKey);
        }
        String already = note(ctx, keys.toArray(new String[0]));
        if (!already.isEmpty()) {
            setNote(ctx, already, keys.toArray(new String[0]));
            return;
        }
        setNote(ctx, existing, keys.toArray(new String[0]));
    }

    static String bookSummary(Context ctx) {
        JSONObject book = Prefs.peerNotes(ctx);
        List<String> names = new ArrayList<>();
        Iterator<String> it = book.keys();
        while (it.hasNext()) {
            String v = sanitize(book.optString(it.next(), ""));
            if (!v.isEmpty() && !names.contains(v)) {
                names.add(v);
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("已备注：");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(names.get(i));
        }
        return sb.toString();
    }
}
