package com.example.mapdistance;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 两个打点或轨迹点之间的距离、用时、均速、步数、步幅。 */
final class SpanStats {
    String fromLabel = "";
    String toLabel = "";
    String mode = "";
    double trackM;
    double straightM;
    long clockMs;
    long movingMs;
    int steps = -1;
    boolean stepsEstimated;
    JSONArray line = new JSONArray();

    private SpanStats() {}

    String text(Context c) {
        fillIfMissing(c);
        StringBuilder b = new StringBuilder();
        b.append("从 ").append(fromLabel).append('\n');
        b.append("到 ").append(toLabel).append('\n');
        b.append("轨迹 ").append(Formats.distance(trackM)).append('\n');
        long useMs = movingMs > 0 ? movingMs : clockMs;
        b.append("用时 ").append(Formats.duration(useMs));
        if (clockMs > 0 && movingMs > 0 && Math.abs(clockMs - movingMs) > 5000) {
            b.append("（钟表 ").append(Formats.duration(clockMs)).append("）");
        }
        b.append('\n');
        if (useMs >= 1000 && trackM >= 1) {
            double mps = trackM / (useMs / 1000.0);
            b.append("均速 ").append(Formats.speed(c, mps)).append('\n');
            String pace = Formats.pace(trackM, useMs);
            if (!"--".equals(pace)) {
                b.append("配速 ").append(pace).append('\n');
            }
        }
        b.append("步数 ");
        if (steps < 0) {
            b.append("--");
        } else {
            b.append(Formats.steps(steps));
            if (stepsEstimated && steps > 0) {
                b.append("（按这段距离估）");
            }
        }
        b.append('\n');
        b.append(strideLine()).append('\n');
        if (straightM >= 1) {
            b.append("直线 ").append(Formats.distance(straightM));
        }
        return b.toString().trim();
    }

    private void fillIfMissing(Context c) {
        if (steps >= 0) {
            return;
        }
        if (TrackEngine.isRide(mode)) {
            steps = 0;
            stepsEstimated = false;
            return;
        }
        if (trackM < 1 || c == null) {
            return;
        }
        float strideM = Prefs.lastStrideM(c);
        if (strideM < 0.2f || strideM > 2.5f) {
            return;
        }
        steps = Math.max(0, (int) Math.round(trackM / strideM));
        stepsEstimated = true;
    }

    private String strideLine() {
        if (TrackEngine.isRide(mode)) {
            return "步幅 --（骑车不计）";
        }
        if (steps <= 0 || trackM < 1) {
            return "步幅 --";
        }
        double cm = trackM / steps * 100.0;
        if (cm < 8 || cm > 250) {
            return "步幅 --";
        }
        return String.format(Locale.CHINA, "步幅 %.0f 厘米", cm);
    }

    static List<TrackPoint> visible(TrackSession s) {
        List<TrackPoint> out = new ArrayList<>();
        if (s == null || s.points == null) {
            return out;
        }
        for (TrackPoint p : s.points) {
            if (p != null && !p.hidden) {
                out.add(p);
            }
        }
        return out;
    }

