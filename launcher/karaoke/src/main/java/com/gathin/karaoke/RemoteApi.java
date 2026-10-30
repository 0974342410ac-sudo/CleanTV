package com.gathin.karaoke;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** The phone page's plain parts, kept apart from sockets and Android so they can be tested on the PC. */
final class RemoteApi {
    private RemoteApi() { }

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    /**
     * The 4-digit PIN check. Wrong PINs MAX_TRIES times in a row lock it for LOCK_MS, so all 10 000 PINs cannot
     * be tried from the home network in a few minutes.
     */
    static final class PinGate {
        static final int MAX_TRIES = 5;
        static final long LOCK_MS = 60_000;
        private int wrong;
        private long lockedUntil;

        /** "ok", "wrong" or "locked:<seconds>". */
        synchronized String check(String given, String pin, long now) {
            if (now < lockedUntil) return "locked:" + ((lockedUntil - now + 999) / 1000);
            if (given == null || !given.equals(pin)) {
                if (++wrong >= MAX_TRIES) {
                    wrong = 0;
                    lockedUntil = now + LOCK_MS;
                }
                return "wrong";
            }
            wrong = 0;
            return "ok";
        }
    }

    /** a=1&b=x%20y -> {a: 1, b: "x y"}; broken pairs are skipped. */
    static Map<String, String> form(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null) return map;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                map.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                        URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (IllegalArgumentException | UnsupportedEncodingException ignored) {
            }
        }
        return map;
    }

    /** The song the phone sent (fields of songJson), or null when its id is not a video id. */
    static Song song(Map<String, String> form) {
        String id = form.get("id");
        if (id == null || !VIDEO_ID.matcher(id).matches()) return null;
        return new Song(id, cut(form.get("title"), 200), cut(form.get("channel"), 100), cut(form.get("duration"), 12),
                cut(form.get("views"), 40));
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    static JSONObject songJson(Song s) throws JSONException {
        return s.toJson().put("thumb", s.thumbnail());
    }

    static JSONArray songsJson(List<Song> songs) throws JSONException {
        JSONArray a = new JSONArray();
        for (Song s : songs) a.put(songJson(s));
        return a;
    }

    /** Search answer: the songs, and what the phone sends back for the next page (absent: no next page). */
    static JSONObject page(SearchParser.Page p) throws JSONException {
        JSONObject o = new JSONObject().put("ok", true).put("songs", songsJson(p.songs));
        if (p.continuation != null) {
            o.put("more", p.continuation);
            if (p.clientVersion != null) o.put("version", p.clientVersion);
        }
        return o;
    }

    static int index(Map<String, String> form) {
        try {
            return Integer.parseInt(form.getOrDefault("index", "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
