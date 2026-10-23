package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.gathin.tvhome/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the dock and the look without a rebuild:
 * {@code tools/theme.sh themes/<name>} pushes a theme folder there and restarts Home.
 *
 * Read once in {@link MainActivity#onCreate}; nothing here touches the network.
 */
final class Config {
    private static final String TAG = "TvHome.Config";
    static final String FILE_NAME = "config.json";
    static final String WALLPAPER_NAME = "wallpaper.jpg";
    static final String SETTINGS_NAME = "launcher_settings";
    private static final String THEME_REVISION_NAME = "theme_revision";
    private static final String THEME_SIGNATURE_KEY = "theme_signature";
    /** Wallpaper chosen on the TV (Launcher settings): "picked" (wallpaper.jpg) or "none"; empty follows the theme. */
    static final String TV_WALLPAPER_KEY = "tv_wallpaper";
    /** The picture picked on the TV, kept apart from the theme's wallpaper.jpg so the theme's comes back. */
    static final String TV_WALLPAPER_NAME = "tv_wallpaper.jpg";
    /** Time zone for the launcher's clock, date, greeting and time-based wallpaper; empty follows the TV. */
    static final String CLOCK_ZONE_KEY = "clock_timezone";
    /** Weather place chosen on the TV: "off" or "city|latitude|longitude"; empty follows the theme. */
    static final String TV_WEATHER_KEY = "tv_weather";
    static final String UI_GREETING = "ui_greeting_name";
    static final String UI_BACKGROUND = "ui_background_color";
    static final String UI_SKY = "ui_sky_intensity";
    static final String UI_ROWS_BELOW = "ui_rows_below_dock";
    static final String UI_DOCK_TITLE = "ui_dock_title";
    static final String UI_STATS = "ui_show_stats";
    static final String UI_REDUCED_MOTION = "ui_reduced_motion";
    static final String UI_WALLPAPER_DIM = "ui_wallpaper_dim";
    /** Appearance chosen on the TV; a theme push or "Follow theme" clears them like the other TV choices. */
    static final String[] UI_KEYS = {UI_GREETING, UI_BACKGROUND, UI_SKY, UI_ROWS_BELOW, UI_DOCK_TITLE, UI_STATS,
            UI_REDUCED_MOTION, UI_WALLPAPER_DIM};

    /**
     * Tiles that borrow another installed app's banner, icon and name while still opening their own app
     * (config.xml app_look_like "package=source", or a config.json object of the same name), e.g. TizenTube
     * shown as YouTube. Nothing is copied: the source app's own artwork is read through PackageManager.
     */
    static Map<String, String> lookLike(Context ctx) {
        Map<String, String> map = new HashMap<>();
        for (String item : ctx.getResources().getStringArray(R.array.app_look_like)) {
            int eq = item.indexOf('=');
            if (eq > 0) map.put(item.substring(0, eq).trim(), item.substring(eq + 1).trim());
        }
        File dir = dir(ctx);
        JSONObject json = read(dir != null ? new File(dir, FILE_NAME) : null);
        JSONObject look = json != null ? json.optJSONObject("app_look_like") : null;
        if (look != null) {
            for (java.util.Iterator<String> it = look.keys(); it.hasNext(); ) {
                String pkg = it.next();
                String src = look.optString(pkg, "").trim();
                if (src.isEmpty()) map.remove(pkg); else map.put(pkg, src);
            }
        }
        return map;
    }

    /**
     * Remote buttons that start voice search (config.xml voice_key_keycodes, or config.json of the same name).
     * KEYCODE_SEARCH and KEYCODE_VOICE_ASSIST always do; the XGIMI remote's microphone button sends F5 (135).
     */
    static int[] voiceKeycodes(Context ctx) {
        int[] codes = ctx.getResources().getIntArray(R.array.voice_key_keycodes);
        File dir = dir(ctx);
        JSONObject json = read(dir != null ? new File(dir, FILE_NAME) : null);
        return json != null ? intArray(json, "voice_key_keycodes", codes) : codes;
    }

    /** The Android 6 XGIMI home: the firmware opens it for Home without a chooser and ADB can neither disable nor hide it. */
    static final String LEGACY_XGIMI_HOME = "com.xgimi.overseashome";

    /**
     * home_redirect_package (config.xml, or a theme's config.json): the stock home HomeKeyService replaces. Unset,
     * an Android 6/7 XGIMI with its overseas home gets that home by default, so a launcher installed from a USB
     * stick with a file manager needs no config.json; an empty string in config.json still turns it off.
     */
    static String homeRedirect(Context ctx) {
        String pkg = ctx.getResources().getString(R.string.home_redirect_package).trim();
        File dir = dir(ctx);
        JSONObject json = read(dir != null ? new File(dir, FILE_NAME) : null);
        if (json != null && json.has("home_redirect_package")) return string(json, "home_redirect_package", pkg);
        if (pkg.isEmpty() && android.os.Build.VERSION.SDK_INT < 26 && installed(ctx, LEGACY_XGIMI_HOME)) {
            return LEGACY_XGIMI_HOME;
        }
        return pkg;
    }

    /**
     * home_key (config.json): HomeKeyService takes the remote's Home button and opens this launcher, without
     * the window redirect of home_redirect_package. For a TV that keeps its stock home enabled (XGIMI DOHKO,
     * whose features open pages in it) and then shows a home chooser on Home.
     */
    static boolean homeKey(Context ctx) {
        File dir = dir(ctx);
        JSONObject json = read(dir != null ? new File(dir, FILE_NAME) : null);
        return json != null && bool(json, "home_key", false);
    }

    /** HomeKeyService has the Home button to handle: by home_redirect_package or home_key. */
    static boolean takesHomeKey(Context ctx) {
        return !homeRedirect(ctx).isEmpty() || homeKey(ctx);
    }

    private static boolean installed(Context ctx, String pkg) {
        try {
            ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean isVoiceKey(int keyCode, int[] extra) {
        if (keyCode == android.view.KeyEvent.KEYCODE_SEARCH || keyCode == android.view.KeyEvent.KEYCODE_VOICE_ASSIST) return true;
        for (int c : extra) if (c == keyCode) return true;
        return false;
    }

    /** Removes the appearance choices made on the TV. */
    static SharedPreferences.Editor clearUi(SharedPreferences.Editor e) {
        for (String k : UI_KEYS) e.remove(k);
        return e;
    }

    /**
     * Applies the clock time zone chosen in Launcher settings to this process. A normal app cannot change
     * the TV's own time zone (SET_TIME_ZONE is a privileged permission), so the launcher shows its own.
     */
    static void applyClockZone(Context ctx) {
        String id = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE).getString(CLOCK_ZONE_KEY, "");
        TimeZone.setDefault(id.isEmpty() ? null : TimeZone.getTimeZone(id));
    }

    final List<String> dockApps;
    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock); on by default, false leaves the slots empty. */
    final boolean fillDock;
    final List<String> hiddenApps;
    final String liveTvPackage;
    final String liveTvAction;
    final String sourcePanelService;
    final String sourcePanelAction;
    final String inputAction;
    final int columns;
    final boolean animations;
    final boolean advancedEffects;
    final int rowsBelowDock;
    final boolean shamsiDate;
    /** Vietnamese lunar date above the date (config.xml lunar_date). */
    final boolean lunarDate;
    /** The lunar date sits above the top buttons instead of the line above the date (config.json lunar_top_right). */
    final boolean lunarTopRight;
    /** Run /data/local/tmp/cleantv/boot.sh through the TV's adbd after boot (config.json boot_script, BootScript). */
    final boolean bootScript;
    /** Name after the greeting ("Good morning, Mr. Trịnh"); empty for the greeting alone. */
    final String greetingName;
    /** Place whose weather follows the greeting (config.xml weather_city); empty: no weather. */
    final String weatherCity;
    final double weatherLatitude;
    final double weatherLongitude;
    final boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    final boolean reducedMotion;
    /** The focused dock app's name in large type above the dock (config.xml, dock_title). */
    final boolean dockTitle;
    /** The dock as its own row: band, no names, parked low (config.xml dock_row). false: one plain grid. */
    final boolean dockRow;
    /** Reorder button in the top bar (config.xml reorder_button); the Settings long-press menu offers it either way. */
    final boolean reorderButton;
    final String vpnApp;
    final boolean showHomeSettingsShortcut;
    /** One line under the clock: CPU, free memory, free storage. Off unless config or a theme turns it on. */
    final boolean showStats;
    /** App library pill after the last Home row (config.xml app_library). */
    final boolean appLibrary;
    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise. */
    final int backgroundColor;
    /** Time-of-day backdrop, 0 to 100 (config.xml, sky_intensity); 0 keeps the plain background. */
    final int skyIntensity;
    /** Remote keycodes that open Settings through HomeKeyService (config.xml, settings_key_keycodes). */
    final int[] settingsKeycodes;
    /** Extra keycodes that open the input switcher in InputKeyService (config.xml input_key_keycodes). */
    final int[] inputKeycodes;
    /** Remote buttons that open an app, "keycode:package" or "KEYCODE_NAME:package" (config.xml app_keys). */
    final List<String> appKeys;
    /** The Settings button opens the quick settings panel instead of the TV's Settings (settings_key_panel). */
    final boolean settingsKeyPanel;
    /** Whether themes may show a wallpaper (config.json, wallpaper). */
    final boolean wallpaperEnabled;
    /** Full-screen picture behind the launcher, or null for the plain colour. */
    final File wallpaper;
    /** Rotating theme images, or the single wallpaper when no collection is configured. */
    final List<File> wallpapers;
    final int wallpaperRotationMinutes;
    /** Theme images named morning/afternoon/evening/night follow the hour (config.json wallpaper_by_time). */
    final boolean wallpaperByTime;
    /** Switch ADB back on at boot and on Home (config.xml keep_adb_on, AdbKeeper). */
    final boolean keepAdbOn;
    final int wallpaperDim;
    /** Whether the overlay file was present and parsed. */
    final boolean overlayApplied;
    /** Festive skin from a theme (festival, festival_until, greeting, accent_color); {@link Festival#NONE} if none. */
    final Festival festival;

    private Config(List<String> dockApps, boolean fillDock, List<String> hiddenApps, String liveTvPackage,
                   String liveTvAction, String sourcePanelService, String sourcePanelAction, String inputAction,
                   int columns, int rowsBelowDock, boolean animations, boolean advancedEffects,
                   boolean shamsiDate, String greetingName, boolean moduleCards, boolean reducedMotion, boolean dockTitle, boolean dockRow, boolean reorderButton, String vpnApp,
                   boolean showHomeSettingsShortcut, boolean showStats, boolean appLibrary, int backgroundColor, int skyIntensity,
                   int[] settingsKeycodes, boolean settingsKeyPanel, boolean wallpaperEnabled, File wallpaper, List<File> wallpapers,
                   int wallpaperRotationMinutes, int wallpaperDim, boolean overlayApplied,
                   Festival festival, int[] inputKeycodes, List<String> appKeys,
                   String weatherCity, double weatherLatitude, double weatherLongitude, boolean wallpaperByTime,
                   boolean keepAdbOn, boolean lunarDate, boolean lunarTopRight, boolean bootScript) {
        this.lunarDate = lunarDate;
        this.lunarTopRight = lunarTopRight;
        this.bootScript = bootScript;
        this.wallpaperByTime = wallpaperByTime;
        this.keepAdbOn = keepAdbOn;
        this.weatherCity = weatherCity;
        this.weatherLatitude = weatherLatitude;
        this.weatherLongitude = weatherLongitude;
        this.inputKeycodes = inputKeycodes;
        this.appKeys = Collections.unmodifiableList(appKeys);
        this.dockApps = Collections.unmodifiableList(dockApps);
        this.fillDock = fillDock;
        this.hiddenApps = Collections.unmodifiableList(hiddenApps);
        this.liveTvPackage = liveTvPackage;
        this.liveTvAction = liveTvAction;
        this.sourcePanelService = sourcePanelService;
        this.sourcePanelAction = sourcePanelAction;
        this.inputAction = inputAction;
        this.columns = columns;
        this.rowsBelowDock = rowsBelowDock;
        this.animations = animations;
        this.advancedEffects = advancedEffects;
        this.shamsiDate = shamsiDate;
        this.greetingName = greetingName;
        this.moduleCards = moduleCards;
        this.reducedMotion = reducedMotion;
        this.dockTitle = dockTitle;
        this.dockRow = dockRow;
        this.reorderButton = reorderButton;
        this.vpnApp = vpnApp;
        this.showHomeSettingsShortcut = showHomeSettingsShortcut;
        this.showStats = showStats;
        this.appLibrary = appLibrary;
        this.backgroundColor = backgroundColor;
        this.skyIntensity = skyIntensity;
        this.settingsKeycodes = settingsKeycodes;
        this.settingsKeyPanel = settingsKeyPanel;
        this.wallpaperEnabled = wallpaperEnabled;
        this.wallpaper = wallpaper;
        this.wallpapers = Collections.unmodifiableList(wallpapers);
        this.wallpaperRotationMinutes = wallpaperRotationMinutes;
        this.wallpaperDim = wallpaperDim;
        this.overlayApplied = overlayApplied;
        this.festival = festival;
    }

    /** The folder the theme tools push into; null when external storage is unavailable. */
    static File dir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static Config load(Context ctx) {
        Resources res = ctx.getResources();
        List<String> dock = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        boolean fillDock = res.getBoolean(R.bool.fill_dock);
        List<String> hidden = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        String liveTv = res.getString(R.string.live_tv_package).trim();
        String liveTvAction = res.getString(R.string.live_tv_action).trim();
        String panelService = res.getString(R.string.source_panel_service).trim();
        String panelAction = res.getString(R.string.source_panel_action).trim();
        String inputAction = res.getString(R.string.input_action).trim();
        int columns = res.getInteger(R.integer.columns);
        int rowsBelow = res.getInteger(R.integer.rows_below_dock);
        boolean animations = res.getBoolean(R.bool.animations);
        boolean advancedEffects = res.getBoolean(R.bool.advanced_effects);
        boolean shamsi = res.getBoolean(R.bool.shamsi_date);
        boolean lunar = res.getBoolean(R.bool.lunar_date);
        boolean lunarTopRight = false;   // config.json only
        boolean bootScript = false;   // config.json only: tools/xgimi-vi/dohko-boot-vi.ps1 turns it on
        String greetingName = res.getString(R.string.greeting_name).trim();
        boolean keepAdbOn = res.getBoolean(R.bool.keep_adb_on);
        String weatherCity = res.getString(R.string.weather_city).trim();
        double weatherLat = parseDouble(res.getString(R.string.weather_latitude), Double.NaN);
        double weatherLon = parseDouble(res.getString(R.string.weather_longitude), Double.NaN);
        boolean moduleCards = res.getBoolean(R.bool.module_cards);
        boolean reducedMotion = res.getBoolean(R.bool.reduced_motion);
        boolean dockTitle = res.getBoolean(R.bool.dock_title);
        boolean dockRow = res.getBoolean(R.bool.dock_row);
        boolean reorderButton = res.getBoolean(R.bool.reorder_button);
        String vpn = res.getString(R.string.vpn_app).trim();
        boolean homeSettings = res.getBoolean(R.bool.show_home_settings_shortcut);
        boolean showStats = res.getBoolean(R.bool.show_stats);
        boolean library = res.getBoolean(R.bool.app_library);
        int background = 0xFF000000;
        int skyIntensity = res.getInteger(R.integer.sky_intensity);
        int[] settingsKeycodes = res.getIntArray(R.array.settings_key_keycodes);
        boolean settingsKeyPanel = res.getBoolean(R.bool.settings_key_panel);
        int[] inputKeycodes = res.getIntArray(R.array.input_key_keycodes);
        List<String> appKeys = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.app_keys)));
        boolean applied = false;

        File dir = dir(ctx);
        File file = dir != null ? new File(dir, FILE_NAME) : null;
        JSONObject json = read(file);
        if (json != null) {
            applied = true;
            dock = stringList(json, "dock_apps", dock);
            fillDock = bool(json, "fill_dock", fillDock);
            hidden = stringList(json, "hidden_apps", hidden);
            liveTv = string(json, "live_tv_package", liveTv);
            liveTvAction = string(json, "live_tv_action", liveTvAction);
            panelService = string(json, "source_panel_service", panelService);
            panelAction = string(json, "source_panel_action", panelAction);
            inputAction = string(json, "input_action", inputAction);
            columns = integer(json, "columns", columns, 1, 8);
            rowsBelow = integer(json, "rows_below_dock", rowsBelow, 0, 3);
            animations = bool(json, "animations", animations);
            advancedEffects = bool(json, "advanced_effects", advancedEffects);
            shamsi = bool(json, "shamsi_date", shamsi);
            lunar = bool(json, "lunar_date", lunar);
            lunarTopRight = bool(json, "lunar_top_right", lunarTopRight);
            bootScript = bool(json, "boot_script", bootScript);
            greetingName = string(json, "greeting_name", greetingName).trim();
            keepAdbOn = bool(json, "keep_adb_on", keepAdbOn);
            weatherCity = string(json, "weather_city", weatherCity);
            weatherLat = number(json, "weather_latitude", weatherLat, -90, 90);
            weatherLon = number(json, "weather_longitude", weatherLon, -180, 180);
            moduleCards = bool(json, "module_cards", moduleCards);
            reducedMotion = bool(json, "reduced_motion", reducedMotion);
            dockTitle = bool(json, "dock_title", dockTitle);
            dockRow = bool(json, "dock_row", dockRow);
            reorderButton = bool(json, "reorder_button", reorderButton);
            vpn = string(json, "vpn_app", vpn);
            homeSettings = bool(json, "show_home_settings_shortcut", homeSettings);
            showStats = bool(json, "show_stats", showStats);
            library = bool(json, "app_library", library);
            background = color(json, "background_color", background);
            skyIntensity = integer(json, "sky_intensity", skyIntensity, 0, 100);
            settingsKeycodes = intArray(json, "settings_key_keycodes", settingsKeycodes);
            settingsKeyPanel = bool(json, "settings_key_panel", settingsKeyPanel);
            inputKeycodes = intArray(json, "input_key_keycodes", inputKeycodes);
            appKeys = stringList(json, "app_keys", appKeys);
        }
        // A theme push or reset invalidates choices made on the TV. On first use, remember the
        // current theme without clearing choices from an older launcher version.
        SharedPreferences choices = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE);
        syncThemeChoices(choices, dir, file);
        int chosenColumns = choices.getInt("columns", columns);
        if (chosenColumns >= 4 && chosenColumns <= 7) columns = chosenColumns;
        animations = choices.getBoolean("animations", animations);
        advancedEffects = choices.getBoolean("advanced_effects", advancedEffects);
        // Weather chosen on the TV (Launcher settings): "off", or "city|latitude|longitude".
        // Look chosen on the TV (Launcher settings > Appearance); each key overrides config.xml and the theme.
        greetingName = choices.getString(UI_GREETING, greetingName).trim();
        background = choices.getInt(UI_BACKGROUND, background);
        skyIntensity = Math.max(0, Math.min(100, choices.getInt(UI_SKY, skyIntensity)));
        rowsBelow = Math.max(0, Math.min(3, choices.getInt(UI_ROWS_BELOW, rowsBelow)));
        dockTitle = choices.getBoolean(UI_DOCK_TITLE, dockTitle);
        showStats = choices.getBoolean(UI_STATS, showStats);
        reducedMotion = choices.getBoolean(UI_REDUCED_MOTION, reducedMotion);
        String tvWeather = choices.getString(TV_WEATHER_KEY, "");
        if ("off".equals(tvWeather)) {
            weatherCity = "";
        } else if (!tvWeather.isEmpty()) {
            String[] p = tvWeather.split("\\|");
            if (p.length == 3) {
                weatherCity = p[0];
                weatherLat = parseDouble(p[1], Double.NaN);
                weatherLon = parseDouble(p[2], Double.NaN);
            }
        }
        File wallpaper = dir != null ? new File(dir, WALLPAPER_NAME) : null;
        if (wallpaper != null && !(wallpaper.isFile() && wallpaper.length() > 0)) wallpaper = null;
        // A theme can also switch the wallpaper off while the file is still there.
        boolean wallpaperEnabled = json == null || bool(json, "wallpaper", true);
        // A wallpaper picked (or removed) on the TV wins over the theme's until the next theme push.
        String tvWallpaper = choices.getString(TV_WALLPAPER_KEY, "");
        if ("picked".equals(tvWallpaper) && dir != null) {
            File picked = new File(dir, TV_WALLPAPER_NAME);
            if (picked.isFile() && picked.length() > 0) {
                wallpaper = picked;
                wallpaperEnabled = true;
            }
        }
        else if ("none".equals(tvWallpaper)) wallpaperEnabled = false;
        if (!wallpaperEnabled) wallpaper = null;
        boolean themeWallpapers = json != null && tvWallpaper.isEmpty();
        int rotationMinutes = themeWallpapers ? integer(json, "wallpaper_rotation_minutes", 0, 0, 1440) : 0;
        int wallpaperDim = json == null ? 0 : integer(json, "wallpaper_dim", 0, 0, 90);
        wallpaperDim = Math.max(0, Math.min(90, choices.getInt(UI_WALLPAPER_DIM, wallpaperDim)));
        boolean byTime = themeWallpapers && bool(json, "wallpaper_by_time", false);
        List<File> wallpapers = new ArrayList<>();
        if ((rotationMinutes > 0 || byTime) && dir != null && wallpaperEnabled) {
            wallpapers.addAll(WallpaperRotation.images(new File(dir, "wallpapers")));
        }
        if (wallpapers.isEmpty() && wallpaper != null) wallpapers.add(wallpaper);
        // A festive skin comes only from a theme; config.xml has no festival.
        Festival festival = json == null ? Festival.NONE : Festival.parse(string(json, "festival", ""),
                string(json, "festival_until", ""), string(json, "greeting", ""), string(json, "accent_color", ""));

        return new Config(dock, fillDock, hidden, liveTv, liveTvAction, panelService, panelAction, inputAction,
                Math.max(1, columns), Math.max(0, rowsBelow), animations, advancedEffects,
                shamsi, greetingName, moduleCards, reducedMotion, dockTitle, dockRow, reorderButton, vpn, homeSettings, showStats, library, background,
                Math.max(0, Math.min(100, skyIntensity)), settingsKeycodes, settingsKeyPanel, wallpaperEnabled, wallpaper,
                wallpapers, rotationMinutes, wallpaperDim, applied,
                festival, inputKeycodes, appKeys,
                Double.isNaN(weatherLat) || Double.isNaN(weatherLon) ? "" : weatherCity, weatherLat, weatherLon,
                byTime, keepAdbOn, lunar, lunarTopRight, bootScript);
    }

    private static void syncThemeChoices(SharedPreferences choices, File dir, File config) {
        File revision = dir == null ? null : new File(dir, THEME_REVISION_NAME);
        String token = revisionToken(revision);
        String signature = fileStamp(config) + ":" + token;
        String seen = choices.getString(THEME_SIGNATURE_KEY, null);
        if (seen == null) {
            // Existing preferences survive an app upgrade, unless the first launch follows an
            // explicit theme push or reset by the updated tools.
            SharedPreferences.Editor editor = choices.edit();
            if (!"absent".equals(token)) editor.remove("columns").remove("animations")
                    .remove("advanced_effects");
            editor.putString(THEME_SIGNATURE_KEY, signature).commit();
        } else if (!seen.equals(signature)) {
            // A new theme brings its own wallpaper, so a picture picked on the TV gives way to it.
            clearUi(choices.edit()).remove("columns").remove("animations").remove("advanced_effects")
                    .remove(TV_WALLPAPER_KEY).remove(TV_WEATHER_KEY).putString(THEME_SIGNATURE_KEY, signature).commit();
        }
    }

    private static String fileStamp(File file) {
        return file != null && file.isFile() ? file.length() + "@" + file.lastModified() : "absent";
    }

    private static String revisionToken(File file) {
        if (file == null || !file.isFile() || file.length() > 64) return "absent";
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            return n > 0 ? new String(bytes, 0, n, StandardCharsets.US_ASCII) : "absent";
        } catch (Exception ignored) {
            return "absent";
        }
    }

    /** Reads and parses the overlay; any problem means "no overlay", never a crash on the home screen. */
    private static JSONObject read(File file) {
        if (file == null || !file.isFile()) return null;
        if (file.length() > 64 * 1024) {
            Log.w(TAG, "ignoring " + file + ": larger than 64 KB");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);   // Windows editors add a BOM
            return new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static String string(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    private static boolean bool(JSONObject o, String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(JSONObject o, String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    private static double number(JSONObject o, String key, double fallback, double min, double max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        double d = ((Number) v).doubleValue();
        return d < min || d > max ? fallback : d;
    }

    private static double parseDouble(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int color(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return fallback;
        try {
            int c = Color.parseColor(((String) v).trim());
            return c | 0xFF000000;   // always opaque: the window behind is black anyway
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Positive keycodes only (vendor remotes use codes in the thousands); a list with anything else in it is ignored as a whole. */
    private static int[] intArray(JSONObject o, String key, int[] fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        int[] out = new int[a.length()];
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (!(item instanceof Number)) return fallback;
            out[i] = ((Number) item).intValue();
            if (out[i] < 1) return fallback;
        }
        return out;
    }

    private static List<String> stringList(JSONObject o, String key, List<String> fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (item instanceof String && !((String) item).trim().isEmpty()) out.add(((String) item).trim());
        }
        return out;
    }
}
