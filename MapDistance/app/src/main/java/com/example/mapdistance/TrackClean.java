package com.example.mapdistance;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 飞点和停下：测量中、历史、统计共用。
 * 历史可按本条记录的速度阈值、手工去掉的点重算；不改没去掉的原始点。
 */
final class TrackClean {
    static final long STILL_MS = 12_000L;
    static final double STILL_M = 8.0;
    private static final double LEAVE_STILL_M = 12.0;
    /** 去掉飞点或手工去点之后，若下一点仍离得很远，当成 GPS 挪了位置：断开轨迹，不加这段距离。 */
    private static final double GAP_M = 22.0;

    static final class Hop {
        final int index;
        final double meters;
        final double mps;
        final long t;
        final boolean hidden;

        Hop(int index, double meters, double mps, long t, boolean hidden) {
            this.index = index;
            this.meters = meters;
            this.mps = mps;
            this.t = t;
            this.hidden = hidden;
        }
    }

    private TrackClean() {}

    static double maxImpliedMps(String mode) {
        if (TrackEngine.MODE_AUTO.equals(mode)) {
            return 40.0;
        }
        if (TrackEngine.MODE_RIDE.equals(mode)) {
            return 22.0;
        }
        if (TrackEngine.MODE_RUN.equals(mode)) {
            return 9.5;
        }
        return 7.5;
    }

    /** 本条实际用来判飞点的上限。自定义优先，否则走路/跑步/骑车默认。 */
    static double hopLimitMps(TrackSession s) {
        if (s != null && s.hopMaxMps >= 0.3) {
            return s.hopMaxMps;
        }
        return maxImpliedMps(s == null ? TrackEngine.MODE_WALK : s.mode);
    }

    static boolean rejectHop(String mode, double d, double dtSec) {
        TrackSession tmp = new TrackSession();
        tmp.mode = mode == null ? TrackEngine.MODE_WALK : mode;
        return rejectHop(tmp, d, dtSec);
    }

    static boolean rejectHop(TrackSession s, double d, double dtSec) {
        if (d < 0) {
            return true;
        }
        if (dtSec <= 0) {
            dtSec = 0.001;
        }
        double v = d / dtSec;
        double max = hopLimitMps(s);
        if (v > max) {
            return true;
        }
        if (s != null && s.hopMaxMps >= 0.3) {
            return false;
        }
        String mode = s == null ? TrackEngine.MODE_WALK : s.mode;
        if (TrackEngine.MODE_AUTO.equals(mode) || TrackEngine.MODE_RIDE.equals(mode)) {
            return d > 180 && v > 8.0;
        }
        if (TrackEngine.MODE_RUN.equals(mode)) {
            return d > 50 && v > 4.5;
        }
        return d > 30 && v > 2.8;
    }

    static double hopM(TrackPoint a, TrackPoint b) {
        if (a == null || b == null) {
            return 0;
        }
        return CoordTransform.haversineM(a.latWgs, a.lngWgs, b.latWgs, b.lngWgs);
    }

    static boolean leaveStill(String mode, double d, double dtSec) {
        if (d < LEAVE_STILL_M) {
            return false;
        }
        return !rejectHop(mode, d, dtSec);
    }

    static int hiddenCount(TrackSession s) {
        if (s == null) {
            return 0;
        }
        int n = 0;
        for (TrackPoint p : s.points) {
            if (p.hidden) {
                n++;
            }
        }
        return n;
    }

    static boolean hasHidden(TrackSession s) {
        return hiddenCount(s) > 0;
    }

    static TrackSession view(Context c, TrackSession raw) {
        if (raw == null) {
            return new TrackSession();
        }
        TrackSession s = copyOf(raw);
        s.rawDistanceM = raw.distanceM;
        s.rawMovingMs = raw.movingMs;
        s.histViewed = false;
        boolean need = (c != null && Prefs.histClean(c))
                || s.hopMaxMps >= 0.3
                || hasHidden(s);
        if (!need || s.points.size() < 2) {
            return s;
        }
        boolean stripStill = c != null && Prefs.histClean(c);
        applyStats(c, s, stripStill);
        s.histViewed = true;
        return s;
    }

