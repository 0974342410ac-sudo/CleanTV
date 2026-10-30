package com.gathin.karaoke;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** "Yêu thích" (songs marked with the heart) and "Đã hát" (songs sung, newest first), kept on the TV. */
final class Library {
    private static final String PREFS = "library", FAVORITES = "favorites", HISTORY = "history";
    private static final int HISTORY_MAX = 100;
    private static Library instance;

    private final SharedPreferences prefs;
    private final List<Song> favorites, history;

    static synchronized Library get(Context context) {
        if (instance == null) instance = new Library(context.getApplicationContext());
        return instance;
    }

    private Library(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        favorites = load(FAVORITES);
        history = load(HISTORY);
    }

    List<Song> favorites() {
        return new ArrayList<>(favorites);
    }

    List<Song> history() {
        return new ArrayList<>(history);
    }

    boolean isFavorite(String id) {
        return indexOf(favorites, id) >= 0;
    }

    /** Adds or removes the heart; true when the song is now a favorite. */
    boolean toggleFavorite(Song song) {
        int at = indexOf(favorites, song.id);
        if (at >= 0) favorites.remove(at);
        else favorites.add(0, song);
        save(FAVORITES, favorites);
        return at < 0;
    }

    /** A song started: it goes to the top of "Đã hát". */
    void sung(Song song) {
        int at = indexOf(history, song.id);
        if (at >= 0) history.remove(at);
        history.add(0, song);
        while (history.size() > HISTORY_MAX) history.remove(history.size() - 1);
        save(HISTORY, history);
    }

    void clearHistory() {
        history.clear();
        save(HISTORY, history);
    }

    private static int indexOf(List<Song> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(id)) return i;
        return -1;
    }

    private List<Song> load(String key) {
        List<Song> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) list.add(Song.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return list;
    }

    private void save(String key, List<Song> list) {
        JSONArray a = new JSONArray();
        try {
            for (Song s : list) a.put(s.toJson());
        } catch (JSONException ignored) {
        }
        prefs.edit().putString(key, a.toString()).apply();
    }
}
