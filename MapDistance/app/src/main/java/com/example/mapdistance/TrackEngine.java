package com.example.mapdistance;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内唯一的测量引擎。Activity 只负责画界面，定位回调进这里。
 * 锁屏靠 TrackService 保活；进程被杀后从 Prefs 草稿恢复。
 */
public final class TrackEngine {
    public static final String IDLE = "idle";
    public static final String RUNNING = "running";
    public static final String PAUSED = "paused";
    public static final String MODE_WALK = "walk";
    public static final String MODE_RUN = "run";
    public static final String MODE_RIDE = "ride";
    public static final String MODE_AUTO = "auto";

    public static boolean isRide(String mode) {
        return MODE_RIDE.equals(mode);
    }

    public static boolean isAuto(String mode) {
        return MODE_AUTO.equals(mode);
    }

    /** kcal ≈ 体重kg × 公里 × 系数。骑车按普通自行车粗估，不是电助力。自动模式按走路段算。 */
    public static double calorieFactor(String mode) {
        if (MODE_RUN.equals(mode)) {
            return 1.036;
        }
        if (MODE_RIDE.equals(mode)) {
            return 0.40;
        }
        return 0.75;
    }

    /** 到达/间隔提醒用的距离：自动模式只算真正走路。 */
    public static double alertDistanceM(TrackSession s) {
        if (s == null) {
            return 0;
        }
        return isAuto(s.mode) ? s.walkDistanceM : s.distanceM;
    }

    /** 到达/间隔提醒用的步数：自动模式只算走路段。 */
    public static int alertSteps(TrackSession s) {
        if (s == null) {
            return 0;
        }
        return isAuto(s.mode) ? s.walkSteps : s.steps;
    }

    /** 超过这个瞬时位移速度当 GPS 跳点丢掉。骑车允许更快。 */
    public static double maxImpliedMps(String mode) {
        return TrackClean.maxImpliedMps(mode);
    }

    public interface Listener {
        void onUpdate(TrackSession session, float currentSpeedMps, String gpsLabel,
                      boolean hasFix, TrackPoint lastFix);

        void onNewPoint(TrackPoint point);

        void onStopped(TrackSession saved);
    }

    private static TrackEngine instance;

    public static synchronized TrackEngine get(Context context) {
        if (instance == null) {
            instance = new TrackEngine(context.getApplicationContext());
        }
        return instance;
    }