    static int nearest(List<TrackPoint> pts, double latGcj, double lngGcj, double maxM) {
        if (pts == null || pts.isEmpty()) {
            return -1;
        }
        int best = -1;
        double bestD = maxM;
        for (int i = 0; i < pts.size(); i++) {
            TrackPoint p = pts.get(i);
            double d = CoordTransform.haversineM(p.latGcj, p.lngGcj, latGcj, lngGcj);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    static int nearestMark(List<Checkpoint> marks, double latGcj, double lngGcj, double maxM) {
        if (marks == null || marks.isEmpty()) {
            return -1;
        }
        int best = -1;
        double bestD = maxM;
        for (int i = 0; i < marks.size(); i++) {
            Checkpoint m = marks.get(i);
            double d = CoordTransform.haversineM(m.latGcj, m.lngGcj, latGcj, lngGcj);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    static SpanStats betweenMarks(TrackSession s, int ia, int ib) {
        if (s == null || s.marks == null || ia < 0 || ib < 0
                || ia >= s.marks.size() || ib >= s.marks.size() || ia == ib) {
            return null;
        }
        Checkpoint a = s.marks.get(ia);
        Checkpoint b = s.marks.get(ib);
        if (a.movingMs > b.movingMs || (a.movingMs == b.movingMs && a.t > b.t)) {
            Checkpoint tmp = a;
            a = b;
            b = tmp;
        }
        List<TrackPoint> pts = visible(s);
        int[] filled = fillSteps(pts, s);
        SpanStats r = new SpanStats();
        r.mode = s.mode;
        r.fromLabel = Formats.markTitle(a);
        r.toLabel = Formats.markTitle(b);
        r.trackM = Math.abs(b.distanceM - a.distanceM);
        r.straightM = CoordTransform.haversineM(a.latGcj, a.lngGcj, b.latGcj, b.lngGcj);
        r.movingMs = Math.abs(b.movingMs - a.movingMs);
        r.clockMs = Math.abs(b.t - a.t);
        int sa = stepsOfMark(s, a, pts, filled);
        int sb = stepsOfMark(s, b, pts, filled);
        if (sa >= 0 && sb >= 0) {
            r.steps = Math.abs(sb - sa);
            r.stepsEstimated = a.stepsAt < 0 || b.stepsAt < 0;
        }
        r.line = lineBetween(pts, a.latGcj, a.lngGcj, b.latGcj, b.lngGcj);
        return r;
    }

    static SpanStats betweenPoints(TrackSession s, List<TrackPoint> pts, int ia, int ib) {
        if (pts == null || ia < 0 || ib < 0 || ia >= pts.size() || ib >= pts.size() || ia == ib) {
            return null;
        }
        if (ia > ib) {
            int t = ia;
            ia = ib;
            ib = t;
        }
        TrackPoint a = pts.get(ia);
        TrackPoint b = pts.get(ib);
        int[] filled = fillSteps(pts, s);
        SpanStats r = new SpanStats();
        r.mode = s == null ? "" : s.mode;
        r.fromLabel = "轨迹点 " + Formats.clock(a.t);
        r.toLabel = "轨迹点 " + Formats.clock(b.t);
        double track = 0;
        JSONArray line = new JSONArray();
        putLl(line, a);
        for (int i = ia + 1; i <= ib; i++) {
            TrackPoint prev = pts.get(i - 1);
            TrackPoint cur = pts.get(i);
            track += hop(prev, cur);
            putLl(line, cur);
        }
        r.trackM = track;
        r.straightM = hop(a, b);
        r.clockMs = Math.max(0, b.t - a.t);
        r.movingMs = r.clockMs;
        int sa = filled.length > ia ? filled[ia] : a.stepsAt;
        int sb = filled.length > ib ? filled[ib] : b.stepsAt;
        if (sa >= 0 && sb >= 0) {
            r.steps = Math.abs(sb - sa);
            r.stepsEstimated = a.stepsAt < 0 || b.stepsAt < 0;
        }
        r.line = line;
        return r;
    }

    JSONObject mapJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("line", line);
            if (line.length() > 0) {
                o.put("a", line.getJSONObject(0));
                o.put("b", line.getJSONObject(line.length() - 1));
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    /**
     * 旧历史点没有 st。有锚点就按累计距离插；一个都没有但整段有步数，就按走路距离从 0 插到终点。
     * 只改返回数组，不回写库。
     */
    static int[] fillSteps(List<TrackPoint> pts, TrackSession s) {
        int n = pts == null ? 0 : pts.size();
        int[] st = new int[n];
        if (n == 0) {
            return st;
        }
        Arrays.fill(st, -1);
        boolean any = false;
        for (int i = 0; i < n; i++) {
            if (pts.get(i).stepsAt >= 0) {
                st[i] = pts.get(i).stepsAt;
                any = true;
            }
        }
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) {
            cum[i] = cum[i - 1] + hop(pts.get(i - 1), pts.get(i));
        }
        int sessSteps = s == null ? 0 : Math.max(0, s.steps);
        if (!any && sessSteps > 0) {
            seedFromSession(pts, st, sessSteps);
            any = st[0] >= 0 || st[n - 1] >= 0;
        }
        if (!any) {
            return st;
        }
        int prev = -1;
        for (int i = 0; i < n; i++) {
            if (st[i] < 0) {
                continue;
            }
            if (prev >= 0 && i > prev + 1) {
                double span = cum[i] - cum[prev];
                int ds = st[i] - st[prev];
                for (int j = prev + 1; j < i; j++) {
                    if (span < 0.5) {
                        st[j] = st[prev];
                    } else {
                        st[j] = st[prev] + (int) Math.round(ds * (cum[j] - cum[prev]) / span);
                    }
                }
            }
            prev = i;
        }
        int first = -1;
        for (int i = 0; i < n; i++) {
            if (st[i] >= 0) {
                first = i;
                break;
            }
        }
        if (first > 0) {
            for (int i = 0; i < first; i++) {
                st[i] = st[first];
            }
        }
        int last = -1;
        for (int i = n - 1; i >= 0; i--) {
            if (st[i] >= 0) {
                last = i;
                break;
            }
        }
        if (last >= 0) {
            for (int i = last + 1; i < n; i++) {
                st[i] = st[last];
            }
        }
        return st;
    }

    private static void seedFromSession(List<TrackPoint> pts, int[] st, int sessSteps) {
        int n = pts.size();
        st[0] = 0;
        double walk = 0;
        double[] walkCum = new double[n];
        for (int i = 1; i < n; i++) {
            boolean veh = pts.get(i).kind == TrackPoint.KIND_VEHICLE
                    || pts.get(i - 1).kind == TrackPoint.KIND_VEHICLE;
            if (!veh) {
                walk += hop(pts.get(i - 1), pts.get(i));
            }
            walkCum[i] = walk;
        }
        if (walk < 1) {
            st[n - 1] = sessSteps;
            return;
        }
        for (int i = 1; i < n; i++) {
            st[i] = (int) Math.round(sessSteps * walkCum[i] / walk);
        }
    }

    private static int stepsOfMark(TrackSession s, Checkpoint m, List<TrackPoint> pts, int[] filled) {
        if (m.stepsAt >= 0) {
            return m.stepsAt;
        }
        int i = nearest(pts, m.latGcj, m.lngGcj, 80);
        if (i >= 0 && filled[i] >= 0) {
            return filled[i];
        }
        if (s != null && s.steps > 0 && s.distanceM > 1 && m.distanceM >= 0) {
            return (int) Math.round(s.steps * Math.min(1.0, m.distanceM / s.distanceM));
        }
        return i >= 0 ? filled[i] : -1;
    }

    private static JSONArray lineBetween(List<TrackPoint> pts, double aLat, double aLng,
                                         double bLat, double bLng) {
        int ia = nearest(pts, aLat, aLng, 10_000);
        int ib = nearest(pts, bLat, bLng, 10_000);
        JSONArray line = new JSONArray();
        if (ia < 0 || ib < 0) {
            putLl(line, aLat, aLng);
            putLl(line, bLat, bLng);
            return line;
        }
        if (ia > ib) {
            int t = ia;
            ia = ib;
            ib = t;
        }
        for (int i = ia; i <= ib; i++) {
            putLl(line, pts.get(i));
        }
        return line;
    }

    private static double hop(TrackPoint a, TrackPoint b) {
        if (a.latWgs != 0 || a.lngWgs != 0) {
            return CoordTransform.haversineM(a.latWgs, a.lngWgs, b.latWgs, b.lngWgs);
        }
        return CoordTransform.haversineM(a.latGcj, a.lngGcj, b.latGcj, b.lngGcj);
    }

    private static void putLl(JSONArray line, TrackPoint p) {
        putLl(line, p.latGcj, p.lngGcj);
    }

    private static void putLl(JSONArray line, double lat, double lng) {
        try {
            JSONObject o = new JSONObject();
            o.put("a", lat);
            o.put("n", lng);
            line.put(o);
        } catch (Exception ignored) {
        }
    }
}
