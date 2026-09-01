package com.example.mapdistance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 闹钟过不了重启，开机和覆盖安装后把定时备份重新挂上。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Backups.schedule(context);
        }
    }
}
