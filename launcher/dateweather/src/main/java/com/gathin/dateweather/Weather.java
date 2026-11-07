package com.gathin.dateweather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Fetches Ha Noi weather from Open-Meteo (no API key) and maps WMO codes to Vietnamese. */
final class Weather {

    // Ha Noi
    static final double LAT = 21.0278, LON = 105.8342;
    static final String CITY = "Hà Nội";

    double currentTemp;
    int currentCode;
    String[] dayDate = new String[0];   // yyyy-MM-dd
    int[] dayCode = new int[0];
    double[] dayMax = new double[0];
    double[] dayMin = new double[0];

    static Weather fetch() throws Exception {
        String spec = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + LAT + "&longitude=" + LON
                + "&current=temperature_2m,weather_code"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=Asia%2FHo_Chi_Minh&forecast_days=4";
        HttpURLConnection c = (HttpURLConnection) new URL(spec).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "CleanTV-DateWeather");
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            Weather w = new Weather();
            JSONObject cur = root.getJSONObject("current");
            w.currentTemp = cur.getDouble("temperature_2m");
            w.currentCode = cur.getInt("weather_code");
            JSONObject daily = root.getJSONObject("daily");
            JSONArray t = daily.getJSONArray("time");
            JSONArray code = daily.getJSONArray("weather_code");
            JSONArray mx = daily.getJSONArray("temperature_2m_max");
            JSONArray mn = daily.getJSONArray("temperature_2m_min");
            int n = t.length();
            w.dayDate = new String[n];
            w.dayCode = new int[n];
            w.dayMax = new double[n];
            w.dayMin = new double[n];
            for (int i = 0; i < n; i++) {
                w.dayDate[i] = t.getString(i);
                w.dayCode[i] = code.getInt(i);
                w.dayMax[i] = mx.getDouble(i);
                w.dayMin[i] = mn.getDouble(i);
            }
            return w;
        } finally {
            c.disconnect();
        }
    }

    static String codeText(int c) {
        if (c == 0) return "Trời quang";
        if (c == 1) return "Ít mây";
        if (c == 2) return "Có mây";
        if (c == 3) return "Nhiều mây";
        if (c == 45 || c == 48) return "Sương mù";
        if (c >= 51 && c <= 57) return "Mưa phùn";
        if (c >= 61 && c <= 67) return "Mưa";
        if (c >= 71 && c <= 77) return "Tuyết";
        if (c >= 80 && c <= 82) return "Mưa rào";
        if (c == 85 || c == 86) return "Mưa tuyết";
        if (c >= 95) return "Dông";
        return "Thời tiết";
    }

    static String codeEmoji(int c) {
        if (c == 0) return "☀";
        if (c == 1 || c == 2) return "⛅";
        if (c == 3) return "☁";
        if (c == 45 || c == 48) return "🌫";
        if (c >= 51 && c <= 57) return "🌦";
        if (c >= 61 && c <= 67) return "🌧";
        if (c >= 71 && c <= 77) return "❄";
        if (c >= 80 && c <= 82) return "🌦";
        if (c == 85 || c == 86) return "🌨";
        if (c >= 95) return "⛈";
        return "⛅";
    }
}
