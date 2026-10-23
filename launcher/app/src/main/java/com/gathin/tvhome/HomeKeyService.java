package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Collections;
import java.util.Map;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same map InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();

    /**
     * home_redirect_package: a stock home that can be neither disabled nor hidden over ADB (system uid, Android 6
     * XGIMI) and that the firmware opens for Home without a chooser. Home goes here instead, and whenever that
     * home comes to the front anyway (Back, boot) this launcher is opened over it. Empty: nothing redirected.
     */
    private String redirectHome = "";
    /** home_key: Home opens this launcher (no chooser), with no window redirect. */
    private boolean homeKey;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        redirectHome = Config.homeRedirect(this);
        homeKey = Config.homeKey(this);
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (settingsPanel) {
                    startActivity(new Intent(this, QuickSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    SystemSettings.open(this, Settings.ACTION_SETTINGS);
                }
            }
            return true;
        }
        String app = appKeys.get(event.getKeyCode());
        if (app != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, app);
            return true;
        }
        boolean home = event.getKeyCode() == KeyEvent.KEYCODE_ALL_APPS
                || event.getKeyCode() == KeyEvent.KEYCODE_HOME && (!redirectHome.isEmpty() || homeKey);
        if (!home) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) openHome();
        return true;
    }

    private void openHome() {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(home);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // The stock home came up anyway (Back to home, boot): this launcher goes over it
        if (!redirectHome.isEmpty() && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && redirectHome.equals(String.valueOf(event.getPackageName()))) {
            openHome();
        }
    }

    @Override
    public void onInterrupt() {
    }
}
