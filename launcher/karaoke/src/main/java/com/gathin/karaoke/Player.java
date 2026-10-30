package com.gathin.karaoke;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Where a song plays: the built-in player (PlayerActivity) or a YouTube app on the TV. */
final class Player {
    private Player() { }

    /** Same order as the launcher's voice search: TizenTube when installed, then YouTube for TV, then YouTube. */
    private static final String[] YOUTUBE = {"io.gh.reisxd.tizentube.cobalt", "com.google.android.youtube.tv",
            "com.google.android.youtube"};
    private static final String PREFS = "karaoke", KEY_EXTERNAL = "external_player";

    static boolean external(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_EXTERNAL, false);
    }

    static void setExternal(Context c, boolean external) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_EXTERNAL, external).apply();
    }

    /**
     * Sings song now. From the phone page the context is the application, not an activity: the built-in player
     * already on screen takes the song itself, otherwise a new task is needed to start it.
     */
    static void play(Context context, Song song) {
        Library.get(context).sung(song);
        if (external(context)) {
            openInYouTube(context, song);
            return;
        }
        PlayerActivity current = PlayerActivity.current;
        if (current != null) {
            current.playNow(song);
            return;
        }
        context.startActivity(newTask(context, PlayerActivity.intent(context, song)));
    }

    /** Opens the video in a YouTube app; false when none takes it. */
    static boolean openInYouTube(Context context, Song song) {
        Uri uri = Uri.parse("https://www.youtube.com/watch?v=" + song.id);
        for (String pkg : YOUTUBE) {
            try {
                context.startActivity(newTask(context, new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)));
                return true;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        try {
            context.startActivity(newTask(context, new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + song.id))));
            return true;
        } catch (ActivityNotFoundException e) {
            android.widget.Toast.makeText(context, R.string.no_youtube_app, android.widget.Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static Intent newTask(Context context, Intent intent) {
        return context instanceof Activity ? intent : intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
