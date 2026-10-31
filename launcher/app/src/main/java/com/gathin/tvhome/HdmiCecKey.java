package com.gathin.tvhome;

import android.view.KeyEvent;

/**
 * The OK button on an HDMI source of an MStar TV. MTvPlayer hands every key to TvCecManager.sendCecKey, whose table has no
 * DPAD_CENTER, so OK never reaches the HDMI device; ENTER is in the table and arrives as CEC Select. Reached by reflection,
 * since the classes are only on such TVs. Same checks as RootActivity.sendCecKey.
 */
final class HdmiCecKey {
    private HdmiCecKey() {
    }

    /** The player window showing an input, or the source banner over it. Its menus are other windows, where OK stays OK. */
    static boolean isPlayer(String window) {
        return "com.mstar.tv.tvplayer.ui.RootActivity".equals(window)
                || "com.mstar.tv.tvplayer.ui.channel.SourceInfoActivity".equals(window);
    }

    /**
     * MStar sends no CEC release, so the device holds a key until its own timeout (550 ms on Android), long enough for a
     * long press on OK. Another key ends the hold. Only 15 keys can be sent; Play does least: nothing outside a player.
     */
    static void endOk() {
        try {
            Class<?> cecClass = Class.forName("com.mstar.android.tv.TvCecManager");
            Object cec = cecClass.getMethod("getInstance").invoke(null);
            cecClass.getMethod("sendCecKey", int.class).invoke(cec, KeyEvent.KEYCODE_MEDIA_PLAY);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // The device's own timeout ends the hold
        }
    }

    /** True when the key went out; on false the caller lets OK pass untouched. */
    static boolean sendOk() {
        try {
            Class<?> cecClass = Class.forName("com.mstar.android.tv.TvCecManager");
            Object cec = cecClass.getMethod("getInstance").invoke(null);
            Object setting = cecClass.getMethod("getCecConfiguration").invoke(cec);
            if (((Number) setting.getClass().getField("cecStatus").get(setting)).intValue() != 1) return false;
            Class<?> commonClass = Class.forName("com.mstar.android.tv.TvCommonManager");
            Object common = commonClass.getMethod("getInstance").invoke(null);
            if (!Boolean.TRUE.equals(commonClass.getMethod("isHdmiSignalMode").invoke(common))) return false;
            return Boolean.TRUE.equals(cecClass.getMethod("sendCecKey", int.class).invoke(cec, KeyEvent.KEYCODE_ENTER));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
