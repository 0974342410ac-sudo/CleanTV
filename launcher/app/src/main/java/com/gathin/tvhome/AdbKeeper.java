package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

/**
 * keep_adb_on (config.xml or a theme's config.json): switches ADB back on at boot and whenever Home opens, so
 * a PC (adb, the tools/ scripts) and the phone updater can always reach the TV. Needs WRITE_SECURE_SETTINGS, which only
 * `pm grant` over ADB can give; without it this does nothing. Network ADB also needs a fixed TCP port
 * (persist.adb.tcp.port, as on XGIMI GMUI); Google TV's Wireless debugging is not switched on here.
 *
 * {@link #ACTION_ENABLE_ADB} switches ADB on once whatever keep_adb_on says. It is guarded by a signature
 * permission, so only apps signed like this launcher can send it: the USB updater (vn.cleantv.updater) sends it
 * when it finds ADB off, then connects.
 */
public final class AdbKeeper extends BroadcastReceiver {
    private static final String TAG = "TvHome.AdbKeeper";
    static final String ACTION_ENABLE_ADB = "com.gathin.tvhome.action.ENABLE_ADB";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Config config = Config.load(context);
        ensure(context, config.keepAdbOn);
        // Things this TV forgets at every boot (boot_script): put back through its own adbd
        if (config.bootScript) BootScript.run(goAsync());
        // A stock home this launcher replaces (home_redirect_package) came up at boot: open this launcher over it,
        // even before HomeKeyService is enabled (on the Android 6 XGIMI only ADB can enable it)
        if (Config.takesHomeKey(context)) {
            ensureHomeKeyService(context);
            Intent home = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(home);
            } catch (RuntimeException e) {
                Log.w(TAG, "cannot open Home at boot: " + e);
            }
        }
    }

    /**
     * ACTION_ENABLE_ADB, in its own receiver: the manifest guards this one with the ENABLE_ADB signature
     * permission, while AdbKeeper itself must stay open for BOOT_COMPLETED and so never acts on a request.
     */
    public static final class Request extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_ENABLE_ADB.equals(intent.getAction())) ensure(context, true);
        }
    }

    /**
     * home_redirect_package set: puts HomeKeyService back in the accessibility list when another app took it out
     * (HomeTV DinhLap rewrites the list with only its own service), keeping the services already there. Needs
     * WRITE_SECURE_SETTINGS, like {@link #ensure}. Returns whether the service is on afterwards.
     */
    static boolean ensureHomeKeyService(Context context) {
        if (!Config.takesHomeKey(context)) return false;
        ContentResolver cr = context.getContentResolver();
        String me = context.getPackageName() + "/.HomeKeyService";
        String list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null || list.equals("null")) list = "";
        boolean listed = list.contains(me);
        if (listed && Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1) return true;
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return false;
        try {
            if (!listed) {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.isEmpty() ? me : list + ":" + me);
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Log.i(TAG, "HomeKeyService switched back on");
            return true;
        } catch (SecurityException e) {
            Log.w(TAG, "cannot switch HomeKeyService on: " + e);
            return false;
        }
    }

    static void ensure(Context context, boolean wanted) {
        if (!wanted || context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return;
        ContentResolver cr = context.getContentResolver();
        try {
            // Switching Developer options off also stops ADB, so both come back on.
            if (Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 1) {
                Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
            }
            if (Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) != 1) {
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1);
                Log.i(TAG, "ADB switched back on");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "cannot switch ADB on: " + e);
        }
    }
}
