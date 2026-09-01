package com.example.mapdistance;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class TrackStore {
    private final Context app;
    private final Helper helper;
    private boolean hushDirty;

    public TrackStore(Context context) {
        app = context.getApplicationContext();
        helper = new Helper(app);
    }

    private void dirty() {
        if (!hushDirty) {
            Backups.markDirty(app);
        }
    }

    public long insert(TrackSession s) {
        if (s.uid == null || s.uid.trim().isEmpty()) {
            s.uid = java.util.UUID.randomUUID().toString();
        }
        ContentValues v = toValues(s);
        SQLiteDatabase db = helper.getWritableDatabase();
        s.id = db.insert("sessions", null, v);
        dirty();
        return s.id;
    }

    public int count() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM sessions", null);
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        } finally {
            c.close();
        }
        return 0;
    }

    public boolean hasUid(String uid) {
        if (uid == null || uid.isEmpty()) {
            return false;
        }
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query("sessions", new String[]{"id"}, "uid=?", new String[]{uid},
                null, null, null, "1");
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public void ensureUids() {
        List<TrackSession> all = list();
        SQLiteDatabase db = helper.getWritableDatabase();
        for (TrackSession s : all) {
            if (s.uid != null && !s.uid.isEmpty()) {
                continue;
            }
            String uid = java.util.UUID.randomUUID().toString();
            ContentValues v = new ContentValues();
            v.put("uid", uid);
            db.update("sessions", v, "id=?", new String[]{String.valueOf(s.id)});
            s.uid = uid;
        }
    }

    public static final class Delta {
        public int added;
        public int skipped;
    }

    public Delta mergeIncoming(List<TrackSession> incoming) {
        ensureUids();
        Delta d = new Delta();
        if (incoming == null) {
            return d;
        }
        for (TrackSession s : incoming) {
            if (s == null) {
                continue;
            }
            if (s.uid == null || s.uid.trim().isEmpty()) {
                s.uid = java.util.UUID.randomUUID().toString();
            }
            if (hasUid(s.uid)) {
                d.skipped++;
                continue;
            }
            s.id = 0;
            s.state = TrackEngine.IDLE;
            s.origin = TrackSession.ORIGIN_SYNC;
            insert(s);
            d.added++;
        }
        return d;
    }

    /** 覆盖恢复用：先清空再整包写入，只标一次 dirty。 */
    public int replaceAll(List<TrackSession> incoming) {
        hushDirty = true;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("sessions", null, null);
            int n = 0;
            if (incoming != null) {
                for (TrackSession s : incoming) {
                    if (s == null) {
                        continue;
                    }
                    if (s.uid == null || s.uid.trim().isEmpty()) {
                        s.uid = java.util.UUID.randomUUID().toString();
                    }
                    s.id = 0;
                    s.state = TrackEngine.IDLE;
                    s.origin = TrackSession.ORIGIN_RESTORE;
                    s.id = db.insert("sessions", null, toValues(s));
                    n++;
                }
            }
            db.setTransactionSuccessful();
            hushDirty = false;
            Backups.markDirty(app);
            return n;
        } finally {
            hushDirty = false;
            db.endTransaction();
        }
    }

    public void delete(long id) {
        helper.getWritableDatabase().delete("sessions", "id=?", new String[]{String.valueOf(id)});
        dirty();
    }

    public void updateAddrs(long id, String start, String end) {
        ContentValues v = new ContentValues();
        v.put("start_addr", start == null ? "" : start);
        v.put("end_addr", end == null ? "" : end);
        helper.getWritableDatabase().update("sessions", v, "id=?",
                new String[]{String.valueOf(id)});
        dirty();
    }

    public void updateLabels(long id, String title, String fromPlace, String toPlace) {
        ContentValues v = new ContentValues();
        v.put("title", title == null ? "" : title.trim());
        v.put("from_place", fromPlace == null ? "" : fromPlace.trim());
        v.put("to_place", toPlace == null ? "" : toPlace.trim());
        helper.getWritableDatabase().update("sessions", v, "id=?",
                new String[]{String.valueOf(id)});
        dirty();
    }

    /** 写回轨迹点、速度阈值、距离用时等；id 不变。 */
    public void updateTrack(TrackSession s) {
        if (s == null || s.id <= 0) {
            return;
        }
        helper.getWritableDatabase().update("sessions", toValues(s), "id=?",
                new String[]{String.valueOf(s.id)});
        dirty();
    }

    public List<TrackSession> list() {
        List<TrackSession> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query("sessions", null, null, null, null, null, "start_ms DESC");
        try {
            while (c.moveToNext()) {
                out.add(fromCursor(c));
            }
        } finally {
            c.close();
        }
        return out;
    }

    public TrackSession get(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query("sessions", null, "id=?", new String[]{String.valueOf(id)},
                null, null, null);
        try {
            if (c.moveToFirst()) {
                return fromCursor(c);
            }
        } finally {
            c.close();
        }
        return null;
    }

    private static ContentValues toValues(TrackSession s) {
        ContentValues v = new ContentValues();
        v.put("mode", s.mode);
        v.put("start_ms", s.startMs);
        v.put("end_ms", s.endMs);
        v.put("moving_ms", s.movingMs);
        v.put("distance_m", s.distanceM);
        v.put("max_speed", s.maxSpeedMps);
        v.put("calories", s.calories);
        v.put("start_addr", s.startAddr);
        v.put("end_addr", s.endAddr);
        v.put("title", s.title == null ? "" : s.title);
        v.put("from_place", s.fromPlace == null ? "" : s.fromPlace);
        v.put("to_place", s.toPlace == null ? "" : s.toPlace);
        v.put("uid", s.uid == null ? "" : s.uid);
        v.put("steps", s.steps);
        v.put("batt_start_pct", s.battStartPct);
        v.put("batt_start_uah", s.battStartUah);
        v.put("batt_end_pct", s.battEndPct);
        v.put("batt_end_uah", s.battEndUah);
        v.put("batt_saw_charge", s.battSawCharge ? 1 : 0);
        v.put("hop_max_mps", s.hopMaxMps);
        v.put("walk_m", s.walkDistanceM);
        v.put("walk_ms", s.walkMovingMs);
        v.put("walk_steps", s.walkSteps);
        v.put("veh_m", s.vehicleDistanceM);
        v.put("veh_ms", s.vehicleMovingMs);
        v.put("origin", TrackSession.normalizeOrigin(s.origin));
        JSONArray arr = new JSONArray();
        try {
            for (TrackPoint p : s.points) {
                arr.put(p.toJson());
            }
        } catch (JSONException ignored) {
        }
        v.put("points", arr.toString());
        JSONArray mk = new JSONArray();
        try {
            for (Checkpoint c : s.marks) {
                mk.put(c.toJson());
            }
        } catch (JSONException ignored) {
        }
        v.put("marks", mk.toString());
        return v;
    }

    private static TrackSession fromCursor(Cursor c) {
        TrackSession s = new TrackSession();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.mode = c.getString(c.getColumnIndexOrThrow("mode"));
        s.startMs = c.getLong(c.getColumnIndexOrThrow("start_ms"));
        s.endMs = c.getLong(c.getColumnIndexOrThrow("end_ms"));
        s.movingMs = c.getLong(c.getColumnIndexOrThrow("moving_ms"));
        s.distanceM = c.getDouble(c.getColumnIndexOrThrow("distance_m"));
        s.maxSpeedMps = c.getDouble(c.getColumnIndexOrThrow("max_speed"));
        s.calories = c.getDouble(c.getColumnIndexOrThrow("calories"));
        s.startAddr = c.getString(c.getColumnIndexOrThrow("start_addr"));
        s.endAddr = c.getString(c.getColumnIndexOrThrow("end_addr"));
        s.title = col(c, "title");
        s.fromPlace = col(c, "from_place");
        s.toPlace = col(c, "to_place");
        s.uid = col(c, "uid");
        int stepsCol = c.getColumnIndex("steps");
        if (stepsCol >= 0) {
            s.steps = c.getInt(stepsCol);
        }
        s.battStartPct = colInt(c, "batt_start_pct", -1);
        s.battStartUah = colLong(c, "batt_start_uah", -1);
        s.battEndPct = colInt(c, "batt_end_pct", -1);
        s.battEndUah = colLong(c, "batt_end_uah", -1);
        s.battSawCharge = colInt(c, "batt_saw_charge", 0) != 0;
        s.hopMaxMps = colDouble(c, "hop_max_mps", 0);
        s.walkDistanceM = colDouble(c, "walk_m", 0);
        s.walkMovingMs = colLong(c, "walk_ms", 0);
        s.walkSteps = colInt(c, "walk_steps", 0);
        s.vehicleDistanceM = colDouble(c, "veh_m", 0);
        s.vehicleMovingMs = colLong(c, "veh_ms", 0);
        s.origin = TrackSession.normalizeOrigin(col(c, "origin"));
        s.state = TrackEngine.IDLE;
        String raw = c.getString(c.getColumnIndexOrThrow("points"));
        try {
            JSONArray arr = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    s.points.add(TrackPoint.fromJson(o));
                }
            }
        } catch (JSONException ignored) {
        }
        int marksCol = c.getColumnIndex("marks");
        if (marksCol >= 0) {
            String marksRaw = c.getString(marksCol);
            try {
                JSONArray mk = new JSONArray(marksRaw == null || marksRaw.isEmpty() ? "[]" : marksRaw);
                for (int i = 0; i < mk.length(); i++) {
                    JSONObject o = mk.optJSONObject(i);
                    if (o != null) {
                        s.marks.add(Checkpoint.fromJson(o));
                    }
                }
            } catch (JSONException ignored) {
            }
        }
        s.recomputeMarkSegments();
        return s;
    }

    private static String col(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        if (i < 0) {
            return "";
        }
        String v = c.getString(i);
        return v == null ? "" : v;
    }

    private static int colInt(Cursor c, String name, int fallback) {
        int i = c.getColumnIndex(name);
        if (i < 0 || c.isNull(i)) {
            return fallback;
        }
        return c.getInt(i);
    }

    private static long colLong(Cursor c, String name, long fallback) {
        int i = c.getColumnIndex(name);
        if (i < 0 || c.isNull(i)) {
            return fallback;
        }
        return c.getLong(i);
    }

    private static double colDouble(Cursor c, String name, double fallback) {
        int i = c.getColumnIndex(name);
        if (i < 0 || c.isNull(i)) {
            return fallback;
        }
        return c.getDouble(i);
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, "tracks.db", null, 9);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE sessions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "mode TEXT NOT NULL,"
                    + "start_ms INTEGER NOT NULL,"
                    + "end_ms INTEGER NOT NULL,"
                    + "moving_ms INTEGER NOT NULL,"
                    + "distance_m REAL NOT NULL,"
                    + "max_speed REAL NOT NULL,"
                    + "calories REAL NOT NULL,"
                    + "start_addr TEXT,"
                    + "end_addr TEXT,"
                    + "points TEXT NOT NULL,"
                    + "marks TEXT NOT NULL DEFAULT '[]',"
                    + "title TEXT NOT NULL DEFAULT '',"
                    + "from_place TEXT NOT NULL DEFAULT '',"
                    + "to_place TEXT NOT NULL DEFAULT '',"
                    + "uid TEXT NOT NULL DEFAULT '',"
                    + "steps INTEGER NOT NULL DEFAULT 0,"
                    + "batt_start_pct INTEGER NOT NULL DEFAULT -1,"
                    + "batt_start_uah INTEGER NOT NULL DEFAULT -1,"
                    + "batt_end_pct INTEGER NOT NULL DEFAULT -1,"
                    + "batt_end_uah INTEGER NOT NULL DEFAULT -1,"
                    + "batt_saw_charge INTEGER NOT NULL DEFAULT 0,"
                    + "hop_max_mps REAL NOT NULL DEFAULT 0,"
                    + "walk_m REAL NOT NULL DEFAULT 0,"
                    + "walk_ms INTEGER NOT NULL DEFAULT 0,"
                    + "walk_steps INTEGER NOT NULL DEFAULT 0,"
                    + "veh_m REAL NOT NULL DEFAULT 0,"
                    + "veh_ms INTEGER NOT NULL DEFAULT 0,"
                    + "origin TEXT NOT NULL DEFAULT 'local')");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN marks TEXT NOT NULL DEFAULT '[]'");
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN title TEXT NOT NULL DEFAULT ''");
                db.execSQL("ALTER TABLE sessions ADD COLUMN from_place TEXT NOT NULL DEFAULT ''");
                db.execSQL("ALTER TABLE sessions ADD COLUMN to_place TEXT NOT NULL DEFAULT ''");
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN uid TEXT NOT NULL DEFAULT ''");
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN steps INTEGER NOT NULL DEFAULT 0");
            }
            if (oldVersion < 6) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN batt_start_pct INTEGER NOT NULL DEFAULT -1");
                db.execSQL("ALTER TABLE sessions ADD COLUMN batt_start_uah INTEGER NOT NULL DEFAULT -1");
                db.execSQL("ALTER TABLE sessions ADD COLUMN batt_end_pct INTEGER NOT NULL DEFAULT -1");
                db.execSQL("ALTER TABLE sessions ADD COLUMN batt_end_uah INTEGER NOT NULL DEFAULT -1");
                db.execSQL("ALTER TABLE sessions ADD COLUMN batt_saw_charge INTEGER NOT NULL DEFAULT 0");
            }
            if (oldVersion < 7) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN hop_max_mps REAL NOT NULL DEFAULT 0");
            }
            if (oldVersion < 8) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN walk_m REAL NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE sessions ADD COLUMN walk_ms INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE sessions ADD COLUMN walk_steps INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE sessions ADD COLUMN veh_m REAL NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE sessions ADD COLUMN veh_ms INTEGER NOT NULL DEFAULT 0");
            }
            if (oldVersion < 9) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN origin TEXT NOT NULL DEFAULT 'local'");
            }
        }
    }
}
