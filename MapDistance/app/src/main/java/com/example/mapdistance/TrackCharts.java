package com.example.mapdistance;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 统计图表用的 JSON：只带每次测量的摘要，不带轨迹点。 */
final class TrackCharts {
    private TrackCharts() {}

    static String payload(Context c, List<TrackSession> all) {
        String filter = Prefs.statsFilter(c);
        List<TrackSession> list = new ArrayList<>(TrackStats.filtered(TrackClean.views(c, all), filter));
        Collections.sort(list, new Comparator<TrackSession>() {
            @Override
            public int compare(TrackSession a, TrackSession b) {
                return Long.compare(a.startMs, b.startMs);
            }
        });
        try {
            JSONObject o = new JSONObject();
            o.put("version", BuildConfig.VERSION_NAME);
            o.put("filter", filter);
            o.put("filterLabel", TrackStats.label(filter));
            o.put("speedUnit", Prefs.speedUnit(c));
            o.put("total", all == null ? 0 : all.size());
            JSONArray arr = new JSONArray();
            for (TrackSession s : list) {
                JSONObject row = new JSONObject();
                row.put("t", s.startMs);
                row.put("end", s.endMs);
                row.put("mode", s.mode == null ? TrackEngine.MODE_WALK : s.mode);
                row.put("m", s.distanceM);
                row.put("walkM", s.walkDistanceM);
                row.put("vehM", s.vehicleDistanceM);
                row.put("walkMs", s.walkMovingMs);
                row.put("vehMs", s.vehicleMovingMs);
                row.put("walkSteps", s.walkSteps);
                row.put("moveMs", s.movingMs);
                row.put("pauseMs", s.pausedMs);
                row.put("steps", s.steps);
                row.put("kcal", s.calories);
                row.put("maxMps", s.maxSpeedMps);
                row.put("avgMps", s.avgSpeedMps());
                row.put("marks", s.marks.size());
                row.put("title", Formats.nz(s.title));
                String from = Formats.nz(s.fromPlace);
                if (from.isEmpty() && !Formats.needsGeocode(s.startAddr)) {
                    from = Formats.nz(s.startAddr);
                }
                String to = Formats.nz(s.toPlace);
                if (to.isEmpty() && !Formats.needsGeocode(s.endAddr)) {
                    to = Formats.nz(s.endAddr);
                }
                row.put("from", from);
                row.put("to", to);
                arr.put(row);
            }
            o.put("sessions", arr);
            return o.toString();
        } catch (Exception e) {
            return "{\"sessions\":[],\"filterLabel\":\"全部\",\"version\":\"\"}";
        }
    }
}
