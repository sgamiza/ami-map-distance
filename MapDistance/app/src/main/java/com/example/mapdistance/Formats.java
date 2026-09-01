package com.example.mapdistance;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Formats {
    private Formats() {}

    public static String distance(double meters) {
        if (meters < 1000) {
            return String.format(Locale.CHINA, "%.0f 米", meters);
        }
        return String.format(Locale.CHINA, "%.2f 公里", meters / 1000.0);
    }

    public static String duration(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format(Locale.CHINA, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(Locale.CHINA, "%02d:%02d", m, sec);
    }

    public static final String UNIT_KMH = "kmh";
    public static final String UNIT_MS = "ms";
    public static final String UNIT_MPH = "mph";

    /**
     * 配速：走完 1 公里花的时间。例如 11分17秒/公里 ≈ 时速 5.3 km/h。
     * 跑步表盘常写成 11'17"，这里改成中文，避免看成奇怪的速度单位。
     */
    public static String pace(double meters, long movingMs) {
        if (meters < 20 || movingMs < 1000) {
            return "--";
        }
        double secPerKm = (movingMs / 1000.0) / (meters / 1000.0);
        if (secPerKm < 36 || secPerKm > 3600) {
            return "--";
        }
        int min = (int) (secPerKm / 60);
        int sec = (int) Math.round(secPerKm % 60);
        if (sec == 60) {
            min += 1;
            sec = 0;
        }
        return String.format(Locale.CHINA, "%d分%02d秒/公里", min, sec);
    }

    public static String speed(double metersPerSec, String unit) {
        if (metersPerSec < 0 || Double.isNaN(metersPerSec)) {
            metersPerSec = 0;
        }
        if (UNIT_MS.equals(unit)) {
            return String.format(Locale.CHINA, "%.2f m/s", metersPerSec);
        }
        if (UNIT_MPH.equals(unit)) {
            return String.format(Locale.CHINA, "%.1f mph", metersPerSec * 2.236936);
        }
        return String.format(Locale.CHINA, "%.1f km/h", metersPerSec * 3.6);
    }

    public static String speed(android.content.Context c, double metersPerSec) {
        return speed(metersPerSec, Prefs.speedUnit(c));
    }

    public static String speedKmh(double metersPerSec) {
        return speed(metersPerSec, UNIT_KMH);
    }

    public static String nextSpeedUnit(String unit) {
        if (UNIT_KMH.equals(unit)) {
            return UNIT_MS;
        }
        if (UNIT_MS.equals(unit)) {
            return UNIT_MPH;
        }
        return UNIT_KMH;
    }

    public static String speedUnitLabel(String unit) {
        if (UNIT_MS.equals(unit)) {
            return "米/秒";
        }
        if (UNIT_MPH.equals(unit)) {
            return "英里/时";
        }
        return "公里/时";
    }

    public static int speedUnitButtonId(String unit) {
        if (UNIT_MS.equals(unit)) {
            return R.id.unit_ms;
        }
        if (UNIT_MPH.equals(unit)) {
            return R.id.unit_mph;
        }
        return R.id.unit_kmh;
    }

    public static String speedUnitFromButton(int buttonId) {
        if (buttonId == R.id.unit_ms) {
            return UNIT_MS;
        }
        if (buttonId == R.id.unit_mph) {
            return UNIT_MPH;
        }
        return UNIT_KMH;
    }

    public static String steps(int n) {
        if (n <= 0) {
            return "0 步";
        }
        return String.format(Locale.CHINA, "%,d 步", n);
    }

    public static String strideCm(double meters, int steps) {
        if (steps < 5 || meters < 5) {
            return "--";
        }
        double cm = meters / steps * 100.0;
        if (cm < 8 || cm > 250) {
            return "--";
        }
        return String.format(Locale.CHINA, "步幅 %.0f 厘米", cm);
    }

    /** 测量页常驻：有这次实测用这次，否则用最近一次走完记下的。骑车不按步幅估。 */
    public static String strideAlways(android.content.Context c, double meters, int steps,
                                      String mode) {
        if (TrackEngine.isRide(mode)) {
            return "骑车不计步幅";
        }
        String live = strideCm(meters, steps);
        if (!"--".equals(live)) {
            return live;
        }
        float last = c == null ? 0.70f : Prefs.lastStrideM(c);
        return String.format(Locale.CHINA, "步幅 %.0f 厘米", last * 100f);
    }

    public static String tripSteps(double meters, int steps) {
        if (steps <= 0) {
            return "步数 --（走几步就会出现）";
        }
        return steps(steps) + " · " + distance(meters);
    }

    public static String kcal(double kcal) {
        if (kcal < 0.5) {
            return "--";
        }
        return String.format(Locale.CHINA, "%.0f kcal", kcal);
    }

    public static String when(long ms) {
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(ms));
    }

    public static String clock(long ms) {
        return new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(ms));
    }

    public static String modeLabel(String mode) {
        if (TrackEngine.MODE_RUN.equals(mode)) {
            return "跑步";
        }
        if (TrackEngine.MODE_RIDE.equals(mode)) {
            return "骑车";
        }
        if (TrackEngine.MODE_AUTO.equals(mode)) {
            return "自动";
        }
        return "走路";
    }

    public static String cadence(double spm) {
        if (spm < 20 || spm > 260 || Double.isNaN(spm)) {
            return "--";
        }
        return String.format(Locale.CHINA, "%.0f 步/分", spm);
    }

    public static String autoKindLabel(int kind) {
        if (kind == TrackPoint.KIND_WALK) {
            return "走路";
        }
        if (kind == TrackPoint.KIND_VEHICLE) {
            return "车程";
        }
        return "识别中";
    }

    /** 测量页自动模式：走路一段 vs 没步数的移动。 */
    public static String autoPanel(android.content.Context c, TrackSession s, float nowMps,
                                  int liveKind) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        b.append("现在：").append(autoKindLabel(liveKind));
        double liveCad = s.cadenceSpm > 8 ? s.cadenceSpm : s.walkCadenceSpm();
        if (liveKind == TrackPoint.KIND_WALK && liveCad >= 20) {
            b.append("  ·  步频 ").append(cadence(liveCad));
        }
        b.append('\n');
        b.append("走路  ").append(distance(s.walkDistanceM))
                .append("  ·  ").append(duration(s.walkMovingMs))
                .append("  ·  均速 ").append(speed(c, s.walkAvgMps()));
        if (s.walkSteps > 0) {
            b.append("  ·  ").append(steps(s.walkSteps));
        } else {
            b.append("  ·  步数 --");
        }
        if (s.walkCadenceSpm() >= 20) {
            b.append("  ·  ").append(cadence(s.walkCadenceSpm()));
        }
        b.append('\n');
        b.append("车程  ").append(distance(s.vehicleDistanceM))
                .append("  ·  ").append(duration(s.vehicleMovingMs))
                .append("  ·  均速 ").append(speed(c, s.vehicleAvgMps()));
        if (liveKind == TrackPoint.KIND_VEHICLE && nowMps >= 0.3f) {
            b.append("  ·  现在 ").append(speed(c, nowMps));
        }
        return b.toString();
    }

    public static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    public static String headline(TrackSession s) {
        String name = nz(s.title);
        String body;
        if (TrackEngine.isAuto(s.mode)) {
            body = "走路 " + distance(s.walkDistanceM) + "  ·  " + duration(s.walkMovingMs);
            if (s.walkSteps > 0) {
                body += "  ·  " + steps(s.walkSteps);
            }
            if (s.vehicleDistanceM >= 20) {
                body += "  /  车程 " + distance(s.vehicleDistanceM);
            }
        } else {
            body = distance(s.distanceM) + "  ·  " + duration(s.movingMs);
            if (s.steps > 0) {
                body += "  ·  " + steps(s.steps);
            }
        }
        String core;
        if (name.isEmpty()) {
            core = modeLabel(s.mode) + "  " + body;
        } else {
            core = name + "  " + body;
        }
        String tag = originTag(s);
        return tag.isEmpty() ? core : "[" + tag + "] " + core;
    }

    /** 空=本机测的；同步/备份恢复才返回文案。 */
    public static String originTag(TrackSession s) {
        if (s == null) {
            return "";
        }
        if (TrackSession.ORIGIN_SYNC.equals(s.origin)) {
            return "来自同步";
        }
        if (TrackSession.ORIGIN_RESTORE.equals(s.origin)) {
            return "来自备份";
        }
        return "";
    }

    public static String routeLine(TrackSession s) {
        String a = nz(s.fromPlace);
        String b = nz(s.toPlace);
        if (!a.isEmpty() && !b.isEmpty()) {
            return a + " → " + b;
        }
        if (!a.isEmpty()) {
            return "从 " + a + " 出发";
        }
        if (!b.isEmpty()) {
            return "到 " + b;
        }
        return "";
    }

    public static String suggestPlace(String user, String geo) {
        String u = nz(user);
        if (!u.isEmpty()) {
            return u;
        }
        if (!needsGeocode(geo)) {
            return geo.trim();
        }
        return "";
    }

    public static String markTitle(Checkpoint m) {
        if (m == null) {
            return "点";
        }
        return (m.auto ? "自动 " : "手动 ") + m.n;
    }

    public static String markBody(Checkpoint m) {
        StringBuilder b = new StringBuilder();
        b.append(m.auto ? "自动打点" : "手动打点").append('\n');
        b.append("到这里 ").append(distance(m.distanceM))
                .append("，用时 ").append(duration(m.movingMs));
        if (m.n > 1) {
            b.append("\n距上一点 ").append(distance(m.sinceLastM))
                    .append("，").append(duration(m.sinceLastMs));
        }
        if (m.addr != null && !m.addr.isEmpty() && !needsGeocode(m.addr)) {
            b.append('\n').append(m.addr);
        }
        if (m.t > 0) {
            b.append('\n').append(when(m.t));
        }
        return b.toString();
    }

    public static String marksDialog(java.util.List<Checkpoint> marks) {
        if (marks == null || marks.isEmpty()) {
            return "这次还没有打点。测量中走到地方点「打点」，或在设置里打开自动打点。";
        }
        StringBuilder b = new StringBuilder();
        if (marks.size() >= 2) {
            b.append("下面每条仍写「距上一点」。要点任意两个打点之间的距离、用时、均速、步幅，按「选两个打点」。\n\n");
        }
        for (int i = 0; i < marks.size(); i++) {
            if (i > 0) {
                b.append("\n\n");
            }
            Checkpoint m = marks.get(i);
            b.append(markTitle(m)).append('\n').append(markBody(m));
        }
        return b.toString();
    }

    /** 历史列表 / 结束确认：手动和自动分开写。 */
    public static String marksCountLine(java.util.List<Checkpoint> marks) {
        if (marks == null || marks.isEmpty()) {
            return "";
        }
        int hand = 0;
        int auto = 0;
        for (Checkpoint m : marks) {
            if (m.auto) {
                auto++;
            } else {
                hand++;
            }
        }
        if (auto == 0) {
            return "手动 " + hand + " 个打点";
        }
        if (hand == 0) {
            return "自动 " + auto + " 个打点";
        }
        return "打点 手动" + hand + " · 自动" + auto;
    }

    /** 空、或以前离线时写进去的「纬度, 经度」占位，联网后要重新查地址。 */
    public static boolean needsGeocode(String addr) {
        if (addr == null) {
            return true;
        }
        String s = addr.trim();
        if (s.isEmpty() || "[]".equals(s)) {
            return true;
        }
        return s.matches("^-?\\d+\\.\\d+,\\s*-?\\d+\\.\\d+$");
    }
}
