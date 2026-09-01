package com.example.mapdistance;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 历史列表：全文搜索 + 按时间/公里/步数/均速排序。 */
public final class HistoryQuery {
    public static final String SORT_TIME = "time";
    public static final String SORT_KM = "km";
    public static final String SORT_STEPS = "steps";
    public static final String SORT_SPEED = "speed";

    private HistoryQuery() {}

    public static boolean matches(TrackSession s, String query) {
        if (s == null) {
            return false;
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.CHINA);
        if (q.isEmpty()) {
            return true;
        }
        String blob = blob(s).toLowerCase(Locale.CHINA);
        String compact = blob.replace(",", "").replace(" ", "").replace("·", "");
        String[] toks = q.split("\\s+");
        for (String tok : toks) {
            if (tok.isEmpty()) {
                continue;
            }
            String needle = tok.replace(",", "");
            String tight = needle.replace(" ", "");
            if (!blob.contains(needle) && !compact.contains(tight)) {
                return false;
            }
        }
        return true;
    }

    public static int compare(TrackSession a, TrackSession b, String sort, boolean desc) {
        if (a == null || b == null) {
            return 0;
        }
        int c;
        if (SORT_KM.equals(sort)) {
            c = Double.compare(a.distanceM, b.distanceM);
        } else if (SORT_STEPS.equals(sort)) {
            c = Integer.compare(a.steps, b.steps);
        } else if (SORT_SPEED.equals(sort)) {
            c = Double.compare(a.avgSpeedMps(), b.avgSpeedMps());
        } else {
            c = Long.compare(a.startMs, b.startMs);
        }
        if (c == 0) {
            c = Long.compare(a.startMs, b.startMs);
        }
        if (c == 0) {
            c = Long.compare(a.id, b.id);
        }
        return desc ? -c : c;
    }

    public static String sortLabel(String sort, boolean desc) {
        if (SORT_KM.equals(sort)) {
            return desc ? "公里从多到少" : "公里从少到多";
        }
        if (SORT_STEPS.equals(sort)) {
            return desc ? "步数从多到少" : "步数从少到多";
        }
        if (SORT_SPEED.equals(sort)) {
            return desc ? "速度从快到慢" : "速度从慢到快";
        }
        return desc ? "时间从新到旧" : "时间从旧到新";
    }

    public static String normalizeSort(String sort) {
        if (SORT_KM.equals(sort) || SORT_STEPS.equals(sort) || SORT_SPEED.equals(sort)) {
            return sort;
        }
        return SORT_TIME;
    }

    static String blob(TrackSession s) {
        StringBuilder b = new StringBuilder(384);
        add(b, s.title, s.fromPlace, s.toPlace, s.startAddr, s.endAddr);
        add(b, Formats.modeLabel(s.mode), Formats.headline(s), Formats.routeLine(s));
        String origin = Formats.originTag(s);
        if (!origin.isEmpty()) {
            add(b, origin, "[" + origin + "]");
        }
        if (TrackEngine.isAuto(s.mode)) {
            add(b, "自动", "车程", "真正走路", Formats.distance(s.walkDistanceM),
                    Formats.distance(s.vehicleDistanceM));
        }
        add(b, Formats.when(s.startMs));
        if (s.endMs > 0) {
            add(b, Formats.when(s.endMs));
        }
        if (s.startMs > 0) {
            add(b, new SimpleDateFormat("yyyy-MM-dd yyyy年M月d日", Locale.CHINA)
                    .format(new Date(s.startMs)));
        }
        add(b, Formats.distance(s.distanceM), Formats.duration(s.movingMs));
        add(b, Formats.steps(s.steps), String.valueOf(s.steps));
        add(b, String.valueOf((int) Math.round(s.distanceM)), "米");
        double km = s.distanceM / 1000.0;
        add(b, String.format(Locale.CHINA, "%.2f公里", km));
        add(b, String.format(Locale.CHINA, "%.1f公里", km));
        if (km >= 1) {
            add(b, ((int) km) + "公里");
        }
        add(b, Formats.pace(s.distanceM, s.movingMs));
        add(b, Formats.speedKmh(s.avgSpeedMps()), Formats.speedKmh(s.maxSpeedMps));
        add(b, Formats.kcal(s.calories));
        String stride = Formats.strideCm(s.distanceM, s.steps);
        if (!"--".equals(stride)) {
            add(b, stride);
        }
        String batt = BatterySnap.savedShort(s);
        if (batt != null && !batt.isEmpty()) {
            add(b, batt);
        }
        if (!s.marks.isEmpty()) {
            add(b, Formats.marksCountLine(s.marks), "打点");
            for (Checkpoint m : s.marks) {
                if (m == null) {
                    continue;
                }
                add(b, Formats.markTitle(m), m.addr);
            }
        }
        return b.toString();
    }

    private static void add(StringBuilder b, String... bits) {
        for (String bit : bits) {
            if (bit == null) {
                continue;
            }
            String t = bit.trim();
            if (t.isEmpty() || "--".equals(t)) {
                continue;
            }
            b.append(t).append(' ');
        }
    }
}
