package com.example.mapdistance;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.util.Locale;

/**
 * 整机电量快照。第三方读不到「只有本 App」的 mAh，锁屏走路时
 * 多半就是 GPS + 唤醒在耗，所以用本段开始/现在的整机差值。
 */
public final class BatterySnap {
    public int pct = -1;
    public long uah = -1;
    public boolean charging;
    public long atMs;

    private static volatile BatterySnap openSnap;

    private BatterySnap() {}

    public static void markAppOpen(Context c) {
        if (openSnap == null) {
            openSnap = now(c);
        }
    }

    public static BatterySnap now(Context c) {
        BatterySnap s = new BatterySnap();
        s.atMs = System.currentTimeMillis();
        Context app = c.getApplicationContext();
        Intent i = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i != null) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) {
                s.pct = Math.round(level * 100f / scale);
            }
            int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            s.charging = plugged > 0
                    || status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }
        BatteryManager bm = (BatteryManager) app.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            try {
                int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (cap >= 0 && cap <= 100) {
                    s.pct = cap;
                }
            } catch (Exception ignored) {
            }
            try {
                s.uah = normalizeUah(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
            } catch (Exception ignored) {
            }
        }
        return s;
    }

    public static void captureStart(TrackSession s, Context c) {
        BatterySnap b = now(c);
        s.battStartPct = b.pct;
        s.battStartUah = b.uah;
        s.battEndPct = -1;
        s.battEndUah = -1;
        s.battSawCharge = b.charging;
    }

    public static void captureEnd(TrackSession s, Context c) {
        BatterySnap b = now(c);
        s.battEndPct = b.pct;
        s.battEndUah = b.uah;
        if (b.charging) {
            s.battSawCharge = true;
        }
    }

    public static void noteLive(TrackSession s, Context c) {
        if (s == null) {
            return;
        }
        BatterySnap b = now(c);
        if (b.charging) {
            s.battSawCharge = true;
        }
    }

    /** 锁屏折叠行：电量-3% / 电量不到1% / 充电中 */
    public static String sessionShort(Context c, TrackSession s) {
        if (s == null || s.battStartPct < 0) {
            return "";
        }
        BatterySnap end = now(c);
        if (end.charging) {
            return "充电中";
        }
        int d = s.battStartPct - end.pct;
        if (d > 0) {
            return "电量-" + d + "%";
        }
        if (d == 0) {
            return "电量不到1%";
        }
        return "电量升了";
    }

    public static String sessionLine(Context c, TrackSession s) {
        if (s == null || s.battStartPct < 0) {
            return "";
        }
        BatterySnap start = startOf(s);
        BatterySnap end;
        boolean live = TrackEngine.RUNNING.equals(s.state) || TrackEngine.PAUSED.equals(s.state);
        if (live) {
            if (c == null) {
                return "";
            }
            end = now(c);
        } else if (s.battEndPct >= 0) {
            end = endOf(s);
        } else {
            return "";
        }
        return describe("本段", start, end, s.battSawCharge || end.charging);
    }

    public static String sinceOpenLine(Context c) {
        BatterySnap start = openSnap;
        if (start == null || start.pct < 0) {
            return "";
        }
        return describe("打开后", start, now(c), false);
    }

    public static String savedShort(TrackSession s) {
        if (s == null || s.battStartPct < 0 || s.battEndPct < 0) {
            return "";
        }
        if (s.battSawCharge && s.battEndPct >= s.battStartPct) {
            return "充电中";
        }
        int d = s.battStartPct - s.battEndPct;
        if (d > 0) {
            return "电量-" + d + "%";
        }
        if (d == 0) {
            return "电量不到1%";
        }
        return "";
    }

    private static BatterySnap startOf(TrackSession s) {
        BatterySnap b = new BatterySnap();
        b.pct = s.battStartPct;
        b.uah = s.battStartUah;
        return b;
    }

    private static BatterySnap endOf(TrackSession s) {
        BatterySnap b = new BatterySnap();
        b.pct = s.battEndPct;
        b.uah = s.battEndUah;
        b.charging = s.battSawCharge;
        return b;
    }

    static String describe(String tag, BatterySnap start, BatterySnap end, boolean sawCharge) {
        if (start == null || end == null || start.pct < 0 || end.pct < 0) {
            return "";
        }
        if (end.charging || (sawCharge && end.pct >= start.pct)) {
            return tag + "充电中（现 " + end.pct + "%），不算耗电";
        }
        int d = start.pct - end.pct;
        StringBuilder b = new StringBuilder();
        b.append(tag).append("电量 ").append(start.pct).append("%→").append(end.pct).append("%");
        if (d > 0) {
            b.append("（-").append(d).append("%）");
        } else if (d == 0) {
            b.append("（不到 1%）");
        } else {
            b.append("（升了，可能在充电）");
            return b.toString();
        }
        String mah = mahPart(start, end);
        if (!mah.isEmpty()) {
            b.append(" · ").append(mah);
        }
        return b.toString();
    }

    private static String mahPart(BatterySnap start, BatterySnap end) {
        if (start.uah <= 0 || end.uah <= 0 || end.uah > start.uah) {
            return "";
        }
        double mah = (start.uah - end.uah) / 1000.0;
        if (mah >= 10) {
            return String.format(Locale.CHINA, "约 %.0f mAh", mah);
        }
        if (mah >= 1) {
            return String.format(Locale.CHINA, "约 %.0f mAh", mah);
        }
        if (mah >= 0.2) {
            return String.format(Locale.CHINA, "约 %.1f mAh", mah);
        }
        return "";
    }

    /** 各机型单位不一：µAh / mAh / nAh，归一成剩余 µAh。 */
    static long normalizeUah(long raw) {
        if (raw <= 0 || raw == Integer.MIN_VALUE || raw == Long.MIN_VALUE) {
            return -1;
        }
        if (raw >= 500_000_000L && raw <= 50_000_000_000L) {
            return raw / 1000L;
        }
        if (raw >= 500_000L && raw <= 50_000_000L) {
            return raw;
        }
        if (raw >= 400L && raw <= 20_000L) {
            return raw * 1000L;
        }
        return -1;
    }
}