    static List<TrackSession> views(Context c, List<TrackSession> all) {
        List<TrackSession> out = new ArrayList<>();
        if (all == null) {
            return out;
        }
        for (TrackSession s : all) {
            out.add(view(c, s));
        }
        return out;
    }

    /** 把距离、用时、最高时速、热量写回 session；轨迹点（含隐藏）原样保留。 */
    static void applyStats(Context c, TrackSession s, boolean stripStill) {
        if (s == null) {
            return;
        }
        Rebuild r = rebuild(s, stripStill);
        s.distanceM = r.dist;
        s.movingMs = r.move;
        s.maxSpeedMps = r.maxSpd;
        if (TrackEngine.isAuto(s.mode) && (r.walkDist > 0.5 || r.vehDist > 0.5)) {
            s.walkDistanceM = r.walkDist;
            s.walkMovingMs = r.walkMove;
            s.vehicleDistanceM = r.vehDist;
            s.vehicleMovingMs = r.vehMove;
        }
        if (c != null) {
            double km = (TrackEngine.isAuto(s.mode) ? s.walkDistanceM : r.dist) / 1000.0;
            double kg = Prefs.weightJin(c) / 2.0;
            s.calories = kg * km * TrackEngine.calorieFactor(s.mode);
        }
        long wall = Math.max(0, s.endMs - s.startMs);
        if (wall > s.movingMs) {
            s.pausedMs = wall - s.movingMs;
        }
    }

    /** 地图上画的点：未隐藏，且不是对上一接受点的飞点。飞点处断开，不连成一条线。 */
    static List<TrackPoint> pathPoints(TrackSession s) {
        List<TrackPoint> out = new ArrayList<>();
        for (List<TrackPoint> seg : pathSegments(s)) {
            out.addAll(seg);
        }
        return out;
    }

    static List<List<TrackPoint>> pathSegments(TrackSession s) {
        return rebuild(s, false).segs;
    }

    static List<Hop> suspectHops(TrackSession s) {
        List<Hop> out = new ArrayList<>();
        if (s == null || s.points.size() < 2) {
            return out;
        }
        double cap = hopLimitMps(s);
        TrackPoint last = null;
        for (int i = 0; i < s.points.size(); i++) {
            TrackPoint p = s.points.get(i);
            if (p.hidden) {
                if (last != null) {
                    double d = hopM(last, p);
                    double dt = Math.max(0.001, (p.t - last.t) / 1000.0);
                    out.add(new Hop(i, d, d / dt, p.t, true));
                } else {
                    out.add(new Hop(i, 0, 0, p.t, true));
                }
                continue;
            }
            if (last == null) {
                last = p;
                continue;
            }
            double d = hopM(last, p);
            double dt = Math.max(0.001, (p.t - last.t) / 1000.0);
            double v = d / dt;
            boolean fly = rejectHop(s, d, dt) || v > cap * 0.65 || d > 25;
            if (fly) {
                out.add(new Hop(i, d, v, p.t, false));
            }
            if (!rejectHop(s, d, dt)) {
                last = p;
            }
        }
        return out;
    }

