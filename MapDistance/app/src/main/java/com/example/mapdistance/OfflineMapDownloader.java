package com.example.mapdistance;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 按范围预下载高德矢量瓦片，和浏览时用同一套 URL，离线才能命中缓存。 */
public final class OfflineMapDownloader {
    public static final int MAX_TILES = 2800;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static final class Job {
        public double south, west, north, east;
        public int zMin, zMax;
        public boolean satellite;
        public String title = "";
    }

    public interface Listener {
        void onProgress(int done, int total, int neu, int skip, int fail);

        void onDone(int neu, int skip, int fail, boolean cancelled);
    }

    private OfflineMapDownloader() {}

    public static Job around(double latGcj, double lngGcj, double radiusM, int zMin, int zMax, String title) {
        double dLat = radiusM / 111320.0;
        double cos = Math.cos(Math.toRadians(latGcj));
        double dLng = radiusM / (111320.0 * Math.max(0.2, Math.abs(cos)));
        Job j = new Job();
        j.south = latGcj - dLat;
        j.north = latGcj + dLat;
        j.west = lngGcj - dLng;
        j.east = lngGcj + dLng;
        j.zMin = zMin;
        j.zMax = zMax;
        j.title = title;
        return j;
    }

    public static Job bounds(double south, double west, double north, double east, int zoom) {
        Job j = new Job();
        j.south = south;
        j.west = west;
        j.north = north;
        j.east = east;
        j.zMin = Math.max(12, zoom - 1);
        j.zMax = Math.min(18, zoom + 2);
        j.title = "当前视野";
        return j;
    }

    public static List<String> urls(Job job) {
        List<String> out = new ArrayList<>();
        if (job == null) {
            return out;
        }
        for (int z = job.zMin; z <= job.zMax; z++) {
            int[] a = TileCache.latLngToTile(job.south, job.west, z);
            int[] b = TileCache.latLngToTile(job.north, job.east, z);
            int n = 1 << z;
            int x0 = Math.max(0, Math.min(a[0], b[0]) - 1);
            int x1 = Math.min(n - 1, Math.max(a[0], b[0]) + 1);
            int y0 = Math.max(0, Math.min(a[1], b[1]) - 1);
            int y1 = Math.min(n - 1, Math.max(a[1], b[1]) + 1);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    out.add(TileCache.vectorUrl(x, y, z));
                    if (job.satellite) {
                        out.add(TileCache.satUrl(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    public static String estimate(Job job) {
        int n = urls(job).size();
        double mb = n * 18.0 / 1024.0;
        return String.format(java.util.Locale.CHINA, "大约 %d 张，约 %.1f MB（已有的会跳过）", n, mb);
    }

    public static void start(Context context, Job job, AtomicBoolean cancel, Listener listener) {
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            List<String> list = urls(job);
            if (list.size() > MAX_TILES) {
                list = list.subList(0, MAX_TILES);
            }
            int total = list.size();
            int neu = 0;
            int skip = 0;
            int fail = 0;
            int done = 0;
            for (String url : list) {
                if (cancel.get()) {
                    break;
                }
                int r = TileCache.fetch(app, url);
                if (r == 1) {
                    neu++;
                } else if (r == 0) {
                    skip++;
                } else {
                    fail++;
                }
                done++;
                if (done == 1 || done == total || done % 15 == 0) {
                    final int d = done, n = neu, s = skip, f = fail, t = total;
                    MAIN.post(() -> listener.onProgress(d, t, n, s, f));
                }
            }
            TileCache.trimPublic(app);
            final int n = neu, s = skip, f = fail;
            final boolean c = cancel.get();
            MAIN.post(() -> listener.onDone(n, s, f, c));
        });
    }
}
