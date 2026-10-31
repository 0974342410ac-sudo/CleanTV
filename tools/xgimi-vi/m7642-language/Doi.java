package vn.cleantv.language;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;

import java.util.Locale;

/**
 * For the XGIMI m7642 firmware (build-firmware-vi.ps1). XGIMI's language page only knows Chinese and English, in code, so
 * its Chinese row is labelled "Tiếng Việt" and whenever the system turns Chinese this puts Vietnamese in its place, the
 * same way that page changes the language. Needs CHANGE_CONFIGURATION, so it lives in /system/priv-app.
 */
public final class Doi extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"zh".equals(context.getResources().getConfiguration().locale.getLanguage())) return;
        try {
            Object am = Class.forName("android.app.ActivityManagerNative").getMethod("getDefault").invoke(null);
            Class<?> type = Class.forName("android.app.IActivityManager");
            Configuration c = (Configuration) type.getMethod("getConfiguration").invoke(am);
            c.setLocale(new Locale("vi", "VN"));
            Configuration.class.getField("userSetLocale").setBoolean(c, true);
            type.getMethod("updateConfiguration", Configuration.class).invoke(am, c);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e("CleanTVLanguage", "cannot switch to Vietnamese", e);
        }
    }
}