    static int nearestIndex(List<TrackPoint> pts, double latGcj, double lngGcj, double maxM) {
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

    static String hopLimitLabel(TrackSession s) {
        return hopLimitLabel(null, s);
    }

    static String hopLimitLabel(Context c, TrackSession s) {
        double mps = hopLimitMps(s);
        boolean custom = s != null && s.hopMaxMps >= 0.3;
        String unit = Formats.UNIT_KMH;
        if (c != null) {
            unit = Prefs.speedUnit(c);
            if (Formats.UNIT_MPH.equals(unit)) {
                unit = Formats.UNIT_KMH;
            }
        }
        return (custom ? "" : "默认 ") + Formats.speed(mps, unit)
                + (Formats.UNIT_MS.equals(unit)
                ? String.format(Locale.CHINA, "（%.0f km/h）", mps * 3.6)
                : String.format(Locale.CHINA, "（%.1f m/s）", mps));
    }

    /** 把界面上的数字按单位换成米/秒。空=0；非法=-1。 */
    static double parseHopNumber(String raw, boolean asMs) {
        if (raw == null) {
            return 0;
        }
        String t = raw.trim().replace(" ", "");
        if (t.isEmpty()) {
            return 0;
        }
        try {
            double v = Double.parseDouble(t);
            if (v <= 0) {
                return 0;
            }
            double mps = asMs ? v : v / 3.6;
            if (mps < 0.3) {
                return 0;
            }
            if (mps > 40) {
                return 40;
            }
            return mps;
        } catch (Exception e) {
            return -1;
        }
    }

    static boolean changed(TrackSession s) {
        if (s == null || !s.histViewed) {
            return false;
        }
        return Math.abs(s.rawDistanceM - s.distanceM) >= 15
                || Math.abs(s.rawMovingMs - s.movingMs) >= 5000
                || hasHidden(s)
                || (s.hopMaxMps >= 0.3);
    }

    static String hint(TrackSession s) {
        if (s == null) {
            return "";
        }
        int hid = hiddenCount(s);
        if (hid > 0) {
            return "已去掉 " + hid + " 个点（可恢复）";
        }
        if (s.hopMaxMps >= 0.3) {
            return "阈值 " + Formats.speedKmh(s.hopMaxMps);
        }
        if (!changed(s)) {
            return "";
        }
        return "已去飞点/停留";
    }

    /**
     * 解析用户输入：空=默认；可写 54、54km/h、10m/s。
     * 没写单位时按 km/h。
     */
    static double parseHopMps(String raw) {
        if (raw == null) {
            return 0;
        }
        String t = raw.trim().toLowerCase(Locale.US)
                .replace(" ", "")
                .replace("公里/时", "kmh")
                .replace("公里每小时", "kmh")
                .replace("千米/时", "kmh")
                .replace("米/秒", "mps")
                .replace("米每秒", "mps");
        if (t.isEmpty()) {
            return 0;
        }
        boolean asMs = t.contains("m/s") || t.contains("mps") || t.endsWith("ms");
        boolean asKmh = t.contains("km") || t.contains("公里");
        t = t.replace("km/h", "").replace("kmh", "").replace("km", "")
                .replace("m/s", "").replace("mps", "");
        if (t.endsWith("ms") && !asKmh) {
            t = t.substring(0, t.length() - 2);
            asMs = true;
        }
        try {
            double v = Double.parseDouble(t);
            if (v <= 0) {
                return 0;
            }
            double mps = (asMs && !asKmh) ? v : v / 3.6;
            if (mps < 0.3) {
                return 0;
            }
            if (mps > 40) {
                return 40;
            }
            return mps;
        } catch (Exception e) {
            return -1;
        }
    }

    private static final class Rebuild {
        final List<List<TrackPoint>> segs = new ArrayList<>();
        double dist;
        long move;
        double maxSpd;
        double walkDist;
        long walkMove;
        double vehDist;
        long vehMove;
    }

    private static Rebuild rebuild(TrackSession s, boolean stripStill) {
        Rebuild r = new Rebuild();
        if (s == null || s.points.isEmpty()) {
            return r;
        }
        List<TrackPoint> seg = new ArrayList<>();
        TrackPoint last = null;
        int rejects = 0;
        int hiddenSince = 0;
        for (TrackPoint p : s.points) {
            if (p == null) {
                continue;
            }
            if (p.hidden) {
                hiddenSince++;
                continue;
            }
            if (last == null) {
                last = p;
                seg.add(p);
                hiddenSince = 0;
                rejects = 0;
                continue;
            }
            double d = hopM(last, p);
            double dt = Math.max(0.001, (p.t - last.t) / 1000.0);
            if (rejectHop(s, d, dt)) {
                rejects++;
                continue;
            }
            boolean gap = (rejects > 0 || hiddenSince > 0) && d > GAP_M;
            if (gap) {
                if (!seg.isEmpty()) {
                    r.segs.add(seg);
                }
                seg = new ArrayList<>();
                seg.add(p);
            } else {
                seg.add(p);
            }
            last = p;
            rejects = 0;
            hiddenSince = 0;
        }
        if (!seg.isEmpty()) {
            r.segs.add(seg);
        }
        for (List<TrackPoint> one : r.segs) {
            accSegment(one, stripStill, r);
        }
        return r;
    }

    private static void accSegment(List<TrackPoint> kept, boolean stripStill, Rebuild r) {
        if (kept == null || kept.size() < 2) {
            return;
        }
        TrackPoint last = kept.get(0);
        for (int i = 1; i < kept.size(); i++) {
            TrackPoint p = kept.get(i);
            double d = hopM(last, p);
            long dtMs = p.t - last.t;
            if (dtMs < 0) {
                continue;
            }
            if (stripStill) {
                int w = i;
                while (w > 0 && p.t - kept.get(w - 1).t <= STILL_MS) {
                    w--;
                }
                TrackPoint winStart = kept.get(w);
                double winD = hopM(winStart, p);
                long winMs = p.t - winStart.t;
                boolean stillNow = winMs >= 8_000L && winD < STILL_M;
                if (stillNow) {
                    continue;
                }
                if (d < 1.2 && dtMs < 2500) {
                    continue;
                }
                r.dist += d;
                long add = dtMs;
                if (add > STILL_MS && d < 60) {
                    add = Math.max(1000L, winMs);
                }
                r.move += add;
                accKind(p, d, add, r);
                if (add == dtMs && d > 3) {
                    float implied = (float) (d / Math.max(0.001, add / 1000.0));
                    if (implied > r.maxSpd) {
                        r.maxSpd = implied;
                    }
                }
            } else {
                if (d < 1.2 && dtMs < 2500) {
                    continue;
                }
                r.dist += d;
                r.move += dtMs;
                accKind(p, d, dtMs, r);
                if (d > 3) {
                    float implied = (float) (d / Math.max(0.001, dtMs / 1000.0));
                    if (implied > r.maxSpd) {
                        r.maxSpd = implied;
                    }
                }
            }
            last = p;
        }
    }

    private static void accKind(TrackPoint p, double d, long add, Rebuild r) {
        if (p == null) {
            return;
        }
        if (p.kind == TrackPoint.KIND_WALK) {
            r.walkDist += d;
            r.walkMove += add;
        } else if (p.kind == TrackPoint.KIND_VEHICLE) {
            r.vehDist += d;
            r.vehMove += add;
        }
    }

    static TrackSession copyOf(TrackSession src) {
        TrackSession s = new TrackSession();
        if (src == null) {
            return s;
        }
        s.id = src.id;
        s.mode = src.mode;
        s.state = src.state;
        s.startMs = src.startMs;
        s.endMs = src.endMs;
        s.movingMs = src.movingMs;
        s.pausedMs = src.pausedMs;
        s.distanceM = src.distanceM;
        s.maxSpeedMps = src.maxSpeedMps;
        s.calories = src.calories;
        s.startAddr = src.startAddr;
        s.endAddr = src.endAddr;
        s.title = src.title;
        s.fromPlace = src.fromPlace;
        s.toPlace = src.toPlace;
        s.uid = src.uid;
        s.steps = src.steps;
        s.stepAnchor = src.stepAnchor;
        s.battStartPct = src.battStartPct;
        s.battStartUah = src.battStartUah;
        s.battEndPct = src.battEndPct;
        s.battEndUah = src.battEndUah;
        s.battSawCharge = src.battSawCharge;
        s.autoMarkDueMs = src.autoMarkDueMs;
        s.goalDistFired = src.goalDistFired;
        s.goalStepsFired = src.goalStepsFired;
        s.everyDistN = src.everyDistN;
        s.everyStepsN = src.everyStepsN;
        s.hopMaxMps = src.hopMaxMps;
        s.walkDistanceM = src.walkDistanceM;
        s.walkMovingMs = src.walkMovingMs;
        s.walkSteps = src.walkSteps;
        s.vehicleDistanceM = src.vehicleDistanceM;
        s.vehicleMovingMs = src.vehicleMovingMs;
        s.autoKind = src.autoKind;
        s.cadenceSpm = src.cadenceSpm;
        s.origin = TrackSession.normalizeOrigin(src.origin);
        for (TrackPoint p : src.points) {
            s.points.add(p.copy());
        }
        s.marks.addAll(src.marks);
        return s;
    }
}
