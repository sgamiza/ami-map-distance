package com.example.mapdistance;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class TrackService extends Service implements TrackEngine.Listener {
    public static final String ACTION_PAUSE = "com.example.mapdistance.PAUSE";
    public static final String ACTION_RESUME = "com.example.mapdistance.RESUME";
    private static final String CH = "track";
    private static final int NID = 42;

    private LocationManager locations;
    private PowerManager.WakeLock wakeLock;
    private TrackEngine engine;
    private float lastSpeedMps;
    private final Handler pulse = new Handler(Looper.getMainLooper());
    private final Runnable pulseBatt = new Runnable() {
        @Override
        public void run() {
            if (engine == null || !engine.isActive()) {
                return;
            }
            engine.noteBattery();
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NID, liveNotification(lastSpeedMps));
            pulse.postDelayed(this, 15000);
        }
    };

    public static void start(Context context) {
        Intent i = new Intent(context, TrackService.class);
        ContextCompat.startForegroundService(context, i);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, TrackService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        engine = TrackEngine.get(this);
        ensureChannel();
        startForeground(NID, liveNotification(0f));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ami:track");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
        engine.addListener(this);
        startLocation();
        pulse.removeCallbacks(pulseBatt);
        pulse.postDelayed(pulseBatt, 15000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(action)) {
            engine.pause();
        } else if (ACTION_RESUME.equals(action)) {
            engine.resume();
        }
        if (!engine.isActive()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NID, liveNotification(0f));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        engine.removeListener(this);
        pulse.removeCallbacks(pulseBatt);
        stopLocation();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startLocation() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locations = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener);
        } catch (Exception ignored) {
        }
        try {
            locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 0, netListener);
        } catch (Exception ignored) {
        }
        Location last = null;
        try {
            last = locations.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) {
                last = locations.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception ignored) {
        }
        if (last != null) {
            engine.onLocation(last, LocationManager.GPS_PROVIDER.equals(last.getProvider()));
        }
    }

    private void stopLocation() {
        if (locations == null) {
            return;
        }
        try {
            locations.removeUpdates(gpsListener);
        } catch (Exception ignored) {
        }
        try {
            locations.removeUpdates(netListener);
        } catch (Exception ignored) {
        }
    }

    private final LocationListener gpsListener = new SimpleListener(true);
    private final LocationListener netListener = new SimpleListener(false);

    private final class SimpleListener implements LocationListener {
        private final boolean gps;

        SimpleListener(boolean gps) {
            this.gps = gps;
        }

        @Override
        public void onLocationChanged(Location location) {
            engine.onLocation(location, gps);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
            engine.onProviderStatus(gps ? "GPS 已打开" : "网络定位已打开");
        }

        @Override
        public void onProviderDisabled(String provider) {
            engine.onProviderStatus(gps ? "请打开系统定位 / GPS" : "网络定位关闭");
        }
    }

    @Override
    public void onUpdate(TrackSession session, float currentSpeedMps, String gpsLabel,
                         boolean hasFix, TrackPoint lastFix) {
        if (!engine.isActive()) {
            return;
        }
        lastSpeedMps = currentSpeedMps;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NID, liveNotification(currentSpeedMps));
    }

    @Override
    public void onNewPoint(TrackPoint point) {
    }

    @Override
    public void onStopped(TrackSession saved) {
        stopSelf();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                CH, "测量进行中", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("走路、跑步、骑车或自动识别时显示距离、用时、步数和本段电量；可暂停。结束要进 App 确认。");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(ch);
    }

    private Notification liveNotification(float speedMps) {
        TrackSession s = engine.session();
        boolean paused = TrackEngine.PAUSED.equals(s.state);
        boolean still = engine.autoStillActive();
        boolean waitGps = engine.waitingForGps();
        boolean lost = engine.gpsLostHold();
        String hold = paused ? " · 已暂停"
                : (waitGps ? " · 等 GPS" : (lost ? " · 丢星" : (still ? " · 已停下" : "中")));
        String head;
        String line1;
        String line2;
        if (TrackEngine.isAuto(s.mode)) {
            String kind = Formats.autoKindLabel(s.autoKind);
            head = "自动·" + kind + hold;
            line1 = "走路 " + Formats.distance(s.walkDistanceM)
                    + "  ·  车程 " + Formats.distance(s.vehicleDistanceM);
            line2 = (s.walkSteps > 0 ? Formats.steps(s.walkSteps) : "步数 --")
                    + "  ·  " + Formats.speed(this, speedMps)
                    + (s.cadenceSpm >= 20 ? "  ·  " + Formats.cadence(s.cadenceSpm) : "");
        } else {
            head = Formats.modeLabel(s.mode) + hold;
            line1 = Formats.distance(s.distanceM) + "  ·  " + Formats.duration(s.movingMs);
            line2 = (s.steps > 0 ? Formats.steps(s.steps) : "步数 --")
                    + "  ·  " + Formats.speed(this, speedMps);
        }
        String battShort = BatterySnap.sessionShort(this, s);
        String battLine = BatterySnap.sessionLine(this, s);
        String collapsed = line2 + (battShort.isEmpty() ? "" : "  ·  " + battShort);
        String expanded = line1 + "\n" + line2
                + (battLine.isEmpty() ? "" : "\n" + battLine);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, flags);

        Intent end = new Intent(this, MainActivity.class);
        end.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        end.putExtra(MainActivity.EXTRA_CONFIRM_STOP, true);
        PendingIntent endPi = PendingIntent.getActivity(this, 3, end, flags);

        Intent toggle = new Intent(this, TrackService.class);
        toggle.setAction(paused ? ACTION_RESUME : ACTION_PAUSE);
        PendingIntent togglePi = PendingIntent.getService(this, paused ? 2 : 1, toggle, flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(head + "  ·  " + line1)
                .setContentText(collapsed)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .setBigContentTitle(head)
                        .bigText(expanded))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openPi)
                .addAction(0, paused ? "继续" : "暂停", togglePi)
                .addAction(0, "结束…", endPi);
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }
}
