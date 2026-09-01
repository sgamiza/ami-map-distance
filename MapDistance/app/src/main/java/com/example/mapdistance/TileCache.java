package com.example.mapdistance;

import android.content.Context;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 把高德栅格瓦片落到本机。走过的区域离线还能看；没缓存时回一张空图，轨迹线照样画。
 */
public final class TileCache {
    public static final int MAX_FILES = 5000;

    public static File dir(Context context) {
        return new File(context.getCacheDir(), "tiles");
    }

    public static String vectorUrl(int x, int y, int z) {
        char s = "1234".charAt(Math.abs(x + y) % 4);
        return "https://webrd0" + s + ".is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x="
                + x + "&y=" + y + "&z=" + z;
    }

    public static String satUrl(int x, int y, int z) {
        char s = "1234".charAt(Math.abs(x + y) % 4);
        return "https://webst0" + s + ".is.autonavi.com/appmaptile?style=6&x=" + x + "&y=" + y + "&z=" + z;
    }

    public static int[] latLngToTile(double lat, double lng, int z) {
        int n = 1 << z;
        double latRad = Math.toRadians(Math.max(-85.0511, Math.min(85.0511, lat)));
        int x = (int) Math.floor((lng + 180.0) / 360.0 * n);
        int y = (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n);
        if (x < 0) {
            x = 0;
        }
        if (x > n - 1) {
            x = n - 1;
        }
        if (y < 0) {
            y = 0;
        }
        if (y > n - 1) {
            y = n - 1;
        }
        return new int[]{x, y};
    }

    public static File fileFor(Context context, String url) {
        return new File(dir(context), Integer.toHexString(url.hashCode()) + ".png");
    }

    public static boolean has(Context context, String url) {
        File f = fileFor(context, url);
        return f.isFile() && f.length() > 32;
    }

    /** @return 1 新下成功，0 已有，-1 失败 */
    public static int fetch(Context context, String url) {
        File folder = dir(context);
        if (!folder.exists() && !folder.mkdirs()) {
            return -1;
        }
        File dest = fileFor(context, url);
        if (dest.isFile() && dest.length() > 32) {
            dest.setLastModified(System.currentTimeMillis());
            return 0;
        }
        return download(url, dest) ? 1 : -1;
    }

    public static long sizeBytes(Context context) {
        File[] files = dir(context).listFiles();
        if (files == null) {
            return 0;
        }
        long n = 0;
        for (File f : files) {
            n += f.length();
        }
        return n;
    }

    public static int fileCount(Context context) {
        File[] files = dir(context).listFiles();
        return files == null ? 0 : files.length;
    }

    public static void clear(Context context) {
        File[] files = dir(context).listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    public static String sizeLabel(Context context) {
        long b = sizeBytes(context);
        int n = fileCount(context);
        if (n == 0) {
            return "还没缓存地图";
        }
        if (b < 1024 * 1024) {
            return String.format(java.util.Locale.CHINA, "已缓存 %d 张 · %.0f KB", n, b / 1024.0);
        }
        return String.format(java.util.Locale.CHINA, "已缓存 %d 张 · %.1f MB", n, b / 1024.0 / 1024.0);
    }
    private static final byte[] EMPTY_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06,
            0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0x00, 0x00, 0x00, 0x0A,
            0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
            0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45,
            0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    private TileCache() {}

    public static boolean isTile(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase();
        return u.contains("appmaptile")
                || u.contains("webrd0")
                || u.contains("webst0")
                || u.contains("wprd0");
    }

    public static WebResourceResponse serve(Context context, String url) {
        File folder = dir(context);
        if (!folder.exists() && !folder.mkdirs()) {
            return empty();
        }
        File file = fileFor(context, url);
        if (file.isFile() && file.length() > 32) {
            try {
                return png(new FileInputStream(file));
            } catch (Exception ignored) {
            }
        }
        if (Net.isOnline(context)) {
            if (download(url, file)) {
                trim(folder);
                try {
                    return png(new FileInputStream(file));
                } catch (Exception ignored) {
                }
            }
        }
        return empty();
    }

    public static void trimPublic(Context context) {
        File folder = dir(context);
        if (folder.isDirectory()) {
            trim(folder);
        }
    }

    private static boolean download(String url, File dest) {
        HttpURLConnection conn = null;
        File tmp = new File(dest.getAbsolutePath() + ".tmp");
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 AmiCeju");
            if (conn.getResponseCode() != 200) {
                return false;
            }
            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
            }
            out.close();
            in.close();
            if (total < 32) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return false;
            }
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
            return tmp.renameTo(dest);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void trim(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_FILES) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int drop = files.length - MAX_FILES + 200;
        for (int i = 0; i < drop && i < files.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    private static WebResourceResponse empty() {
        return png(new ByteArrayInputStream(EMPTY_PNG));
    }

    private static WebResourceResponse png(InputStream in) {
        return new WebResourceResponse("image/png", "UTF-8", in);
    }
}
