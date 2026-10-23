package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Opens the TV's own settings. Most firmware answers Settings.ACTION_SETTINGS with an activity; Xiaomi's
 * PatchWall only has its own entry activity, and XGIMI's China-market GMUI has no settings activity at all:
 * its settings are a floating panel that a service in com.android.newsettings draws when it receives
 * com.xgimi.settings.SETTINGS (the panel covers network, picture, sources and keystone, so Wi-Fi goes there too).
 */
final class SystemSettings {
    private static final String XGIMI_PACKAGE = "com.android.newsettings";
    private static final String XGIMI_ACTION = "com.xgimi.settings.SETTINGS";

    private SystemSettings() {}

    /** Opens the screen for {@code standardAction} (e.g. Settings.ACTION_WIFI_SETTINGS), or the nearest thing. */
    static void open(Context ctx, String standardAction) {
        Intent i = new Intent(standardAction);
        if (i.resolveActivity(ctx.getPackageManager()) == null && !Settings.ACTION_SETTINGS.equals(standardAction)) {
            i = new Intent(Settings.ACTION_SETTINGS);
        }
        if (i.resolveActivity(ctx.getPackageManager()) == null) {
            i = new Intent().setComponent(new ComponentName(
                    "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
        }
        if (i.resolveActivity(ctx.getPackageManager()) != null) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException ignored) {
                // fall through to the XGIMI panel
            }
        }
        if (openXgimiPanel(ctx)) return;
        Toast.makeText(ctx, R.string.not_available, Toast.LENGTH_SHORT).show();
    }

    private static boolean openXgimiPanel(Context ctx) {
        try {
            return ctx.startService(new Intent(XGIMI_ACTION).setPackage(XGIMI_PACKAGE)) != null;
        } catch (RuntimeException e) {   // SecurityException, or IllegalStateException when started from the background
            return false;
        }
    }
}