    private final Context app;
    private final TrackStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private TrackSession session = new TrackSession();
    private TrackPoint lastFix;
    private TrackPoint lastAccepted;
    private long lastGpsElapsed;
    private long pauseBeganElapsed;
    private long lastTickElapsed;
    private float currentSpeedMps;
    private String gpsLabel = "正在定位…";
    private boolean hasFix;
    private float lastAccuracy = -1;
    private boolean autoStartArmed = true;
    private boolean previewing;
    private long lastDraftSavedAt;
    private long lastRegeoAt;
    private long lastAcceptedElapsed;
    /** 用来判断「停下」的锚点。GPS 原地漂点不重新计 12 秒。 */
    private TrackPoint stillAnchor;
    private long stillAnchorElapsed;
    /** 最近一次 GPS 时速达到约 0.5 m/s。用来判断原地时速 0。 */
    private long lastGpsMovingElapsed;
    private boolean autoStillActive;
    /** 点开始后是否已真正开始记时记步。等 GPS 时为 false。 */
    private boolean recordingReady;
    /** 已经有过合格 GPS 之后又丢星，且设置了丢星不计。 */
    private boolean gpsLostHold;
    private long lastGoodGpsElapsed;
    private static final long GPS_LOST_MS = 8_000L;
    private int autoKind;
    private int pendingKind;
    private long pendingKindSince;
    private int walkStepBase = -1;
    private final ArrayDeque<long[]> stepWin = new ArrayDeque<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            synchronized (TrackEngine.this) {
                if (!RUNNING.equals(session.state)) {
                    return;
                }
                long now = SystemClock.elapsedRealtime();
                noteStepSampleLocked(now);
                updateGpsLostHoldLocked(now);
                if (lastTickElapsed > 0) {
                    maybeAutoStillLocked(now);
                    long dt = now - lastTickElapsed;
                    if (holdCountingLocked()) {
                        if (recordingReady) {
                            session.pausedMs += dt;
                        }
                    } else {
                        session.movingMs += dt;
                        if (isAuto(session.mode)) {
                            classifyAutoLocked(now);
                            if (autoKind == TrackPoint.KIND_WALK) {
                                session.walkMovingMs += dt;
                            } else if (autoKind == TrackPoint.KIND_VEHICLE) {
                                session.vehicleMovingMs += dt;
                            }
                        }
                    }
                }
                if (holdCountingLocked() && !recordingReady) {
                    lastTickElapsed = 0;
                } else {
                    lastTickElapsed = now;
                }
                refreshCalories();
                BatterySnap.noteLive(session, app);
            }
            maybeAutoMark();
            emit();
            maybeSaveDraft(false);
            timer.postDelayed(this, 1000);
        }
    };

    private TrackEngine(Context app) {
        this.app = app;
        this.store = new TrackStore(app);
        restoreDraft();
        StepSensor.get(app).addListener(this::refresh);
    }

    public TrackStore store() {
        return store;
    }

    public synchronized TrackSession session() {
        return session;
    }

    public synchronized TrackPoint lastFix() {
        return lastFix;
    }

    public synchronized boolean isActive() {
        return RUNNING.equals(session.state) || PAUSED.equals(session.state);
    }

    public synchronized boolean isPreviewing() {
        return previewing;
    }

    public synchronized boolean autoStillActive() {
        return autoStillActive;
    }

    /** 已点开始，但还在等合格 GPS，用时和这次步数还没走。 */
    public synchronized boolean waitingForGps() {
        return RUNNING.equals(session.state) && !recordingReady;
    }

    /** 中途丢星且设置了不计，用时和这次步数冻住。 */
    public synchronized boolean gpsLostHold() {
        return RUNNING.equals(session.state) && gpsLostHold;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
        emit();
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public synchronized int autoKind() {
        return autoKind;
    }

    public synchronized void setPreviewing(boolean v) {
        previewing = v;
    }

    public synchronized void armAutoStart(boolean v) {
        autoStartArmed = v;
    }

    public synchronized boolean consumeAutoStart() {
        if (!autoStartArmed || previewing || isActive()) {
            return false;
        }
        if (!Prefs.autoStart(app)) {
            return false;
        }
        autoStartArmed = false;
        return true;
    }

    public void start(String mode) {
        synchronized (this) {
            if (isActive()) {
                return;
            }
            previewing = false;
            session = new TrackSession();
            session.mode = mode == null ? Prefs.lastMode(app) : mode;
            session.state = RUNNING;
            session.startMs = System.currentTimeMillis();
            session.uid = java.util.UUID.randomUUID().toString();
            session.steps = 0;
            session.stepAnchor = -1;
            BatterySnap.captureStart(session, app);
            session.autoMarkDueMs = 0;
            lastAccepted = null;
            stillAnchor = null;
            stillAnchorElapsed = 0;
            lastGpsMovingElapsed = 0;
            recordingReady = !Prefs.waitGpsStart(app);
            gpsLostHold = false;
            lastGoodGpsElapsed = 0;
            lastTickElapsed = recordingReady ? SystemClock.elapsedRealtime() : 0;
            lastAcceptedElapsed = lastTickElapsed;
            autoStillActive = false;
            autoKind = TrackPoint.KIND_NONE;
            pendingKind = TrackPoint.KIND_NONE;
            pendingKindSince = 0;
            walkStepBase = -1;
            stepWin.clear();
            pauseBeganElapsed = 0;
            currentSpeedMps = 0;
            autoStartArmed = false;
            Prefs.setLastMode(app, session.mode);
            syncStepsLocked();
        }
        TrackService.start(app);
        timer.removeCallbacks(tick);
        timer.post(tick);
        emit();
    }

    public void pause() {
        synchronized (this) {
            if (!RUNNING.equals(session.state)) {
                return;
            }
            syncStepsLocked();
            session.state = PAUSED;
            autoStillActive = false;
            pauseBeganElapsed = SystemClock.elapsedRealtime();
            lastTickElapsed = 0;
            currentSpeedMps = 0;
        }
        timer.removeCallbacks(tick);
        maybeSaveDraft(true);
        emit();
    }

    public void resume() {
        synchronized (this) {
            if (!PAUSED.equals(session.state)) {
                return;
            }
            if (pauseBeganElapsed > 0) {
                session.pausedMs += SystemClock.elapsedRealtime() - pauseBeganElapsed;
            }
            session.state = RUNNING;
            lastTickElapsed = SystemClock.elapsedRealtime();
            lastAcceptedElapsed = lastTickElapsed;
            autoStillActive = false;
            if (lastAccepted != null) {
                stillAnchor = lastAccepted;
                stillAnchorElapsed = lastTickElapsed;
            }
            lastGpsMovingElapsed = lastTickElapsed;
            pauseBeganElapsed = 0;
            session.stepAnchor = -1;
            if (isAuto(session.mode) && autoKind == TrackPoint.KIND_WALK) {
                walkStepBase = session.steps - session.walkSteps;
            }
            if (!recordingReady) {
                lastTickElapsed = 0;
            }
            syncStepsLocked();
        }
        TrackService.start(app);
        timer.removeCallbacks(tick);
        timer.post(tick);
        emit();
    }

    /**
     * 在当前位置打一个手动点。测距、用时继续跑，只记下当时的累计距离和用时。
     * 失败返回 null（还没开始，或还没有定位）。
     */
    public Checkpoint markNow() {
        return addMark(false);
    }

    private Checkpoint addMark(boolean auto) {
        final Checkpoint added;
        synchronized (this) {
            added = createMarkLocked(auto);
        }
        if (added == null) {
            return null;
        }
        maybeSaveDraft(true);
        requestMarkAddr(added);
        emit();
        return added;
    }

    private Checkpoint createMarkLocked(boolean auto) {
        if (!isActive()) {
            return null;
        }
        if (!recordingReady) {
            return null;
        }
        TrackPoint p = lastFix != null ? lastFix : lastAccepted;
        if (p == null) {
            return null;
        }
        if (!session.marks.isEmpty()) {
            Checkpoint last = session.marks.get(session.marks.size() - 1);
            long gap = System.currentTimeMillis() - last.t;
            if (!auto && gap < 1500) {
                return last;
            }
            if (auto && (gap < 20_000 || session.distanceM - last.distanceM < 20)) {
                return null;
            }
        }
        if (RUNNING.equals(session.state) && lastTickElapsed > 0 && !holdCountingLocked()) {
            long now = SystemClock.elapsedRealtime();
            session.movingMs += now - lastTickElapsed;
            lastTickElapsed = now;
        }
        Checkpoint m = new Checkpoint();
        m.t = System.currentTimeMillis();
        m.distanceM = session.distanceM;
        m.movingMs = session.movingMs;
        m.latGcj = p.latGcj;
        m.lngGcj = p.lngGcj;
        m.auto = auto;
        m.stepsAt = Math.max(0, session.steps);
        session.marks.add(m);
        session.recomputeMarkSegments();
        return m;
    }

    /** 按设置的分钟间隔、用测量用时打自动点。暂停不加时，没有定位就等下一秒。 */
    private void maybeAutoMark() {
        int min = Prefs.autoMarkMin(app);
        if (min <= 0) {
            return;
        }
        Checkpoint added = null;
        synchronized (this) {
            if (!RUNNING.equals(session.state)) {
                return;
            }
            long interval = min * 60_000L;
            if (session.autoMarkDueMs <= 0) {
                session.autoMarkDueMs = session.movingMs + interval;
                return;
            }
            if (session.autoMarkDueMs - session.movingMs > interval) {
                session.autoMarkDueMs = session.movingMs + interval;
            }
            if (session.movingMs < session.autoMarkDueMs) {
                return;
            }
            if (lastFix == null && lastAccepted == null) {
                return;
            }
            added = createMarkLocked(true);
            session.autoMarkDueMs = session.movingMs + interval;
        }
        if (added != null) {
            maybeSaveDraft(true);
            requestMarkAddr(added);
        }
    }

    public void stop(final boolean save) {
        TrackSession copy;
        synchronized (this) {
            if (!isActive()) {
                return;
            }
            timer.removeCallbacks(tick);
            if (RUNNING.equals(session.state) && lastTickElapsed > 0) {
                long dt = SystemClock.elapsedRealtime() - lastTickElapsed;
                if (holdCountingLocked()) {
                    if (recordingReady) {
                        session.pausedMs += dt;
                    }
                } else {
                    session.movingMs += dt;
                }
            }
            syncStepsLocked();
            session.endMs = System.currentTimeMillis();
            BatterySnap.captureEnd(session, app);
            session.state = IDLE;
            refreshCalories();
            copy = cloneSession(session);
            lastAccepted = null;
            lastTickElapsed = 0;
            lastAcceptedElapsed = 0;
            stillAnchor = null;
            stillAnchorElapsed = 0;
            lastGpsMovingElapsed = 0;
            autoStillActive = false;
            currentSpeedMps = 0;
            autoStartArmed = false;
            previewing = false;
        }
        rememberStride(copy);
        Prefs.setDraftJson(app, "");
        TrackService.stop(app);
        if (save && copy.distanceM >= 15) {
            fillEndAddr(copy);
            store.insert(copy);
            if (Net.isOnline(app)) {
                backfillWhenOnline();
            }
            final TrackSession saved = copy;
            main.post(() -> {
                for (Listener l : listeners) {
                    l.onStopped(saved);
                }
                emit();
            });
        } else {
            emit();
        }
    }

    public void onLocation(Location loc, boolean fromGps) {
        if (loc == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (fromGps) {
            lastGpsElapsed = now;
        } else if (now - lastGpsElapsed < 8000 && lastGpsElapsed > 0) {
            return;
        }

        float acc = loc.hasAccuracy() ? loc.getAccuracy() : 999f;
        int maxAcc = Prefs.maxAccuracyM(app);
        if (acc > maxAcc) {
            gpsLabel = "精度 " + Math.round(acc) + " 米，再等等";
            lastAccuracy = acc;
            hasFix = false;
            emit();
            return;
        }

        double wgsLat = loc.getLatitude();
        double wgsLng = loc.getLongitude();
        // 系统 GPS 是 WGS-84；画到高德地图前转 GCJ-02。网络定位只用来先把地图对准。
        double[] gcj = CoordTransform.wgs84ToGcj02(wgsLat, wgsLng);

        float speed = loc.hasSpeed() ? loc.getSpeed() : 0f;
        TrackPoint point = new TrackPoint(
                loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis(),
                gcj[0], gcj[1], wgsLat, wgsLng, acc, speed);

        boolean accepted = false;
        synchronized (this) {
            hasFix = true;
            lastAccuracy = acc;
            boolean tracking = RUNNING.equals(session.state) || PAUSED.equals(session.state);

            if (RUNNING.equals(session.state) && fromGps) {
                noteGoodGpsLocked(now);
                if (lastAccepted != null) {
                    double d = CoordTransform.haversineM(
                            lastAccepted.latWgs, lastAccepted.lngWgs, wgsLat, wgsLng);
                    double dt = Math.max(0.001, (point.t - lastAccepted.t) / 1000.0);
                    double implied = d / dt;
                    touchStillWindowLocked(point, speed, now);
                    maybeAutoStillLocked(now);
                    if (TrackClean.rejectHop(session.mode, d, dt)) {
                        gpsLabel = "飞点已忽略 " + Math.round(d) + " 米";
                    } else if (autoStillActive) {
                        if (TrackClean.leaveStill(session.mode, d, dt)) {
                            acceptPointLocked(point, d, implied, speed, now);
                            accepted = true;
                        } else {
                            gpsLabel = "已停下，不计时 · GPS " + Math.round(acc) + " 米";
                            currentSpeedMps = 0;
                        }
                    } else if (d < 1.2) {
                        currentSpeedMps = speed > 0.3f ? speed : 0f;
                        gpsLabel = (fromGps ? "GPS " : "网络 ") + Math.round(acc) + " 米";
                    } else {
                        acceptPointLocked(point, d, implied, speed, now);
                        accepted = true;
                        gpsLabel = (fromGps ? "GPS " : "网络 ") + Math.round(acc) + " 米";
                    }
                } else {
                    acceptFirstLocked(point, now);
                    accepted = true;
                    gpsLabel = (fromGps ? "GPS " : "网络 ") + Math.round(acc) + " 米";
                    if (session.startAddr == null || session.startAddr.isEmpty()) {
                        requestStartAddr(point);
                    }
                }
                if (autoStillActive && gpsLabel != null && !gpsLabel.startsWith("飞点")) {
                    gpsLabel = "已停下，不计时 · GPS " + Math.round(acc) + " 米";
                }
                lastFix = lastAccepted != null ? lastAccepted : point;
            } else {
                currentSpeedMps = speed;
                gpsLabel = (fromGps ? "GPS " : "网络 ") + Math.round(acc) + " 米";
                lastFix = tracking && lastAccepted != null ? lastAccepted : point;
            }
        }

        if (accepted) {
            maybeSaveDraft(false);
            maybeRefreshPlace(point);
            final TrackPoint emitPoint = point;
            main.post(() -> {
                for (Listener l : listeners) {
                    l.onNewPoint(emitPoint);
                }
            });
        }
        emit();
    }

    public void onProviderStatus(String text) {
        if (!hasFix) {
            gpsLabel = text;
            emit();
        }
    }

    private void requestStartAddr(TrackPoint p) {
        AmapClient.regeo(app, p.latGcj, p.lngGcj, addr -> {
            synchronized (TrackEngine.this) {
                if (addr != null && !addr.isEmpty()
                        && (session.startAddr == null || session.startAddr.isEmpty())) {
                    session.startAddr = addr;
                }
            }
            emit();
        });
    }

    private void requestMarkAddr(final Checkpoint mark) {
        if (mark == null) {
            return;
        }
        AmapClient.regeo(app, mark.latGcj, mark.lngGcj, addr -> {
            if (addr == null || addr.isEmpty()) {
                return;
            }
            synchronized (TrackEngine.this) {
                for (Checkpoint c : session.marks) {
                    if (c.n == mark.n) {
                        c.addr = addr;
                        break;
                    }
                }
            }
            emit();
        });
    }

    private void maybeRefreshPlace(TrackPoint p) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRegeoAt < 20000) {
            return;
        }
        lastRegeoAt = now;
        AmapClient.regeo(app, p.latGcj, p.lngGcj, addr -> {
            if (addr == null || addr.isEmpty()) {
                return;
            }
            synchronized (TrackEngine.this) {
                session.endAddr = addr;
            }
            emit();
        });
    }

    private void fillEndAddr(TrackSession s) {
        if (s.startAddr == null || Formats.needsGeocode(s.startAddr)) {
            s.startAddr = session.startAddr == null ? "" : session.startAddr;
        }
        if (s.endAddr == null || Formats.needsGeocode(s.endAddr)) {
            s.endAddr = session.endAddr == null ? "" : session.endAddr;
        }
        if (Formats.needsGeocode(s.startAddr)) {
            s.startAddr = "";
        }
        if (Formats.needsGeocode(s.endAddr)) {
            s.endAddr = "";
        }
    }

    /** 联网后补当前测量和历史记录里空着的出发/到达地址。逆地理编码走单线程队列。 */
    public void backfillWhenOnline() {
        if (!Net.isOnline(app)) {
            return;
        }
        lastRegeoAt = 0;
        TrackSession cur;
        synchronized (this) {
            cur = cloneSession(session);
        }
        if (!cur.points.isEmpty()) {
            TrackPoint first = cur.points.get(0);
            TrackPoint last = cur.points.get(cur.points.size() - 1);
            if (isActive() && Formats.needsGeocode(cur.startAddr)) {
                requestStartAddr(first);
            }
            if (isActive() && Formats.needsGeocode(cur.endAddr)) {
                maybeRefreshPlace(last);
            }
        }
        int n = 0;
        for (TrackSession s : store.list()) {
            if (n >= 12) {
                break;
            }
            if (s.points.isEmpty()) {
                continue;
            }
            boolean needStart = Formats.needsGeocode(s.startAddr);
            boolean needEnd = Formats.needsGeocode(s.endAddr);
            if (!needStart && !needEnd) {
                continue;
            }
            n++;
            TrackPoint first = s.points.get(0);
            TrackPoint last = s.points.get(s.points.size() - 1);
            if (needStart) {
                final long id = s.id;
                AmapClient.regeo(app, first.latGcj, first.lngGcj, addr -> {
                    if (addr == null || addr.isEmpty()) {
                        return;
                    }
                    TrackSession row = store.get(id);
                    if (row == null) {
                        return;
                    }
                    store.updateAddrs(id, addr, row.endAddr);
                    emit();
                });
            }
            if (needEnd) {
                final long id = s.id;
                AmapClient.regeo(app, last.latGcj, last.lngGcj, addr -> {
                    if (addr == null || addr.isEmpty()) {
                        return;
                    }
                    TrackSession row = store.get(id);
                    if (row == null) {
                        return;
                    }
                    store.updateAddrs(id, row.startAddr, addr);
                    emit();
                });
            }
        }
    }

    private void refreshCalories() {
        double km;
        if (isAuto(session.mode)) {
            km = session.walkDistanceM / 1000.0;
        } else {
            km = session.distanceM / 1000.0;
        }
        double kg = Prefs.weightJin(app) / 2.0;
        session.calories = kg * km * calorieFactor(session.mode);
    }

    private void maybeSaveDraft(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastDraftSavedAt < 8000) {
            return;
        }
        lastDraftSavedAt = now;
        try {
            TrackSession copy;
            synchronized (this) {
                if (!isActive()) {
                    return;
                }
                copy = cloneSession(session);
            }
            Prefs.setDraftJson(app, copy.toJson().toString());
        } catch (JSONException ignored) {
        }
    }

    private void restoreDraft() {
        TrackSession draft = TrackSession.fromJson(Prefs.draftJson(app));
        if (draft == null) {
            return;
        }
        boolean waiting = draft.points.isEmpty();
        if (waiting && !RUNNING.equals(draft.state) && !PAUSED.equals(draft.state)) {
            return;
        }
        if (!RUNNING.equals(draft.state) && !PAUSED.equals(draft.state)) {
            draft.state = RUNNING;
        }
        session = draft;
        if (session.uid == null || session.uid.isEmpty()) {
            session.uid = java.util.UUID.randomUUID().toString();
        }
        gpsLostHold = false;
        lastGoodGpsElapsed = 0;
        if (!session.points.isEmpty()) {
            lastAccepted = session.points.get(session.points.size() - 1);
            lastFix = lastAccepted;
            hasFix = true;
            recordingReady = true;
            lastGoodGpsElapsed = SystemClock.elapsedRealtime();
        } else {
            recordingReady = !Prefs.waitGpsStart(app);
        }
        lastAcceptedElapsed = SystemClock.elapsedRealtime();
        stillAnchor = lastAccepted;
        stillAnchorElapsed = lastAcceptedElapsed;
        lastGpsMovingElapsed = lastAcceptedElapsed;
        autoStillActive = false;
        autoKind = session.autoKind;
        pendingKind = autoKind;
        walkStepBase = autoKind == TrackPoint.KIND_WALK
                ? session.steps - session.walkSteps
                : -1;
        autoStartArmed = false;
        if (RUNNING.equals(session.state)) {
            lastTickElapsed = recordingReady ? SystemClock.elapsedRealtime() : 0;
            lastAcceptedElapsed = lastTickElapsed > 0 ? lastTickElapsed : lastAcceptedElapsed;
            timer.post(tick);
        }
    }

    private static TrackSession cloneSession(TrackSession src) {
        try {
            return TrackSession.fromJson(src.toJson());
        } catch (JSONException e) {
            TrackSession s = new TrackSession();
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
            s.points.addAll(src.points);
            s.marks.addAll(src.marks);
            return s;
        }
    }

    private void acceptFirstLocked(TrackPoint point, long nowElapsed) {
        tagAutoPointLocked(point, nowElapsed);
        session.points.add(point);
        lastAccepted = point;
        lastAcceptedElapsed = nowElapsed;
        stillAnchor = point;
        stillAnchorElapsed = nowElapsed;
        if (lastGpsMovingElapsed <= 0) {
            lastGpsMovingElapsed = nowElapsed;
        }
        leaveStillLocked();
    }

    private void acceptPointLocked(TrackPoint point, double d, double implied, float speed,
                                   long nowElapsed) {
        session.distanceM += d;
        float useSpeed = speed > 0.3f ? speed : (float) implied;
        if (speed < 0.3f && d < TrackClean.STILL_M) {
            useSpeed = 0f;
        }
        currentSpeedMps = useSpeed;
        if (useSpeed > session.maxSpeedMps && d > 3) {
            session.maxSpeedMps = useSpeed;
        }
        tagAutoPointLocked(point, nowElapsed);
        if (isAuto(session.mode)) {
            if (point.kind == TrackPoint.KIND_WALK) {
                session.walkDistanceM += d;
            } else if (point.kind == TrackPoint.KIND_VEHICLE) {
                session.vehicleDistanceM += d;
            }
        }
        session.points.add(point);
        lastAccepted = point;
        lastAcceptedElapsed = nowElapsed;
        if (d >= TrackClean.STILL_M || speed >= 0.5f) {
            stillAnchor = point;
            stillAnchorElapsed = nowElapsed;
        }
        leaveStillLocked();
        refreshCalories();
    }

    private void tagAutoPointLocked(TrackPoint point, long nowElapsed) {
        point.stepsAt = Math.max(0, session.steps);
        if (!isAuto(session.mode)) {
            point.kind = TrackPoint.KIND_NONE;
            return;
        }
        classifyAutoLocked(nowElapsed);
        if (autoKind == TrackPoint.KIND_NONE) {
            int steps10 = stepsInWindowLocked(nowElapsed, 10_000L);
            if (steps10 >= 6 && currentSpeedMps < 4.5f) {
                enterAutoKindLocked(TrackPoint.KIND_WALK);
            } else if (currentSpeedMps >= 1.5f || steps10 < 2) {
                if (currentSpeedMps >= 1.5f && steps10 < 4) {
                    enterAutoKindLocked(TrackPoint.KIND_VEHICLE);
                }
            }
        }
        point.kind = autoKind;
        session.autoKind = autoKind;
    }

    /**
     * 停下看的是「约 12 秒净位移不到约 8 米」或「GPS 时速持续约 0」。
     * 以前只看「有没有收点」：原地漂点、超过 2.5 秒也会收下，12 秒计时被清掉，时速 0 了用时还在加。
     */
    private void touchStillWindowLocked(TrackPoint p, float gpsSpeed, long nowElapsed) {
        if (p == null) {
            return;
        }
        if (lastGpsMovingElapsed <= 0 || gpsSpeed >= 0.5f) {
            lastGpsMovingElapsed = nowElapsed;
        }
        if (stillAnchor == null || stillAnchorElapsed <= 0) {
            stillAnchor = p;
            stillAnchorElapsed = nowElapsed;
            return;
        }
        double d = CoordTransform.haversineM(
                stillAnchor.latWgs, stillAnchor.lngWgs, p.latWgs, p.lngWgs);
        long held = nowElapsed - stillAnchorElapsed;
        double implied = d / Math.max(0.001, held / 1000.0);
        boolean reallyMoving = gpsSpeed >= 0.5f
                || (d >= TrackClean.STILL_M && implied >= 1.0 && held >= 8_000L);
        if (reallyMoving) {
            stillAnchor = p;
            stillAnchorElapsed = nowElapsed;
        }
    }

    private void maybeAutoStillLocked(long nowElapsed) {
        if (!RUNNING.equals(session.state) || !Prefs.autoStill(app)) {
            if (autoStillActive) {
                leaveStillLocked();
            }
            return;
        }
        if (stillAnchor == null || stillAnchorElapsed <= 0) {
            if (lastAccepted == null) {
                return;
            }
            stillAnchor = lastAccepted;
            stillAnchorElapsed = lastAcceptedElapsed > 0 ? lastAcceptedElapsed : nowElapsed;
        }
        if (nowElapsed - stillAnchorElapsed < TrackClean.STILL_MS) {
            return;
        }
        double net = 0;
        if (lastAccepted != null) {
            net = CoordTransform.haversineM(
                    stillAnchor.latWgs, stillAnchor.lngWgs,
                    lastAccepted.latWgs, lastAccepted.lngWgs);
        }
        boolean littleMove = net < TrackClean.STILL_M;
        boolean parked = lastGpsMovingElapsed > 0
                && nowElapsed - lastGpsMovingElapsed >= TrackClean.STILL_MS;
        if (littleMove || parked) {
            enterStillLocked();
        }
    }

    private void enterStillLocked() {
        if (autoStillActive) {
            return;
        }
        syncStepsLocked();
        autoStillActive = true;
        currentSpeedMps = 0;
    }

    private void leaveStillLocked() {
        if (!autoStillActive) {
            return;
        }
        autoStillActive = false;
        stillAnchor = lastAccepted;
        stillAnchorElapsed = SystemClock.elapsedRealtime();
        lastGpsMovingElapsed = stillAnchorElapsed;
        session.stepAnchor = -1;
        if (RUNNING.equals(session.state)) {
            syncStepsLocked();
        }
    }

    private void syncStepsLocked() {
        StepSensor ss = StepSensor.get(app);
        ss.start();
        if (!ss.ready()) {
            return;
        }
        long raw = ss.raw();
        if (!RUNNING.equals(session.state)) {
            return;
        }
        if (holdCountingLocked()) {
            return;
        }
        if (session.stepAnchor < 0) {
            session.stepAnchor = raw - Math.max(0, session.steps);
            if (session.stepAnchor < 0) {
                session.stepAnchor = raw;
            }
        }
        long n = raw - session.stepAnchor;
        if (n < 0) {
            session.stepAnchor = raw - session.steps;
            n = session.steps;
        }
        if (n > 1_000_000) {
            return;
        }
        session.steps = (int) n;
        if (isAuto(session.mode) && autoKind == TrackPoint.KIND_WALK && !autoStillActive) {
            if (walkStepBase < 0) {
                walkStepBase = session.steps - session.walkSteps;
            }
            session.walkSteps = Math.max(0, session.steps - walkStepBase);
        }
    }

    private void noteStepSampleLocked(long nowElapsed) {
        StepSensor ss = StepSensor.get(app);
        if (!ss.ready()) {
            return;
        }
        long raw = ss.raw();
        if (raw < 0) {
            return;
        }
        stepWin.addLast(new long[]{nowElapsed, raw});
        while (stepWin.size() > 40
                || (!stepWin.isEmpty() && nowElapsed - stepWin.peekFirst()[0] > 22_000L)) {
            stepWin.removeFirst();
        }
    }

    private int stepsInWindowLocked(long nowElapsed, long windowMs) {
        if (stepWin.isEmpty()) {
            return 0;
        }
        long newest = stepWin.peekLast()[1];
        long oldest = newest;
        for (long[] s : stepWin) {
            if (nowElapsed - s[0] <= windowMs) {
                oldest = s[1];
                break;
            }
        }
        long d = newest - oldest;
        if (d < 0 || d > 8000) {
            return 0;
        }
        return (int) d;
    }

    private void classifyAutoLocked(long nowElapsed) {
        if (!isAuto(session.mode) || !RUNNING.equals(session.state)) {
            return;
        }
        noteStepSampleLocked(nowElapsed);
        int steps10 = stepsInWindowLocked(nowElapsed, 10_000L);
        float spd = currentSpeedMps;
        boolean stepping = steps10 >= 6;
        int want = autoKind;
        if (autoStillActive) {
            if (autoKind == TrackPoint.KIND_WALK && steps10 >= 4) {
                session.cadenceSpm = steps10 * 6f;
            }
            session.autoKind = autoKind;
            return;
        }
        if (spd >= 6.1f) {
            want = TrackPoint.KIND_VEHICLE;
        } else if (stepping && spd < 4.5f) {
            want = TrackPoint.KIND_WALK;
        } else if (!stepping && spd >= 1.5f) {
            want = TrackPoint.KIND_VEHICLE;
        } else if (stepping && spd >= 4.5f) {
            float cad = steps10 * 6f;
            want = cad >= 140f ? TrackPoint.KIND_WALK : TrackPoint.KIND_VEHICLE;
        }
        if (want != TrackPoint.KIND_NONE && want != autoKind) {
            long need = spd >= 6.1f || autoKind == TrackPoint.KIND_NONE ? 0L : 5000L;
            if (pendingKind != want) {
                pendingKind = want;
                pendingKindSince = nowElapsed;
            }
            if (nowElapsed - pendingKindSince >= need) {
                enterAutoKindLocked(want);
            }
        } else if (want == autoKind) {
            pendingKind = autoKind;
        }
        session.autoKind = autoKind;
        if (autoKind == TrackPoint.KIND_WALK && steps10 >= 4) {
            session.cadenceSpm = steps10 * 6f;
        } else if (autoKind != TrackPoint.KIND_WALK) {
            session.cadenceSpm = 0;
        }
    }

    private void enterAutoKindLocked(int kind) {
        if (kind == autoKind) {
            return;
        }
        if (autoKind == TrackPoint.KIND_WALK && kind != TrackPoint.KIND_WALK) {
            walkStepBase = -1;
        }
        autoKind = kind;
        pendingKind = kind;
        session.autoKind = kind;
        if (kind == TrackPoint.KIND_WALK) {
            walkStepBase = session.steps - session.walkSteps;
        }
    }

    private boolean holdCountingLocked() {
        return autoStillActive || !recordingReady || gpsLostHold;
    }

    private void noteGoodGpsLocked(long nowElapsed) {
        lastGoodGpsElapsed = nowElapsed;
        if (!recordingReady) {
            recordingReady = true;
            lastTickElapsed = nowElapsed;
            session.stepAnchor = -1;
            syncStepsLocked();
        }
        if (gpsLostHold) {
            leaveGpsLostHoldLocked();
        }
    }

    private void updateGpsLostHoldLocked(long nowElapsed) {
        if (!RUNNING.equals(session.state)) {
            return;
        }
        if (Prefs.recordIfGpsLost(app) || lastGoodGpsElapsed <= 0) {
            if (gpsLostHold) {
                leaveGpsLostHoldLocked();
            }
            return;
        }
        if (nowElapsed - lastGoodGpsElapsed >= GPS_LOST_MS) {
            enterGpsLostHoldLocked();
        }
    }

    private void enterGpsLostHoldLocked() {
        if (gpsLostHold || !recordingReady) {
            return;
        }
        syncStepsLocked();
        gpsLostHold = true;
        currentSpeedMps = 0;
    }

    private void leaveGpsLostHoldLocked() {
        if (!gpsLostHold) {
            return;
        }
        gpsLostHold = false;
        session.stepAnchor = -1;
        if (RUNNING.equals(session.state) && recordingReady) {
            lastTickElapsed = SystemClock.elapsedRealtime();
            syncStepsLocked();
        }
    }

    private String overlayGpsLabelLocked(String base) {
        if (RUNNING.equals(session.state) && !recordingReady) {
            if (lastAccuracy > 0) {
                return "精度 " + Math.round(lastAccuracy) + " 米，合格后再开始记";
            }
            return "等 GPS 合格再开始记（须 ≤ " + Prefs.maxAccuracyM(app) + " 米）";
        }
        if (RUNNING.equals(session.state) && gpsLostHold) {
            if (lastAccuracy > 0) {
                return "丢星 · 精度 " + Math.round(lastAccuracy) + " 米，不计时计步";
            }
            return "丢星，不计时计步";
        }
        return base;
    }

    private void rememberStride(TrackSession s) {
        if (s == null) {
            return;
        }
        if (isAuto(s.mode)) {
            if (s.walkSteps >= 20 && s.walkDistanceM >= 20) {
                Prefs.setLastStrideM(app, (float) (s.walkDistanceM / s.walkSteps));
            }
            return;
        }
        if (isRide(s.mode) || s.steps < 20 || s.distanceM < 20) {
            return;
        }
        Prefs.setLastStrideM(app, (float) (s.distanceM / s.steps));
    }

    public void refresh() {
        synchronized (this) {
            if (isActive() && !recordingReady && !Prefs.waitGpsStart(app)) {
                recordingReady = true;
                if (RUNNING.equals(session.state)) {
                    lastTickElapsed = SystemClock.elapsedRealtime();
                }
                session.stepAnchor = -1;
                syncStepsLocked();
            }
            updateGpsLostHoldLocked(SystemClock.elapsedRealtime());
        }
        emit();
    }

    public void noteBattery() {
        synchronized (this) {
            if (!isActive()) {
                return;
            }
            BatterySnap.noteLive(session, app);
        }
    }

    private void emit() {
        final TrackSession snap;
        final float speed;
        final String label;
        final boolean fix;
        final TrackPoint fixPoint;
        final java.util.List<GoalAlerts.Hit> alerts;
        synchronized (this) {
            syncStepsLocked();
            updateGpsLostHoldLocked(SystemClock.elapsedRealtime());
            if (isAuto(session.mode) && RUNNING.equals(session.state)) {
                classifyAutoLocked(SystemClock.elapsedRealtime());
            }
            alerts = GoalAlerts.checkTripLocked(app, session);
            snap = cloneSession(session);
            speed = currentSpeedMps;
            label = overlayGpsLabelLocked(gpsLabel);
            fix = hasFix;
            fixPoint = lastFix;
        }
        if (alerts != null) {
            for (GoalAlerts.Hit h : alerts) {
                AlertNotify.show(app, h.title, h.text, h.kind);
            }
        }
        main.post(() -> {
            for (Listener l : listeners) {
                l.onUpdate(snap, speed, label, fix, fixPoint);
            }
        });
    }
}
