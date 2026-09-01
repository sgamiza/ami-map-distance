package com.example.mapdistance;

import org.json.JSONException;
import org.json.JSONObject;

public final class TrackPoint {
    public static final int KIND_NONE = 0;
    public static final int KIND_WALK = 1;
    public static final int KIND_VEHICLE = 2;

    public final long t;
    public final double latGcj;
    public final double lngGcj;
    public final double latWgs;
    public final double lngWgs;
    public final float accuracy;
    public final float speedMps;
    /** 手工去掉的点；还在库里，不算进距离/用时/轨迹。 */
    public boolean hidden;
    /** 自动模式：1 走路，2 车程；其它模式为 0。 */
    public int kind;
    /** 落到这点时芯片累计步数；-1 表示没记。 */
    public int stepsAt = -1;

    public TrackPoint(long t, double latGcj, double lngGcj, double latWgs, double lngWgs,
                      float accuracy, float speedMps) {
        this(t, latGcj, lngGcj, latWgs, lngWgs, accuracy, speedMps, false, 0, -1);
    }

    public TrackPoint(long t, double latGcj, double lngGcj, double latWgs, double lngWgs,
                      float accuracy, float speedMps, boolean hidden) {
        this(t, latGcj, lngGcj, latWgs, lngWgs, accuracy, speedMps, hidden, 0, -1);
    }

    public TrackPoint(long t, double latGcj, double lngGcj, double latWgs, double lngWgs,
                      float accuracy, float speedMps, boolean hidden, int kind, int stepsAt) {
        this.t = t;
        this.latGcj = latGcj;
        this.lngGcj = lngGcj;
        this.latWgs = latWgs;
        this.lngWgs = lngWgs;
        this.accuracy = accuracy;
        this.speedMps = speedMps;
        this.hidden = hidden;
        this.kind = kind;
        this.stepsAt = stepsAt;
    }

    public TrackPoint copy() {
        return new TrackPoint(t, latGcj, lngGcj, latWgs, lngWgs, accuracy, speedMps,
                hidden, kind, stepsAt);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("t", t);
        o.put("a", latGcj);
        o.put("n", lngGcj);
        o.put("wa", latWgs);
        o.put("wn", lngWgs);
        o.put("c", accuracy);
        o.put("s", speedMps);
        if (hidden) {
            o.put("x", 1);
        }
        if (kind != KIND_NONE) {
            o.put("k", kind);
        }
        if (stepsAt >= 0) {
            o.put("st", stepsAt);
        }
        return o;
    }

    public static TrackPoint fromJson(JSONObject o) {
        return new TrackPoint(
                o.optLong("t"),
                o.optDouble("a"),
                o.optDouble("n"),
                o.optDouble("wa"),
                o.optDouble("wn"),
                (float) o.optDouble("c"),
                (float) o.optDouble("s"),
                o.optInt("x", 0) != 0,
                o.optInt("k", KIND_NONE),
                o.optInt("st", -1));
    }
}
