package com.example.mapdistance;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 高德 Web 服务：逆地理编码。 */
public final class AmapClient {
    private static final String TAG = "AmapClient";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface AddrCallback {
        void onAddr(String formatted);
    }

    private AmapClient() {}

    public static void regeo(Context context, double gcjLat, double gcjLng, AddrCallback cb) {
        final String key = Prefs.amapKey(context);
        if (key.isEmpty() || cb == null) {
            return;
        }
        if (!Net.isOnline(context)) {
            MAIN.post(() -> cb.onAddr(""));
            return;
        }
        IO.execute(() -> {
            String addr = fetch(key, gcjLat, gcjLng);
            MAIN.post(() -> cb.onAddr(addr == null ? "" : addr));
        });
    }

    private static String fetch(String key, double lat, double lng) {
        HttpURLConnection conn = null;
        try {
            String url = "https://restapi.amap.com/v3/geocode/regeo?output=json"
                    + "&location=" + lng + "," + lat
                    + "&key=" + key
                    + "&radius=80&extensions=base";
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) {
                return "";
            }
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            r.close();
            JSONObject json = new JSONObject(sb.toString());
            if (!"1".equals(json.optString("status"))) {
                Log.w(TAG, "regeo status=" + json.optString("info"));
                return "";
            }
            JSONObject re = json.optJSONObject("regeocode");
            if (re == null) {
                return "";
            }
            String formatted = re.optString("formatted_address", "");
            if (formatted.isEmpty() || "[]".equals(formatted)) {
                JSONObject ac = re.optJSONObject("addressComponent");
                if (ac != null) {
                    formatted = ac.optString("district", "") + ac.optString("township", "");
                }
            }
            return formatted;
        } catch (Exception e) {
            Log.w(TAG, "regeo failed", e);
            return "";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
