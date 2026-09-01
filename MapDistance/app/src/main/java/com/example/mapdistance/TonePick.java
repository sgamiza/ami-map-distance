package com.example.mapdistance;

import android.content.Context;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 系统闹钟/通知/来电铃声。不走小米那个容易变成「手机文件/MP4」的选择器。 */
final class TonePick {
    static final class Item {
        final String label;
        final Uri uri;
        final boolean pickFile;
        final boolean silent;

        Item(String label, Uri uri, boolean pickFile, boolean silent) {
            this.label = label;
            this.uri = uri;
            this.pickFile = pickFile;
            this.silent = silent;
        }
    }

    private TonePick() {}

    static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addDefault(out, seen, "系统闹钟（默认）", RingtoneManager.TYPE_ALARM);
        addDefault(out, seen, "系统通知（默认）", RingtoneManager.TYPE_NOTIFICATION);
        addDefault(out, seen, "系统来电（默认）", RingtoneManager.TYPE_RINGTONE);
        addType(c, out, seen, RingtoneManager.TYPE_ALARM, "闹钟");
        addType(c, out, seen, RingtoneManager.TYPE_NOTIFICATION, "通知");
        addType(c, out, seen, RingtoneManager.TYPE_RINGTONE, "来电");
        out.add(new Item("从文件选（自己的音频）…", null, true, false));
        out.add(new Item("静音", null, false, true));
        return out;
    }

    private static void addDefault(List<Item> out, Set<String> seen, String label, int type) {
        Uri uri = RingtoneManager.getDefaultUri(type);
        if (uri == null) {
            return;
        }
        seen.add(uri.toString());
        out.add(new Item(label, uri, false, false));
    }

    private static void addType(Context c, List<Item> out, Set<String> seen, int type, String prefix) {
        Cursor cur = null;
        try {
            RingtoneManager rm = new RingtoneManager(c);
            rm.setType(type);
            cur = rm.getCursor();
            if (cur == null) {
                return;
            }
            int n = cur.getCount();
            for (int i = 0; i < n; i++) {
                Uri uri = rm.getRingtoneUri(i);
                if (uri == null) {
                    continue;
                }
                String key = uri.toString();
                if (!seen.add(key)) {
                    continue;
                }
                String title = "铃声";
                if (cur.moveToPosition(i)) {
                    String t = cur.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                    if (t != null && !t.trim().isEmpty()) {
                        title = t.trim();
                    }
                }
                out.add(new Item(prefix + " · " + title, uri, false, false));
            }
        } catch (Exception ignored) {
        }
    }
}
