package com.example.mapdistance;

import android.webkit.JavascriptInterface;

final class ChartBridge {
    private final MainActivity activity;
    private final TrackStore store;

    ChartBridge(MainActivity activity, TrackStore store) {
        this.activity = activity;
        this.store = store;
    }

    @JavascriptInterface
    public String getChartPayload() {
        return TrackCharts.payload(activity, store.list());
    }

    @JavascriptInterface
    public void setFullscreen(boolean on) {
        activity.runOnUiThread(() -> activity.setChartFullscreen(on));
    }
}
