package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Current weather for the greeting line ("28° · Light rain"), for the place in config.xml weather_city /
 * weather_latitude / weather_longitude. Read from Open-Meteo (no key) on a background thread, at most once
 * every 30 minutes and only when Home asks while it is on screen; the last answer is kept for 3 hours, so a
 * restart or an offline TV still shows it. Nothing runs while Home is in the background.
 */
final class Weather {
    private static final String TAG = "TvHome.Weather";
    private static final long REFRESH_MS = 30 * 60_000L;
    private static final long MAX_AGE_MS = 3 * 3600_000L;
    private static final long RETRY_MS = 2 * 60_000L;

    private final Context ctx;
    private final String place;
    private final String url;
    private final SharedPreferences prefs;
    private volatile boolean busy;

    Weather(Context ctx, double latitude, double longitude) {
        this.ctx = ctx.getApplicationContext();
        place = String.format(Locale.US, "%.4f,%.4f", latitude, longitude);
        url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code,is_day", latitude, longitude);
        prefs = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    /** "28° · Light rain, take an umbrella", or "" when nothing recent is known for this place. */
    String summary() {
        // Kept per place, so switching places in Launcher settings and back shows the last answer at once.
        long at = prefs.getLong("at@" + place, 0);
        if (System.currentTimeMillis() - at > MAX_AGE_MS) return "";
        int code = prefs.getInt("code@" + place, -1);
        String text = Math.round(prefs.getFloat("temp@" + place, 0)) + "°";
        int res = describe(code, prefs.getBoolean("day@" + place, true));
        if (res != 0) text += " · " + ctx.getString(res);
        if (wet(code)) text += ctx.getString(R.string.weather_umbrella);
        return text;
    }

    /**
     * Fetches in the background when the last try for this place is over 30 minutes old (a new place is
     * fetched at once), then runs done on handler's thread.
     */
    void refresh(Handler handler, Runnable done) {
        long now = System.currentTimeMillis();
        if (busy || place.equals(prefs.getString("triedPlace", ""))
                && now - prefs.getLong("tried", 0) < REFRESH_MS) return;
        busy = true;
        prefs.edit().putLong("tried", now).putString("triedPlace", place).apply();
        new Thread(() -> {
            try {
                JSONObject current = new JSONObject(get(url)).getJSONObject("current");
                prefs.edit()
                        .putLong("at@" + place, System.currentTimeMillis())
                        .putFloat("temp@" + place, (float) current.getDouble("temperature_2m"))
                        .putInt("code@" + place, current.getInt("weather_code"))
                        .putBoolean("day@" + place, current.optInt("is_day", 1) == 1)
                        .apply();
                handler.post(done);
            } catch (Exception e) {
                Log.w(TAG, "weather not updated: " + e);
                // A failed try (Wi-Fi still joining after standby, say) is retried in 2 minutes, not 30.
                prefs.edit().putLong("tried", System.currentTimeMillis() - REFRESH_MS + RETRY_MS).apply();
            } finally {
                busy = false;
            }
        }, "weather").start();
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    /** WMO weather code, as Open-Meteo reports it, to a short description; 0 for an unknown code. */
    private static int describe(int code, boolean day) {
        if (code == 0) return day ? R.string.weather_clear : R.string.weather_clear_night;
        if (code == 1 || code == 2) return R.string.weather_partly_cloudy;
        if (code == 3) return R.string.weather_overcast;
        if (code == 45 || code == 48) return R.string.weather_fog;
        if (code >= 51 && code <= 57) return R.string.weather_drizzle;
        if (code == 61 || code == 66) return R.string.weather_light_rain;
        if (code == 63) return R.string.weather_rain;
        if (code == 65 || code == 67) return R.string.weather_heavy_rain;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return R.string.weather_snow;
        if (code >= 80 && code <= 82) return R.string.weather_showers;
        if (code >= 95) return R.string.weather_storm;
        return 0;
    }

    private static boolean wet(int code) {
        return code >= 51 && code <= 67 || code >= 80 && code <= 82 || code >= 95;
    }
}
