package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.TimeZone;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView wallpaperChoice;
    private TextView clockZoneChoice;
    private TextView weatherChoice;
    /** Places offered for the weather: name, latitude, longitude (Open-Meteo needs only coordinates). */
    private static final String[][] WEATHER_PLACES = {
            {"Phù Đổng", "21.0571", "105.9632"}, {"Hà Nội", "21.0285", "105.8542"},
            {"TP. Hồ Chí Minh", "10.7769", "106.7009"}, {"Đà Nẵng", "16.0544", "108.2022"},
            {"Hải Phòng", "20.8449", "106.6881"}, {"Cần Thơ", "10.0452", "105.7469"},
            {"Huế", "16.4637", "107.5909"}, {"Nha Trang", "12.2388", "109.1967"},
            {"Đà Lạt", "11.9404", "108.4583"}, {"Vinh", "18.6796", "105.6813"},
            {"Quy Nhơn", "13.7820", "109.2197"}, {"Hạ Long", "20.9599", "107.0425"},
            {"Buôn Ma Thuột", "12.6667", "108.0500"}, {"Vũng Tàu", "10.3460", "107.0843"},
            {"Thái Nguyên", "21.5942", "105.8482"}, {"Nam Định", "20.4388", "106.1621"}};
    private static final int PICK_WALLPAPER = 1;
    private int columns;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            LinearLayout row = (LinearLayout) columnChoices[0].getParent();
            row.removeAllViews();
            for (int i = columnChoices.length - 1; i >= 0; i--) row.addView(columnChoices[i]);
        }
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            Config.clearUi(choices.edit()).remove("columns").remove("animations").remove("advanced_effects").commit();
            Config current = Config.load(this);
            columns = current.columns;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        });
        setupAppearance();
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> chooseWallpaper());
        weatherChoice = findViewById(R.id.weatherChoice);
        weatherChoice.setOnClickListener(v -> chooseWeather());
        clockZoneChoice = findViewById(R.id.clockZoneChoice);
        clockZoneChoice.setOnClickListener(v -> chooseClockZone());        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        followThemeChoice.setSelected(!hasOnTvChoices());
        String wall = choices.getString(Config.TV_WALLPAPER_KEY, "");
        wallpaperChoice.setText(getString(R.string.wallpaper_state, getString(
                "picked".equals(wall) ? R.string.wallpaper_picked
                        : "none".equals(wall) ? R.string.wallpaper_none : R.string.wallpaper_theme)));
        refreshAppearance();
        String tvWeather = choices.getString(Config.TV_WEATHER_KEY, "");
        String city = Config.load(this).weatherCity;
        String shown = city.isEmpty() ? getString(R.string.weather_off) : city;
        weatherChoice.setText(getString(R.string.weather_state,
                tvWeather.isEmpty() ? getString(R.string.weather_theme, shown) : shown));
        String zone = choices.getString(Config.CLOCK_ZONE_KEY, "");
        clockZoneChoice.setText(getString(R.string.clock_zone_state,
                zone.isEmpty() ? getString(R.string.clock_zone_system, systemZone()) : zone));
    }

    /** The TV's own zone, which the default zone hides while the launcher shows another one. */
    private String systemZone() {
        TimeZone.setDefault(null);
        String id = TimeZone.getDefault().getID();
        Config.applyClockZone(this);
        return id;
    }

    private void chooseWallpaper() {
        String[] items = {getString(R.string.wallpaper_pick), getString(R.string.wallpaper_remove),
                getString(R.string.wallpaper_follow_theme)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.wallpaper_state, "").replaceAll("[:：]\\s*$", ""))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                                .addCategory(Intent.CATEGORY_OPENABLE);
                        try {
                            startActivityForResult(pick, PICK_WALLPAPER);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        SharedPreferences.Editor e = choices.edit();
                        if (which == 1) e.putString(Config.TV_WALLPAPER_KEY, "none");
                        else e.remove(Config.TV_WALLPAPER_KEY);
                        e.apply();
                        setResult(RESULT_OK);
                        refresh();
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_WALLPAPER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        final File dir = Config.dir(this);
        if (dir == null) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.wallpaper_saving, Toast.LENGTH_SHORT).show();
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        final int w = Math.max(m.widthPixels, m.heightPixels), h = Math.min(m.widthPixels, m.heightPixels);
        new Thread(() -> {
            boolean ok = saveWallpaper(uri, new File(dir, Config.TV_WALLPAPER_NAME), w, h);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (ok) {
                    choices.edit().putString(Config.TV_WALLPAPER_KEY, "picked").apply();
                    setResult(RESULT_OK);
                    refresh();
                } else {
                    Toast.makeText(this, R.string.wallpaper_failed, Toast.LENGTH_LONG).show();
                }
            });
        }, "wallpaper").start();
    }

    /** Decodes the picture small enough for a 1 GB TV, crops it to fill the screen and saves it as JPEG. */
    private boolean saveWallpaper(Uri uri, File dest, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            Bitmap src;
            try (InputStream in = getContentResolver().openInputStream(uri)) { src = BitmapFactory.decodeStream(in, null, d); }
            if (src == null) return false;
            float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(out);
            float dw = src.getWidth() * scale, dh = src.getHeight() * scale;
            c.drawBitmap(src, null, new RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            src.recycle();
            File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
            try (FileOutputStream f = new FileOutputStream(tmp)) { out.compress(Bitmap.CompressFormat.JPEG, 88, f); }
            out.recycle();
            return tmp.renameTo(dest) || (dest.delete() && tmp.renameTo(dest));
        } catch (Exception | OutOfMemoryError e) {
            return false;
        }
    }

    // ---------- Appearance: the look the PC tool sets, chosen with the remote ----------
    private static final int[] COLORS = {0xFF000000, 0xFF0B1A2E, 0xFF1A1030, 0xFF0F2018, 0xFF22160E, 0xFF1C1C1E};
    private static final int[] COLOR_NAMES = {R.string.color_black, R.string.color_navy, R.string.color_purple,
            R.string.color_green, R.string.color_brown, R.string.color_grey};
    private static final int[] SKY = {0, 30, 60, 100};
    private static final int[] DIM = {0, 20, 40, 60};
    private static final int[] ROWS = {0, 1, 2, 3};

    private void setupAppearance() {
        findViewById(R.id.uiGreeting).setOnClickListener(v -> {
            final android.widget.EditText input = new android.widget.EditText(this);
            input.setSingleLine(true);
            input.setText(Config.load(this).greetingName);
            input.setSelection(input.getText().length());
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.ui_greeting, "").replaceAll("[:：]\\s*$", ""))
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        choices.edit().putString(Config.UI_GREETING, input.getText().toString().trim()).apply();
                        changed();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
        findViewById(R.id.uiBackground).setOnClickListener(v -> {
            String[] labels = new String[COLORS.length];
            int current = Config.load(this).backgroundColor, checked = -1;
            for (int i = 0; i < COLORS.length; i++) {
                labels[i] = getString(COLOR_NAMES[i]);
                if (COLORS[i] == current) checked = i;
            }
            pick(R.string.ui_background, labels, checked, which ->
                    choices.edit().putInt(Config.UI_BACKGROUND, COLORS[which]).apply());
        });
        findViewById(R.id.uiSky).setOnClickListener(v -> pickInt(R.string.ui_sky, SKY,
                Config.load(this).skyIntensity, Config.UI_SKY));
        findViewById(R.id.uiDim).setOnClickListener(v -> pickInt(R.string.ui_dim, DIM,
                Config.load(this).wallpaperDim, Config.UI_WALLPAPER_DIM));
        findViewById(R.id.uiRows).setOnClickListener(v -> pickInt(R.string.ui_rows, ROWS,
                Config.load(this).rowsBelowDock, Config.UI_ROWS_BELOW));
        findViewById(R.id.uiDockTitle).setOnClickListener(v -> {
            choices.edit().putBoolean(Config.UI_DOCK_TITLE, !Config.load(this).dockTitle).apply();
            changed();
        });
        findViewById(R.id.uiStats).setOnClickListener(v -> {
            choices.edit().putBoolean(Config.UI_STATS, !Config.load(this).showStats).apply();
            changed();
        });
        findViewById(R.id.uiReduced).setOnClickListener(v -> {
            choices.edit().putBoolean(Config.UI_REDUCED_MOTION, !Config.load(this).reducedMotion).apply();
            changed();
        });
    }

    private void refreshAppearance() {
        Config c = Config.load(this);
        setRow(R.id.uiGreeting, R.string.ui_greeting, c.greetingName.isEmpty() ? getString(R.string.ui_greeting_none) : c.greetingName);
        String color = String.format(Locale.ROOT, "#%06X", c.backgroundColor & 0xFFFFFF);
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == c.backgroundColor) color = getString(COLOR_NAMES[i]);
        if (color.startsWith("#")) color = getString(R.string.color_custom, color);
        setRow(R.id.uiBackground, R.string.ui_background, color);
        setRow(R.id.uiSky, R.string.ui_sky, c.skyIntensity == 0 ? getString(R.string.off) : c.skyIntensity + "%");
        setRow(R.id.uiDim, R.string.ui_dim, c.wallpaperDim == 0 ? getString(R.string.off) : c.wallpaperDim + "%");
        setRow(R.id.uiRows, R.string.ui_rows, String.valueOf(c.rowsBelowDock));
        setRow(R.id.uiDockTitle, R.string.ui_dock_title, getString(c.dockTitle ? R.string.on : R.string.off));
        setRow(R.id.uiStats, R.string.ui_stats, getString(c.showStats ? R.string.on : R.string.off));
        setRow(R.id.uiReduced, R.string.ui_reduced, getString(c.reducedMotion ? R.string.on : R.string.off));
    }

    private void setRow(int id, int format, String value) {
        ((TextView) findViewById(id)).setText(getString(format, value));
    }

    private void pickInt(int title, int[] values, int current, String key) {
        String[] labels = new String[values.length];
        int checked = -1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = key.equals(Config.UI_ROWS_BELOW) ? String.valueOf(values[i])
                    : values[i] == 0 ? getString(R.string.off) : values[i] + "%";
            if (values[i] == current) checked = i;
        }
        pick(title, labels, checked, which -> choices.edit().putInt(key, values[which]).apply());
    }

    private void pick(int title, String[] labels, int checked, java.util.function.IntConsumer save) {
        new AlertDialog.Builder(this)
                .setTitle(getString(title, "").replaceAll("[:：]\\s*$", ""))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    save.accept(which);
                    changed();
                    dialog.dismiss();
                })
                .show();
    }

    /** A choice was saved: Home rebuilds with it when this screen closes. */
    private void changed() {
        setResult(RESULT_OK);
        refresh();
    }

    private void chooseWeather() {
        String current = choices.getString(Config.TV_WEATHER_KEY, "");
        String[] labels = new String[WEATHER_PLACES.length + 2];
        int checked = -1;
        for (int i = 0; i < WEATHER_PLACES.length; i++) {
            labels[i] = WEATHER_PLACES[i][0];
            if (current.startsWith(WEATHER_PLACES[i][0] + "|")) checked = i;
        }
        labels[WEATHER_PLACES.length] = getString(R.string.weather_choose_off);
        labels[WEATHER_PLACES.length + 1] = getString(R.string.weather_choose_theme);
        if ("off".equals(current)) checked = WEATHER_PLACES.length;
        else if (current.isEmpty()) checked = WEATHER_PLACES.length + 1;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.weather_state, "").replaceAll("[:：]\\s*$", ""))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    SharedPreferences.Editor e = choices.edit();
                    if (which < WEATHER_PLACES.length) {
                        String[] p = WEATHER_PLACES[which];
                        e.putString(Config.TV_WEATHER_KEY, p[0] + "|" + p[1] + "|" + p[2]);
                    } else if (which == WEATHER_PLACES.length) {
                        e.putString(Config.TV_WEATHER_KEY, "off");
                    } else {
                        e.remove(Config.TV_WEATHER_KEY);
                    }
                    e.apply();
                    setResult(RESULT_OK);
                    refresh();
                    dialog.dismiss();
                })
                .show();
    }

    private void chooseClockZone() {
        final String[] zones = {"", "Asia/Ho_Chi_Minh", "Asia/Bangkok", "Asia/Jakarta", "Asia/Singapore",
                "Asia/Shanghai", "Asia/Taipei", "Asia/Manila", "Asia/Seoul", "Asia/Tokyo", "Asia/Kolkata", "Asia/Dubai",
                "Australia/Sydney", "Pacific/Auckland", "Europe/London", "Europe/Paris", "Europe/Berlin", "Europe/Moscow",
                "America/New_York", "America/Chicago", "America/Los_Angeles", "America/Sao_Paulo"};
        String current = choices.getString(Config.CLOCK_ZONE_KEY, "");
        String[] labels = new String[zones.length];
        int checked = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < zones.length; i++) {
            String id = zones[i].isEmpty() ? systemZone() : zones[i];
            int mins = TimeZone.getTimeZone(id).getOffset(now) / 60000;
            String utc = String.format(Locale.ROOT, "UTC%s%02d:%02d", mins < 0 ? "-" : "+", Math.abs(mins) / 60, Math.abs(mins) % 60);
            labels[i] = (zones[i].isEmpty() ? getString(R.string.clock_zone_system, id) : id) + "   " + utc;
            if (zones[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clock_zone_state, "").replaceAll("[:：]\\s*$", ""))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (zones[which].isEmpty()) choices.edit().remove(Config.CLOCK_ZONE_KEY).apply();
                    else choices.edit().putString(Config.CLOCK_ZONE_KEY, zones[which]).apply();
                    Config.applyClockZone(this);
                    setResult(RESULT_OK);
                    refresh();
                    dialog.dismiss();
                })
                .show();
    }

    private boolean hasOnTvChoices() {
        for (String k : Config.UI_KEYS) if (choices.contains(k)) return true;
        return choices.contains("columns") || choices.contains("animations")
                || choices.contains("advanced_effects");
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
