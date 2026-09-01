package com.example.mapdistance;

import org.json.JSONException;
import org.json.JSONObject;

/** 测量中途打的点：记下当时已走距离和已用时间，不影响继续测。 */
public final class Checkpoint {
    public int n;
    public long t;
    public double distanceM;
    public long movingMs;
    public double sinceLastM;
    public long sinceLastMs;
    public double latGcj;
    public double lngGcj;
    public String addr = "";
    /** true = 按间隔自动打的；false / 旧记录 = 手指点的。 */
    public boolean auto;
    /** 打点时这次已走步数；旧记录为 -1。 */
    public int stepsAt = -1;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("n", n);
        o.put("t", t);
        o.put("d", distanceM);
        o.put("m", movingMs);
        o.put("sd", sinceLastM);
        o.put("sm", sinceLastMs);
        o.put("a", latGcj);
        o.put("g", lngGcj);
        o.put("addr", addr == null ? "" : addr);
        o.put("auto", auto);
        if (stepsAt >= 0) {
            o.put("st", stepsAt);
        }
        return o;
    }

    public static Checkpoint fromJson(JSONObject o) {
        Checkpoint c = new Checkpoint();
        c.n = o.optInt("n");
        c.t = o.optLong("t");
        c.distanceM = o.optDouble("d");
        c.movingMs = o.optLong("m");
        c.sinceLastM = o.optDouble("sd");
        c.sinceLastMs = o.optLong("sm");
        c.latGcj = o.optDouble("a");
        c.lngGcj = o.optDouble("g");
        c.addr = o.optString("addr", "");
        c.auto = o.optBoolean("auto", false);
        c.stepsAt = o.optInt("st", -1);
        return c;
    }
}
