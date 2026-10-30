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
    /**
     * Places offered for the weather: name, latitude, longitude (Open-Meteo needs only coordinates). Phù Đổng
     * (the owner's home) first, then the 63 provinces of Vietnam by their capital, in alphabetical order.
     */
    private static final String[][] WEATHER_PLACES = {
            {"Phù Đổng", "21.0571", "105.9632"},
            {"An Giang", "10.3861", "105.4348"}, {"Bà Rịa - Vũng Tàu", "10.3460", "107.0843"},
            {"Bắc Giang", "21.2730", "106.1946"}, {"Bắc Kạn", "22.1470", "105.8348"},
            {"Bạc Liêu", "9.2850", "105.7244"}, {"Bắc Ninh", "21.1861", "106.0763"},
            {"Bến Tre", "10.2415", "106.3759"}, {"Bình Dương", "10.9804", "106.6519"},
            {"Bình Định", "13.7820", "109.2197"}, {"Bình Phước", "11.5349", "106.8933"},
            {"Bình Thuận", "10.9289", "108.1021"}, {"Cà Mau", "9.1769", "105.1524"},
            {"Cao Bằng", "22.6657", "106.2570"}, {"Cần Thơ", "10.0452", "105.7469"},
            {"Đà Nẵng", "16.0544", "108.2022"}, {"Đắk Lắk", "12.6667", "108.0500"},
            {"Đắk Nông", "12.0045", "107.6877"}, {"Điện Biên", "21.3860", "103.0230"},
            {"Đồng Nai", "10.9574", "106.8426"}, {"Đồng Tháp", "10.4593", "105.6328"},
            {"Gia Lai", "13.9833", "108.0000"}, {"Hà Giang", "22.8233", "104.9836"},
            {"Hà Nam", "20.5411", "105.9139"}, {"Hà Nội", "21.0285", "105.8542"},
            {"Hà Tĩnh", "18.3428", "105.9057"}, {"Hải Dương", "20.9373", "106.3146"},
            {"Hải Phòng", "20.8449", "106.6881"}, {"Hậu Giang", "9.7840", "105.4700"},
            {"Hòa Bình", "20.8133", "105.3383"}, {"Hưng Yên", "20.6464", "106.0511"},
            {"Khánh Hòa", "12.2388", "109.1967"}, {"Kiên Giang", "10.0121", "105.0809"},
            {"Kon Tum", "14.3545", "107.9980"}, {"Lai Châu", "22.3964", "103.4590"},
            {"Lâm Đồng", "11.9404", "108.4583"}, {"Lạng Sơn", "21.8537", "106.7610"},
            {"Lào Cai", "22.4809", "103.9755"}, {"Long An", "10.5356", "106.4133"},
            {"Nam Định", "20.4388", "106.1621"}, {"Nghệ An", "18.6796", "105.6813"},
            {"Ninh Bình", "20.2506", "105.9745"}, {"Ninh Thuận", "11.5667", "108.9833"},
            {"Phú Thọ", "21.3227", "105.4017"}, {"Phú Yên", "13.0882", "109.0929"},
            {"Quảng Bình", "17.4689", "106.6223"}, {"Quảng Nam", "15.5736", "108.4740"},
            {"Quảng Ngãi", "15.1213", "108.8044"}, {"Quảng Ninh", "20.9599", "107.0425"},
            {"Quảng Trị", "16.8163", "107.1003"}, {"Sóc Trăng", "9.6037", "105.9739"},
            {"Sơn La", "21.3270", "103.9141"}, {"Tây Ninh", "11.3100", "106.0989"},
            {"Thái Bình", "20.4463", "106.3366"}, {"Thái Nguyên", "21.5942", "105.8482"},
            {"Thanh Hóa", "19.8069", "105.7852"}, {"Thừa Thiên Huế", "16.4637", "107.5909"},
            {"Tiền Giang", "10.3600", "106.3600"}, {"TP. Hồ Chí Minh", "10.7769", "106.7009"},
            {"Trà Vinh", "9.9347", "106.3453"}, {"Tuyên Quang", "21.8233", "105.2180"},
            {"Vĩnh Long", "10.2537", "105.9722"}, {"Vĩnh Phúc", "21.3089", "105.6049"},
            {"Yên Bái", "21.7000", "104.8667"}};
    private static final int PICK_WALLPAPER = 1;
    private static final int PICK_VIDEO = 2;
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
                SystemSettings.open(this, Settings.ACTION_SETTINGS));
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
                        : "video".equals(wall) ? R.string.wallpaper_video_state
                        : "none".equals(wall) ? R.string.wallpaper_none : R.string.wallpaper_theme)));
        refreshAppearance();
        String tvWeather = choices.getString(Config.TV_WEATHER_KEY, "");
        String city = Config.load(this).weatherCity;
        String shown = city.isEmpty() ? getString(R.string.weather_off) : city;
        if ("auto".equals(tvWeather)) shown = getString(R.string.weather_auto_state);
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
        String[] items = {getString(R.string.wallpaper_pick), getString(R.string.wallpaper_video_pick),
                getString(R.string.wallpaper_remove), getString(R.string.wallpaper_follow_theme)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.wallpaper_state, "").replaceAll("[:：]\\s*$", ""))
                .setItems(items, (dialog, which) -> {
                    if (which == 0 || which == 1) {
                        Intent pick = new Intent(Intent.ACTION_GET_CONTENT)
                                .setType(which == 0 ? "image/*" : "video/*")
                                .addCategory(Intent.CATEGORY_OPENABLE);
                        try {
                            startActivityForResult(pick, which == 0 ? PICK_WALLPAPER : PICK_VIDEO);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        SharedPreferences.Editor e = choices.edit();
                        if (which == 2) e.putString(Config.TV_WALLPAPER_KEY, "none");
                        else e.remove(Config.TV_WALLPAPER_KEY);
                        e.apply();
                        // Leaving a picture/video behind would keep wasting space and could come back; drop both.
                        deleteWallpaperFile(Config.TV_WALLPAPER_NAME);
                        deleteWallpaperFile(Config.TV_WALLPAPER_VIDEO_NAME);
                        setResult(RESULT_OK);
                        refresh();
                    }
                })

                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == PICK_VIDEO) { saveVideoWallpaper(data.getData()); return; }
        if (requestCode != PICK_WALLPAPER) return;
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
            if (ok) {
                // Set it and tell Home to reload even if Settings was left while the picture was being saved.
                choices.edit().putString(Config.TV_WALLPAPER_KEY, "picked").apply();
                deleteWallpaperFile(Config.TV_WALLPAPER_VIDEO_NAME);
                getApplicationContext().sendBroadcast(new Intent(MainActivity.ACTION_WALLPAPER_CHANGED)
                        .setPackage(getPackageName()));
            }
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (ok) {
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

    /** Copies the picked video into the theme folder (no re-encode) and makes it the Home background. */
    /** Removes a leftover wallpaper file (the other kind) from the theme folder so it does not linger. */
    private void deleteWallpaperFile(String name) {
        File dir = Config.dir(this);
        if (dir == null) return;
        File f = new File(dir, name);
        if (f.isFile()) f.delete();
    }

    private void saveVideoWallpaper(Uri uri) {
        final File dir = Config.dir(this);
        if (dir == null) { Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(this, R.string.wallpaper_saving, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = copyVideo(uri, new File(dir, Config.TV_WALLPAPER_VIDEO_NAME));
            if (ok) {
                // Set the choice even if the user already left Settings while the copy ran, and tell Home to
                // reload so the video shows at once instead of only on the next Home launch.
                choices.edit().putString(Config.TV_WALLPAPER_KEY, "video").apply();
                deleteWallpaperFile(Config.TV_WALLPAPER_NAME);
                getApplicationContext().sendBroadcast(new Intent(MainActivity.ACTION_WALLPAPER_CHANGED)
                        .setPackage(getPackageName()));
            }
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (ok) {
                    setResult(RESULT_OK);
                    refresh();
                } else {
                    Toast.makeText(this, R.string.wallpaper_video_failed, Toast.LENGTH_LONG).show();
                }
            });
        }, "wallpaper-video").start();
    }

    /** Streams the video to dest, refusing files over 200 MB so a 1 GB TV is not filled. */
    private boolean copyVideo(Uri uri, File dest) {
        File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return false;
            byte[] buf = new byte[256 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 200L * 1024 * 1024) { tmp.delete(); return false; }
                out.write(buf, 0, n);
            }
            if (total == 0) { tmp.delete(); return false; }
            return tmp.renameTo(dest) || (dest.delete() && tmp.renameTo(dest));
        } catch (Exception e) {
            tmp.delete();
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
        findViewById(R.id.uiLunar).setOnClickListener(v -> {
            choices.edit().putBoolean(Config.UI_LUNAR, !Config.load(this).lunarDate).apply();
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
        setRow(R.id.uiLunar, R.string.ui_lunar, getString(c.lunarDate ? R.string.on : R.string.off));
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
        // Order: auto (by IP location), then the fixed places, then off and follow-theme.
        int auto = 0, first = 1, off = WEATHER_PLACES.length + 1, theme = WEATHER_PLACES.length + 2;
        String[] labels = new String[WEATHER_PLACES.length + 3];
        labels[auto] = getString(R.string.weather_choose_auto);
        int checked = -1;
        for (int i = 0; i < WEATHER_PLACES.length; i++) {
            labels[first + i] = WEATHER_PLACES[i][0];
            if (current.startsWith(WEATHER_PLACES[i][0] + "|")) checked = first + i;
        }
        labels[off] = getString(R.string.weather_choose_off);
        labels[theme] = getString(R.string.weather_choose_theme);
        if ("auto".equals(current)) checked = auto;
        else if ("off".equals(current)) checked = off;
        else if (current.isEmpty()) checked = theme;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.weather_state, "").replaceAll("[:：]\\s*$", ""))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    SharedPreferences.Editor e = choices.edit();
                    if (which == auto) {
                        e.putString(Config.TV_WEATHER_KEY, "auto");
                    } else if (which >= first && which < off) {
                        String[] p = WEATHER_PLACES[which - first];
                        e.putString(Config.TV_WEATHER_KEY, p[0] + "|" + p[1] + "|" + p[2]);
                    } else if (which == off) {
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
