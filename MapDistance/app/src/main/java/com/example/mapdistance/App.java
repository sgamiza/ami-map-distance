package com.example.mapdistance;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        BatterySnap.markAppOpen(this);
        TrackEngine.get(this);
        StepMidnight.schedule(this);
        Backups.schedule(this);
    }
}
