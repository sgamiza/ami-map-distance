package com.example.mapdistance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** 定时备份心跳。即使很久没打开 App，脏数据仍会落到磁盘。 */
public class BackupAlarm extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Backups.ACTION_TICK.equals(intent.getAction())) {
            return;
        }
        Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                TrackStore store = TrackEngine.get(app).store();
                String msg = Backups.runScheduled(app, store);
                Log.i("AmiBackup", "scheduled check: " + (msg == null ? "无需备份" : msg));
            } catch (RuntimeException e) {
                Log.w("AmiBackup", "scheduled backup failed", e);
            } finally {
                Backups.schedule(app);
                pending.finish();
            }
        }).start();
    }
}
