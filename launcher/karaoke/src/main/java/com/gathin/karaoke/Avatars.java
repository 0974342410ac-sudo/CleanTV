package com.gathin.karaoke;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singer photos for the "Ca sĩ" tab. Finding one takes a YouTube channel search, so each answer is kept for good
 * (an empty one too, so a singer without a channel is not searched again) and searches run one at a time.
 */
final class Avatars {
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<String> PENDING = new HashSet<>();

    /** "avatars2": 0.3 kept the first channel's picture, often a wrong one; those are searched again. */
    private static final String PREFS = "avatars2";

    private Avatars() { }

    /** The photo address already found for singer ("" none found, null not searched yet). */
    static String cached(Context context, String singer) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(singer, null);
    }

    /** Shows the singer's photo in view once known; the view keeps its letter tile until then. */
    static void load(Context context, ImageView view, String singer) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        view.setTag(R.id.singer_tag, singer);
        // A recycled tile: drop the previous singer's photo and any photo still on its way to it
        view.setTag(null);
        view.setImageDrawable(null);
        String url = prefs.getString(singer, null);
        if (url != null) {
            if (!url.isEmpty()) Thumbnails.load(view, url);
            return;
        }
        if (!PENDING.add(singer)) return;
        ONE.execute(() -> {
            String found;
            try {
                found = YouTubeSearch.channelAvatar(singer);
            } catch (Exception e) {
                // No answer now (network): try again next time the tile shows
                MAIN.post(() -> PENDING.remove(singer));
                return;
            }
            String u = found == null ? "" : found;
            prefs.edit().putString(singer, u).apply();
            MAIN.post(() -> {
                PENDING.remove(singer);
                if (!u.isEmpty() && singer.equals(view.getTag(R.id.singer_tag))) Thumbnails.load(view, u);
            });
        });
    }
}
