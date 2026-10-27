package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final List<File> files;
    private final long interval;
    /** wallpaper_by_time: the picture follows the hour (WallpaperRotation.forHour) instead of a timer. */
    private final boolean byTime;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    private final int height;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** Set when a video is the background (config.wallpaperVideo); the image path is then unused. */
    private final File videoFile;
    private TextureView texture;
    private MediaPlayer player;
    private boolean videoReady;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        videoFile = config.wallpaperEnabled ? config.wallpaperVideo : null;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        byTime = config.wallpaperByTime;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (videoFile != null) {
            setUpVideo(context, config.wallpaperDim);
            return;
        }
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // No shade layer at all when the theme doesn't dim: a full-screen view is overdraw on 1 GB TVs.
            if (config.wallpaperDim > 0) {
                View shade = new View(context);
                shade.setBackgroundColor((config.wallpaperDim * 255 / 100) << 24);
                shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                root.addView(shade, 1, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            showFirst();
        }
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        File first = byTime ? WallpaperRotation.forHour(files, hour()) : WallpaperRotation.find(files, previous);
        if (first == null) first = WallpaperRotation.pick(files, previous, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                displayed = first.getName();
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            first = byTime ? WallpaperRotation.forHour(files, hour()) : WallpaperRotation.pick(files, previous, random);
        }
    }

    void start() {
        active = true;
        if (videoFile != null) { if (videoReady && player != null) player.start(); return; }
        update();
    }

    void stop() {
        active = false;
        if (player != null) { try { player.pause(); } catch (IllegalStateException ignored) {} }
    }

    void close() {
        active = false;
        closed = true;
        if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; }
        decoder.shutdownNow();
    }

    void update() {
        if (!active || closed || loading || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        long changed = preferences.getLong("changed", 0);
        File selected;
        if (byTime) {
            // Called every minute while Home is on screen: the switch happens within a minute of 5h, 12h, 17h, 22h.
            selected = WallpaperRotation.forHour(files, hour());
        } else {
            selected = WallpaperRotation.find(files, previous);
            if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
                selected = WallpaperRotation.pick(files, previous, random);
            }
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                image.setImageBitmap(bitmap);
                displayed = next.getName();
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    private static int hour() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
    }

    // ---------- Video background (config.wallpaperVideo) ----------

    /** Plays the picked video on a TextureView under the Home views: looping, muted, cropped to fill. */
    private void setUpVideo(Context context, int dim) {
        texture = new TextureView(context);
        texture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        texture.setOpaque(true);
        root.addView(texture, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (dim > 0) {
            View shade = new View(context);
            shade.setBackgroundColor((dim * 255 / 100) << 24);
            shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(shade, 1, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                openPlayer(new Surface(st));
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { cropToFill(); }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; }
                videoReady = false;
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });
    }

    private void openPlayer(Surface surface) {
        try {
            player = new MediaPlayer();
            player.setSurface(surface);
            player.setDataSource(videoFile.getPath());
            player.setLooping(true);
            player.setVolume(0f, 0f);   // a wallpaper is silent
            player.setOnVideoSizeChangedListener((mp, w, h) -> cropToFill());
            player.setOnPreparedListener(mp -> {
                videoReady = true;
                cropToFill();
                if (active && !closed) mp.start();
            });
            // A bad file must not crash Home; just leave the plain background.
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w("TvHome.Wallpaper", "Video wallpaper error " + what + "/" + extra);
                return true;
            });
            player.prepareAsync();
        } catch (Exception failure) {
            Log.w("TvHome.Wallpaper", "Cannot play video wallpaper: " + failure);
            if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; }
        }
    }

    /** Center-crops the (aspect-stretched) TextureView so the video fills the screen without bars. */
    private void cropToFill() {
        if (player == null || texture == null) return;
        int vw = texture.getWidth(), vh = texture.getHeight();
        int mw = player.getVideoWidth(), mh = player.getVideoHeight();
        if (vw <= 0 || vh <= 0 || mw <= 0 || mh <= 0) return;
        float viewAspect = (float) vw / vh, videoAspect = (float) mw / mh;
        float scaleX = 1f, scaleY = 1f;
        if (videoAspect > viewAspect) scaleX = videoAspect / viewAspect;
        else scaleY = viewAspect / videoAspect;
        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY, vw / 2f, vh / 2f);
        texture.setTransform(matrix);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
