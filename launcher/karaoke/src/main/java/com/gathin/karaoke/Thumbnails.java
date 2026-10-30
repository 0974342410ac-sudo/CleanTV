package com.gathin.karaoke;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads thumbnails into image views, a few at a time, with a small memory cache. */
final class Thumbnails {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** ~60 thumbnails of 320x180 (about 230 KB each). */
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(14 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private Thumbnails() { }

    static void load(ImageView view, String url) {
        view.setTag(url);
        Bitmap cached = CACHE.get(url);
        view.setImageBitmap(cached);
        if (cached != null) return;
        POOL.execute(() -> {
            Bitmap b = fetch(url);
            if (b == null) return;
            CACHE.put(url, b);
            MAIN.post(() -> {
                // The view may have been reused for another song meanwhile
                if (url.equals(view.getTag())) view.setImageBitmap(b);
            });
        });
    }

    private static Bitmap fetch(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            try (InputStream in = c.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
