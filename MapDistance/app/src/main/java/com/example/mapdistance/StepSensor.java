package com.example.mapdistance;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 读系统 TYPE_STEP_COUNTER（开机以来累计步数）。
 * 小米运动健康的全天步数来自 MIUI 系统服务，第三方读不到；
 * 这颗芯片和运动健康用的是同一颗，测量进行中对照步数和 GPS 公里足够准。
 * 今日步数：同一天内即使 App 被杀，下次打开用累计值补上中间那段。
 */
public final class StepSensor implements SensorEventListener {
    public enum Status { OK, NO_SENSOR, NO_PERM, WAITING }

    public interface Listener {
        void onSteps();
    }

    private static StepSensor instance;

    public static synchronized StepSensor get(Context c) {
        if (instance == null) {
            instance = new StepSensor(c.getApplicationContext());
        }
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private SensorManager sm;
    private Sensor counter;
    private boolean registered;
    private long raw = -1;
    private int today;
    private Status status = Status.WAITING;

    private StepSensor(Context app) {
        this.app = app;
        today = Prefs.todaySteps(app);
        start();
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public Status status() {
        return status;
    }

    public long raw() {
        return raw;
    }

    public boolean ready() {
        return raw >= 0;
    }

    public int today() {
        return Math.max(0, today);
    }

    public synchronized void start() {
        if (!hasPerm()) {
            if (status != Status.NO_PERM) {
                status = Status.NO_PERM;
                unregister();
                notifyListeners();
            }
            return;
        }
        if (sm == null) {
            sm = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        }
        if (sm == null) {
            if (status != Status.NO_SENSOR) {
                status = Status.NO_SENSOR;
                notifyListeners();
            }
            return;
        }
        if (counter == null) {
            counter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }
        if (counter == null) {
            if (status != Status.NO_SENSOR) {
                status = Status.NO_SENSOR;
                notifyListeners();
            }
            return;
        }
        if (!registered) {
            registered = sm.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL);
            if (!registered) {
                status = Status.NO_SENSOR;
                notifyListeners();
                return;
            }
            if (status == Status.NO_PERM || status == Status.NO_SENSOR) {
                status = raw >= 0 ? Status.OK : Status.WAITING;
                notifyListeners();
            }
        }
    }

    public static boolean hasPerm(Context c) {
        if (Build.VERSION.SDK_INT < 29) {
            return true;
        }
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPerm() {
        return hasPerm(app);
    }

    private void unregister() {
        if (registered && sm != null) {
            sm.unregisterListener(this);
        }
        registered = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length == 0) {
            return;
        }
        long v = (long) event.values[0];
        if (v < 0) {
            return;
        }
        applyRaw(v);
        status = Status.OK;
        notifyListeners();
        main.post(() -> GoalAlerts.checkToday(app));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private synchronized void applyRaw(long v) {
        String day = dayKey();
        String lastDay = Prefs.stepDay(app);
        long lastRaw = Prefs.stepRaw(app);
        long lastMs = Prefs.stepRawMs(app);
        long now = System.currentTimeMillis();
        long midnight = startOfDay(now);
        boolean gap = Prefs.stepGap(app);

        if (!day.equals(lastDay)) {
            long base;
            if (day.equals(Prefs.midnightDay(app)) && Prefs.midnightRaw(app) >= 0
                    && v >= Prefs.midnightRaw(app)) {
                base = Prefs.midnightRaw(app);
                gap = false;
            } else if (lastRaw >= 0 && v >= lastRaw && lastMs > 0
                    && lastMs >= midnight - 2L * 3600_000L
                    && lastMs <= midnight + 15L * 60_000L) {
                base = lastRaw;
                gap = lastMs < midnight;
                Prefs.setMidnight(app, day, base);
            } else {
                base = v;
                gap = true;
                Prefs.setMidnight(app, day, base);
            }
            today = (int) Math.max(0, Math.min(v - base, 300000));
            raw = v;
            Prefs.setSteps(app, day, v, today, now, gap);
            return;
        }

        long base = -1;
        if (day.equals(Prefs.midnightDay(app))) {
            base = Prefs.midnightRaw(app);
        }
        if (base >= 0 && v >= base) {
            today = (int) Math.max(0, Math.min(v - base, 300000));
        } else if (lastRaw >= 0 && v >= lastRaw) {
            long delta = v - lastRaw;
            if (delta > 200000) {
                delta = 0;
            }
            today = Prefs.todaySteps(app) + (int) delta;
        } else if (lastRaw > v) {
            today = Prefs.todaySteps(app) + (int) Math.min(v, 100000);
        } else {
            today = Prefs.todaySteps(app);
        }
        raw = v;
        Prefs.setSteps(app, day, v, today, now, gap);
    }

    /** 零点闹钟：把当前累计值当成今日起点。没读到传感器就不动。 */
    public synchronized void noteMidnightPulse() {
        start();
        if (raw < 0) {
            return;
        }
        String day = dayKey();
        long now = System.currentTimeMillis();
        long midnight = startOfDay(now);
        long minOfDay = (now - midnight) / 60000L;
        if (minOfDay <= 15) {
            Prefs.setMidnight(app, day, raw);
            today = 0;
            Prefs.setSteps(app, day, raw, 0, now, false);
            return;
        }
        if (!day.equals(Prefs.midnightDay(app))) {
            long base = raw - Math.max(0, today);
            Prefs.setMidnight(app, day, Math.max(0, base));
        }
    }

    public String todayText(Context c) {
        if (status == Status.NO_PERM) {
            return "今日步数：点这里允许「身体活动」";
        }
        if (status == Status.NO_SENSOR) {
            return "这台手机没有计步传感器";
        }
        if (status == Status.WAITING || !ready()) {
            return "今日步数：正在读传感器…";
        }
        float stride = Prefs.lastStrideM(c);
        double km = today() * stride / 1000.0;
        if (today() < 30) {
            if (Prefs.stepGap(c)) {
                return String.format(Locale.CHINA, "今日 %s（零点到打开前可能少记）",
                        Formats.steps(today()));
            }
            return String.format(Locale.CHINA, "今日 %s", Formats.steps(today()));
        }
        String gap = Prefs.stepGap(c) ? " · 零点到打开前可能少记" : "";
        return String.format(Locale.CHINA, "今日 %s · 约 %s（按最近步幅）%s",
                Formats.steps(today()), Formats.distance(km * 1000.0), gap);
    }

    private void notifyListeners() {
        main.post(() -> {
            for (Listener l : listeners) {
                l.onSteps();
            }
        });
    }

    static String dayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
    }

    private static long startOfDay(long now) {
        Calendar cal = Calendar.getInstance(Locale.CHINA);
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
