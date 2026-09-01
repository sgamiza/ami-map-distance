package com.example.mapdistance;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    /** 高德 Web 服务 Key，用来做逆地理编码。仓库里是占位符，可在设置里改成本机 Key。 */
    public static final String DEFAULT_AMAP_KEY = "YOUR_AMAP_WEB_KEY";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("mapdistance", Context.MODE_PRIVATE);
    }

    public static boolean agreedPrivacy(Context c) {
        return sp(c).getBoolean("privacy", false);
    }

    public static void setAgreedPrivacy(Context c, boolean v) {
        sp(c).edit().putBoolean("privacy", v).apply();
    }

    /** 新 key，旧版默认开过的不再沿用。打开后自动开始默认关。 */
    public static boolean autoStart(Context c) {
        return sp(c).getBoolean("open_autostart", false);
    }

    public static void setAutoStart(Context c, boolean v) {
        sp(c).edit().putBoolean("open_autostart", v).apply();
    }

    public static String speedUnit(Context c) {
        String u = sp(c).getString("speed_unit", Formats.UNIT_KMH);
        if (Formats.UNIT_MS.equals(u) || Formats.UNIT_MPH.equals(u)) {
            return u;
        }
        return Formats.UNIT_KMH;
    }

    public static void setSpeedUnit(Context c, String unit) {
        if (!Formats.UNIT_MS.equals(unit) && !Formats.UNIT_MPH.equals(unit)) {
            unit = Formats.UNIT_KMH;
        }
        sp(c).edit().putString("speed_unit", unit).apply();
    }

    public static float weightJin(Context c) {
        return sp(c).getFloat("weight_jin", 130f);
    }

    public static void setWeightJin(Context c, float v) {
        sp(c).edit().putFloat("weight_jin", v).apply();
    }

    public static int maxAccuracyM(Context c) {
        return sp(c).getInt("max_acc", 40);
    }

    public static void setMaxAccuracyM(Context c, int v) {
        sp(c).edit().putInt("max_acc", v).apply();
    }

    public static String amapKey(Context c) {
        return sp(c).getString("amap_key", DEFAULT_AMAP_KEY);
    }

    public static void setAmapKey(Context c, String v) {
        sp(c).edit().putString("amap_key", v == null ? DEFAULT_AMAP_KEY : v.trim()).apply();
    }

    public static String lastMode(Context c) {
        return sp(c).getString("mode", TrackEngine.MODE_WALK);
    }

    public static void setLastMode(Context c, String v) {
        sp(c).edit().putString("mode", v).apply();
    }

    public static String draftJson(Context c) {
        return sp(c).getString("draft", "");
    }

    public static void setDraftJson(Context c, String json) {
        if (json == null || json.isEmpty()) {
            sp(c).edit().remove("draft").apply();
        } else {
            sp(c).edit().putString("draft", json).apply();
        }
    }

    /** 小米省电策略 / 华为应用启动等系统不给读的项，用户确认已经设过。 */
    public static boolean oemKeepAliveOk(Context c) {
        return sp(c).getBoolean("oem_keepalive_ok", false);
    }

    public static void setOemKeepAliveOk(Context c, boolean v) {
        sp(c).edit().putBoolean("oem_keepalive_ok", v).apply();
    }

    public static String deviceId(Context c) {
        String id = sp(c).getString("device_id", "");
        if (id != null && !id.isEmpty()) {
            return id;
        }
        String androidId = android.provider.Settings.Secure.getString(
                c.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty() || "9774d56d682e549c".equals(androidId)) {
            id = java.util.UUID.randomUUID().toString().replace("-", "");
        } else {
            id = androidId.toLowerCase(java.util.Locale.US);
        }
        if (id.length() > 16) {
            id = id.substring(0, 16);
        }
        sp(c).edit().putString("device_id", id).apply();
        return id;
    }

    public static long lastSyncMs(Context c) {
        return sp(c).getLong("last_sync_ms", 0L);
    }

    public static void setLastSync(Context c, long ms, String text) {
        sp(c).edit().putLong("last_sync_ms", ms)
                .putString("last_sync_text", text == null ? "" : text)
                .apply();
    }

    public static String lastSyncText(Context c) {
        return sp(c).getString("last_sync_text", "");
    }

    public static String phoneNick(Context c) {
        return sp(c).getString("phone_nick", "");
    }

    public static void setPhoneNick(Context c, String nick) {
        sp(c).edit().putString("phone_nick", nick == null ? "" : nick).apply();
    }

    public static org.json.JSONObject peerNotes(Context c) {
        String raw = sp(c).getString("peer_notes", "");
        if (raw == null || raw.isEmpty()) {
            return new org.json.JSONObject();
        }
        try {
            return new org.json.JSONObject(raw);
        } catch (Exception e) {
            return new org.json.JSONObject();
        }
    }

    public static void setPeerNotes(Context c, org.json.JSONObject book) {
        sp(c).edit().putString("peer_notes", book == null ? "{}" : book.toString()).apply();
    }

    public static String backupTree(Context c) {
        return sp(c).getString("backup_tree", "");
    }

    public static void setBackupTree(Context c, String uri) {
        sp(c).edit().putString("backup_tree", uri == null ? "" : uri).apply();
    }

    public static String backupFolderLabel(Context c) {
        return sp(c).getString("backup_folder_label", "");
    }

    public static void setBackupFolderLabel(Context c, String label) {
        sp(c).edit().putString("backup_folder_label", label == null ? "" : label).apply();
    }

    public static String stepDay(Context c) {
        return sp(c).getString("step_day", "");
    }

    public static long stepRaw(Context c) {
        return sp(c).getLong("step_raw", -1L);
    }

    public static int todaySteps(Context c) {
        return sp(c).getInt("step_today", 0);
    }

    public static void setSteps(Context c, String day, long raw, int today) {
        sp(c).edit()
                .putString("step_day", day == null ? "" : day)
                .putLong("step_raw", raw)
                .putInt("step_today", Math.max(0, today))
                .apply();
    }

    /** 最近一次有效测量算出的步幅，用来把今日步数估成公里。默认 0.70 米。 */
    public static float lastStrideM(Context c) {
        float v = sp(c).getFloat("stride_m", 0.70f);
        if (v < 0.35f || v > 1.20f) {
            return 0.70f;
        }
        return v;
    }

    public static void setLastStrideM(Context c, float meters) {
        if (meters >= 0.35f && meters <= 1.20f) {
            sp(c).edit().putFloat("stride_m", meters).apply();
        }
    }

    public static boolean autoMarkOn(Context c) {
        return sp(c).getBoolean("auto_mark", false);
    }

    public static int autoMarkMinStored(Context c) {
        int v = sp(c).getInt("auto_mark_min", 5);
        if (v < 1 || v > 30) {
            return 5;
        }
        return v;
    }

    /** 打开自动打点时返回分钟间隔，关掉时返回 0。 */
    public static int autoMarkMin(Context c) {
        return autoMarkOn(c) ? autoMarkMinStored(c) : 0;
    }

    public static void setAutoMark(Context c, boolean on, int min) {
        if (min < 1 || min > 30) {
            min = autoMarkMinStored(c);
        }
        sp(c).edit().putBoolean("auto_mark", on).putInt("auto_mark_min", min).apply();
    }
}
