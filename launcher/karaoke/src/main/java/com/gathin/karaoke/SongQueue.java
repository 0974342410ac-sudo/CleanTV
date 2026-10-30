package com.gathin.karaoke;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * The songs waiting to be sung, in order. Kept in preferences so a restart (or the TV sleeping) keeps it. The phone
 * page (PhoneRemote) changes it from its own threads, so every method is synchronized, and a change tells the
 * screen on the main thread (setListener).
 */
final class SongQueue {
    private static final String PREFS = "karaoke", KEY = "queue";
    private static SongQueue instance;

    private final SharedPreferences prefs;
    private final List<Song> songs = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable listener;

    static synchronized SongQueue get(Context context) {
        if (instance == null) instance = new SongQueue(context.getApplicationContext());
        return instance;
    }

    private SongQueue(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) songs.add(Song.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
    }

    /** Called on the main thread after each change (null: nobody). */
    synchronized void setListener(Runnable listener) {
        this.listener = listener;
    }

    synchronized List<Song> songs() {
        return new ArrayList<>(songs);
    }

    synchronized int size() {
        return songs.size();
    }

    synchronized boolean contains(String id) {
        for (Song s : songs) if (s.id.equals(id)) return true;
        return false;
    }

    /** Adds at the end; false when the song is already waiting. */
    synchronized boolean add(Song song) {
        if (contains(song.id)) return false;
        songs.add(song);
        save();
        return true;
    }

    synchronized void remove(int index) {
        if (index < 0 || index >= songs.size()) return;
        songs.remove(index);
        save();
    }

    /** Takes the next song off the queue, or null. */
    synchronized Song next() {
        if (songs.isEmpty()) return null;
        Song s = songs.remove(0);
        save();
        return s;
    }

    /** "Ưu tiên": the song is sung next, whether it was waiting already or not. */
    synchronized void addFirst(Song song) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id.equals(song.id)) {
                songs.remove(i);
                break;
            }
        }
        songs.add(0, song);
        save();
    }

    /** The song sung next, or null; it stays in the queue. */
    synchronized Song peek() {
        return songs.isEmpty() ? null : songs.get(0);
    }

    /** Moves the song at index to the front (the next one sung). */
    synchronized void moveToFront(int index) {
        if (index <= 0 || index >= songs.size()) return;
        songs.add(0, songs.remove(index));
        save();
    }

    synchronized void clear() {
        songs.clear();
        save();
    }

    private void save() {
        JSONArray a = new JSONArray();
        try {
            for (Song s : songs) a.put(s.toJson());
        } catch (JSONException ignored) {
        }
        prefs.edit().putString(KEY, a.toString()).apply();
        Runnable l = listener;
        if (l != null) main.post(l);
    }
}
