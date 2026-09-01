package com.example.mapdistance;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.Calendar;
import java.util.Locale;

/**
 * 零点附近拍一次计步芯片累计值，当作「今日」的起点。
 * 运动健康有系统服务 24 小时听着；我们只能尽量在零点醒一下。
 * 小米仍可能拦闹钟，拦了就只能从第一次打开开始记。
 */
public final class StepMidnight {
    public static final String ACTION = "com.example.mapdistance.STEP_MIDNIGHT";

    private StepMidnight() {}

    public static void schedule(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = pending(app);
        Calendar cal = Calendar.getInstance(Locale.CHINA);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 25);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 15_000) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        long at = cal.getTimeInMillis();
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static PendingIntent pending(Context app) {
        Intent i = new Intent(app, Receiver.class);
        i.setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(app, 17, i, flags);
    }

    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final PendingResult pr = goAsync();
            final Context app = context.getApplicationContext();
            final java.util.concurrent.atomic.AtomicBoolean done =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            Runnable finish = () -> {
                if (done.compareAndSet(false, true)) {
                    pr.finish();
                }
            };
            StepSensor.get(app).start();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    StepSensor.get(app).noteMidnightPulse();
                    schedule(app);
                } finally {
                    finish.run();
                }
            }, 2500);
            new Handler(Looper.getMainLooper()).postDelayed(finish, 8000);
        }
    }
}
