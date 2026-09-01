package com.example.mapdistance;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class TrackSession {
    public long id;
    public String mode = TrackEngine.MODE_WALK;
    public String state = TrackEngine.IDLE;
    public long startMs;
    public long endMs;
    public long movingMs;
    public long pausedMs;
    public double distanceM;
    public double maxSpeedMps;
    public double calories;
    public String startAddr = "";
    public String endAddr = "";
    public String title = "";
    public String fromPlace = "";
    public String toPlace = "";
    public String uid = "";
    /** 这次测量的步数（点开始到结束；暂停期间冻住，和距离、用时一样）。 */
    public int steps;
    /** TYPE_STEP_COUNTER 锚点；草稿恢复用。不进历史列表。 */
    public long stepAnchor = -1;
    /** 点开始时的整机电量 0–100；-1 表示没有记。 */
    public int battStartPct = -1;
    public long battStartUah = -1;
    public int battEndPct = -1;
    public long battEndUah = -1;
    /** 本段里见过充电（插着线就不报掉电）。 */
    public boolean battSawCharge;
    /** 下一次自动打点对应的用时（暂停不加）。草稿用，不进历史库。 */
    public long autoMarkDueMs;
    public boolean goalDistFired;
    public boolean goalStepsFired;
    public int everyDistN;
    public int everyStepsN;
    public final List<TrackPoint> points = new ArrayList<>();
    public final List<Checkpoint> marks = new ArrayList<>();
    /**
     * 本条记录的飞点速度上限（米/秒）。≤0 表示用走路/跑步/骑车/自动默认。
     * 超过这个瞬时位移速度的点当 GPS 跳动，不算进距离和轨迹。
     */
    public double hopMaxMps;
    /** 自动模式：识别为走路的距离、用时、步数。 */
    public double walkDistanceM;
    public long walkMovingMs;
    public int walkSteps;
    /** 自动模式：没步数但在移动（开车/骑车等）。 */
    public double vehicleDistanceM;
    public long vehicleMovingMs;
    /** 自动模式当前段：1 走路 2 车程。草稿用。 */
    public int autoKind;
    /** 近几秒步频，不进历史库。 */
    public float cadenceSpm;
    /** 仅展示用，不进库。历史清洗后对比原始距离/用时。 */
    public boolean histViewed;
    public double rawDistanceM;
    public long rawMovingMs;
    /** 本机测的不标；附近/文件夹同步进来、或备份覆盖恢复进来会标。不跟 JSON 走，避免对方的本机变成这边的本机。 */
    public static final String ORIGIN_LOCAL = "local";
    public static final String ORIGIN_SYNC = "sync";
    public static final String ORIGIN_RESTORE = "restore";
    public String origin = ORIGIN_LOCAL;

    public static String normalizeOrigin(String o) {
        if (ORIGIN_SYNC.equals(o) || ORIGIN_RESTORE.equals(o)) {
            return o;
        }
        return ORIGIN_LOCAL;
    }

    public boolean fromElsewhere() {
        return ORIGIN_SYNC.equals(origin) || ORIGIN_RESTORE.equals(origin);
    }

    public double avgSpeedMps() {
        if (movingMs < 1000 || distanceM <= 0) {
            return 0;
        }
        return distanceM / (movingMs / 1000.0);
    }

    public double walkAvgMps() {
        if (walkMovingMs < 1000 || walkDistanceM <= 0) {
            return 0;
        }
        return walkDistanceM / (walkMovingMs / 1000.0);
    }

    public double vehicleAvgMps() {
        if (vehicleMovingMs < 1000 || vehicleDistanceM <= 0) {
            return 0;
        }
        return vehicleDistanceM / (vehicleMovingMs / 1000.0);
    }

    public double walkCadenceSpm() {
        if (walkSteps < 8 || walkMovingMs < 15_000L) {
            return 0;
        }
        return walkSteps / (walkMovingMs / 60000.0);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("mode", mode);
        o.put("state", state);
        o.put("startMs", startMs);
        o.put("endMs", endMs);
        o.put("movingMs", movingMs);
        o.put("pausedMs", pausedMs);
        o.put("distanceM", distanceM);
        o.put("maxSpeedMps", maxSpeedMps);
        o.put("calories", calories);
        o.put("startAddr", startAddr == null ? "" : startAddr);
        o.put("endAddr", endAddr == null ? "" : endAddr);
        o.put("title", title == null ? "" : title);
        o.put("fromPlace", fromPlace == null ? "" : fromPlace);
        o.put("toPlace", toPlace == null ? "" : toPlace);
        o.put("uid", uid == null ? "" : uid);
        o.put("steps", steps);
        o.put("stepAnchor", stepAnchor);
        o.put("battStartPct", battStartPct);
        o.put("battStartUah", battStartUah);
        o.put("battEndPct", battEndPct);
        o.put("battEndUah", battEndUah);
        o.put("battSawCharge", battSawCharge);
        o.put("autoMarkDueMs", autoMarkDueMs);
        o.put("goalDistFired", goalDistFired);
        o.put("goalStepsFired", goalStepsFired);
        o.put("everyDistN", everyDistN);
        o.put("everyStepsN", everyStepsN);
        o.put("hopMaxMps", hopMaxMps);
        o.put("walkDistanceM", walkDistanceM);
        o.put("walkMovingMs", walkMovingMs);
        o.put("walkSteps", walkSteps);
        o.put("vehicleDistanceM", vehicleDistanceM);
        o.put("vehicleMovingMs", vehicleMovingMs);
        o.put("autoKind", autoKind);
        JSONArray arr = new JSONArray();
        for (TrackPoint p : points) {
            arr.put(p.toJson());
        }
        o.put("points", arr);
        JSONArray mk = new JSONArray();
        for (Checkpoint c : marks) {
            mk.put(c.toJson());
        }
        o.put("marks", mk);
        return o;
    }

    public static TrackSession fromJson(JSONObject o) {
        TrackSession s = new TrackSession();
        s.id = o.optLong("id");
        s.mode = o.optString("mode", TrackEngine.MODE_WALK);
        s.state = o.optString("state", TrackEngine.IDLE);
        s.startMs = o.optLong("startMs");
        s.endMs = o.optLong("endMs");
        s.movingMs = o.optLong("movingMs");
        s.pausedMs = o.optLong("pausedMs");
        s.distanceM = o.optDouble("distanceM");
        s.maxSpeedMps = o.optDouble("maxSpeedMps");
        s.calories = o.optDouble("calories");
        s.startAddr = o.optString("startAddr", "");
        s.endAddr = o.optString("endAddr", "");
        s.title = o.optString("title", "");
        s.fromPlace = o.optString("fromPlace", "");
        s.toPlace = o.optString("toPlace", "");
        s.uid = o.optString("uid", "");
        s.steps = o.optInt("steps", 0);
        s.stepAnchor = o.optLong("stepAnchor", -1);
        s.battStartPct = o.optInt("battStartPct", -1);
        s.battStartUah = o.optLong("battStartUah", -1);
        s.battEndPct = o.optInt("battEndPct", -1);
        s.battEndUah = o.optLong("battEndUah", -1);
        s.battSawCharge = o.optBoolean("battSawCharge", false);
        s.autoMarkDueMs = o.optLong("autoMarkDueMs", 0);
        s.goalDistFired = o.optBoolean("goalDistFired", false);
        s.goalStepsFired = o.optBoolean("goalStepsFired", false);
        s.everyDistN = o.optInt("everyDistN", 0);
        s.everyStepsN = o.optInt("everyStepsN", 0);
        s.hopMaxMps = o.optDouble("hopMaxMps", 0);
        s.walkDistanceM = o.optDouble("walkDistanceM", 0);
        s.walkMovingMs = o.optLong("walkMovingMs", 0);
        s.walkSteps = o.optInt("walkSteps", 0);
        s.vehicleDistanceM = o.optDouble("vehicleDistanceM", 0);
        s.vehicleMovingMs = o.optLong("vehicleMovingMs", 0);
        s.autoKind = o.optInt("autoKind", 0);
        JSONArray arr = o.optJSONArray("points");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p != null) {
                    s.points.add(TrackPoint.fromJson(p));
                }
            }
        }
        JSONArray mk = o.optJSONArray("marks");
        if (mk != null) {
            for (int i = 0; i < mk.length(); i++) {
                JSONObject c = mk.optJSONObject(i);
                if (c != null) {
                    s.marks.add(Checkpoint.fromJson(c));
                }
            }
        }
        s.recomputeMarkSegments();
        return s;
    }

    public void recomputeMarkSegments() {
        Checkpoint prev = null;
        int i = 1;
        for (Checkpoint c : marks) {
            c.n = i++;
            c.sinceLastM = c.distanceM - (prev == null ? 0 : prev.distanceM);
            c.sinceLastMs = c.movingMs - (prev == null ? 0 : prev.movingMs);
            if (c.sinceLastM < 0) {
                c.sinceLastM = 0;
            }
            if (c.sinceLastMs < 0) {
                c.sinceLastMs = 0;
            }
            prev = c;
        }
    }

    public static TrackSession fromJson(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return fromJson(new JSONObject(raw));
        } catch (JSONException e) {
            return null;
        }
    }
}
