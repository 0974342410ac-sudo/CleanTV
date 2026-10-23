package vn.cleantv.updater;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates the projector without a PC: installed from a USB stick, it talks to the TV's own ADB as root and
 * does what updater\CapNhat.ps1 does: install the launcher from assets, disable the debloat list, and apply
 * the Vietnamese overlay for XGIMI's settings panel. "Khôi phục" undoes all three.
 */
public final class UpdaterActivity extends Activity {
    private static final String LAUNCHER = "com.gathin.tvhome";
    private static final String STOCK_HOME = "com.xgimi.home";
    private static final String STOCK_HOME_ACTIVITY = "com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity";
    private static final String SETTINGS = "com.android.newsettings";
    private static final String OVERLAY = "com.android.shell:VietHoa";
    private static final String ENABLE_ADB = "com.gathin.tvhome.action.ENABLE_ADB";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView log;
    private ScrollView scroll;
    private Button update, restore, enableAdb, back;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0E1116);
        int pad = (int) (48 * dp);
        root.setPadding(pad, (int) (32 * dp), pad, (int) (24 * dp));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText(BuildConfig.XGIMI_ONLY
                ? "Bản dành riêng cho máy chiếu XGIMI DOHKO (Android 14): cài launcher mới nhất, tắt quảng cáo, Việt hoá "
                        + "Cài đặt, menu tắt máy và 20 ứng dụng XGIMI, giữ tiếng Việt sau khởi động. Không cần máy tính; "
                        + "máy cần bật Gỡ lỗi mạng (ADB)."
                : "Cài launcher mới nhất, tắt quảng cáo và ứng dụng thừa (máy chiếu XGIMI, XGIMI Android 6, onn 4K Box), Việt hoá "
                        + "Cài đặt XGIMI. Không cần máy tính; máy cần bật Gỡ lỗi mạng (ADB).");
        sub.setTextColor(0xFFAAB4BE);
        sub.setTextSize(15);
        sub.setPadding(0, (int) (6 * dp), 0, (int) (18 * dp));
        root.addView(sub);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        update = button("Cập nhật", dp);
        restore = button("Khôi phục", dp);
        enableAdb = button("Bật ADB", dp);
        row.addView(update);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) (16 * dp);
        row.addView(restore, lp);
        row.addView(enableAdb, new LinearLayout.LayoutParams(lp));
        back = button("Trở về", dp);
        row.addView(back, new LinearLayout.LayoutParams(lp));
        root.addView(row);

        scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextColor(0xFFE6E6E6);
        log.setTextSize(15);
        log.setLineSpacing(4 * dp, 1f);
        scroll.addView(log);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.topMargin = (int) (20 * dp);
        root.addView(scroll, sp);
        setContentView(root);

        update.setOnClickListener(v -> run(this::doUpdate));
        restore.setOnClickListener(v -> run(this::doRestore));
        enableAdb.setOnClickListener(v -> run(this::doEnableAdb));
        back.setOnClickListener(v -> goBack());
        update.requestFocus();
    }

    /**
     * "Trở về": leaves the app for the new launcher by name. A plain Home intent can land on XGIMI's home chooser,
     * since XGIMI's home is kept enabled; without the launcher, just closes this screen.
     */
    private void goBack() {
        Intent home = new Intent(Intent.ACTION_MAIN).setClassName(LAUNCHER, LAUNCHER + ".MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (RuntimeException notInstalled) {
            // no new launcher here: the previous screen comes back
        }
        finish();
    }

    private Button button(String text, float dp) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTextColor(Color.WHITE);
        b.setPadding((int) (28 * dp), (int) (12 * dp), (int) (28 * dp), (int) (12 * dp));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, pill(0xFF2F7DF6, dp));
        bg.addState(new int[]{}, pill(0xFF262C36, dp));
        b.setBackground(bg);
        b.setFocusable(true);
        return b;
    }

    private static GradientDrawable pill(int color, float dp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(28 * dp);
        return g;
    }

    private interface Job { void work(LocalAdb adb) throws Exception; }

    private void run(Job job) {
        if (busy) return;
        busy = true;
        update.setEnabled(false);
        restore.setEnabled(false);
        enableAdb.setEnabled(false);
        back.setEnabled(false);
        log.setText("");
        new Thread(() -> {
            // Connection problems and failures of the job itself get different advice
            LocalAdb adb = null;
            try {
                adb = connect();
            } catch (LocalAdb.AuthRequired e) {
                say("!! Không vào được ADB của máy: " + e.getMessage() + ".");
                say("   Bấm lại nút, rồi trên hộp \"Cho phép gỡ lỗi USB?\" chọn \"Luôn cho phép\" và bấm Cho phép.");
            } catch (IOException e) {
                say("!! Không kết nối được ADB của máy chiếu (" + e.getMessage() + ").");
                say("   Launcher không bật lại được ADB (máy chưa có launcher mới, hoặc launcher chưa được cấp quyền).");
                say("   Bật Gỡ lỗi mạng (ADB) trong Tuỳ chọn nhà phát triển rồi thử lại.");
                // Android 6 adbd names every network connection "host" and silently drops a second one
                say("   Nếu máy tính đang kết nối ADB tới máy chiếu, ngắt kết nối đó (adb disconnect) rồi thử lại.");
            }
            if (adb != null) {
                try {
                    job.work(adb);
                } catch (Exception e) {
                    say("!! Lỗi: " + (e.getMessage() != null ? firstLine(e.getMessage()) : e.toString()));
                } finally {
                    adb.close();
                }
            }
            ui.post(() -> {
                busy = false;
                update.setEnabled(true);
                restore.setEnabled(true);
                enableAdb.setEnabled(true);
                back.setEnabled(true);
                // Done: the next press is "Trở về", never "Khôi phục" by accident
                back.requestFocus();
            });
        }, "updater").start();
    }

    /**
     * Connects to the TV's own ADB. When it is off, asks the launcher to switch it on (AdbKeeper.Request; works
     * once a launcher install granted it WRITE_SECURE_SETTINGS) and retries for up to 15 seconds.
     */
    private LocalAdb connect() throws IOException {
        try {
            return open();
        } catch (LocalAdb.AuthRequired e) {
            throw e;
        } catch (IOException first) {
            say("ADB đang tắt: nhờ launcher bật lại…");
            sendBroadcast(new Intent(ENABLE_ADB).setPackage(LAUNCHER));
            for (int i = 0; i < 15; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw first;
                }
                try {
                    LocalAdb adb = open();
                    say("   OK  ADB đã bật lại");
                    say("");
                    return adb;
                } catch (LocalAdb.AuthRequired e) {
                    throw e;
                } catch (IOException ignored) {
                    // adbd is still starting
                }
            }
            throw first;
        }
    }

    /**
     * Connects again after adbd restarted (as root). It comes back a few seconds later, and Android 6 adbd can
     * ignore a connection made while it settles (LocalAdb gives up on it after 10 s): try for about a minute.
     */
    private LocalAdb reopen() throws IOException, InterruptedException {
        IOException last = null;
        for (int i = 0; i < 5; i++) {
            Thread.sleep(i == 0 ? 5000 : 2000);
            try {
                return open();
            } catch (LocalAdb.AuthRequired e) {
                throw e;
            } catch (IOException e) {
                last = e;
                android.util.Log.i("CapNhat", "reopen " + (i + 1) + ": " + e);
            }
        }
        throw last;
    }

    /** One connection to the TV's adbd, with this app's key for TVs that ask for one. */
    private LocalAdb open() throws IOException {
        AdbKey key;
        try {
            key = AdbKey.load(getFilesDir());
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("không tạo được khoá ADB", e);
        }
        return new LocalAdb(5555, key, () -> {
            say("Trên TV đang hiện hộp \"Cho phép gỡ lỗi USB?\":");
            say("   chọn \"Luôn cho phép từ máy tính này\" rồi bấm Cho phép (chỉ cần một lần).");
        });
    }

    // ---------- Devices ----------
    /** What differs between the TVs this app knows: removal list, stock home, voice key, XGIMI's settings text. */
    private static final class Device {
        final String label, debloat, stockHome;
        final boolean xgimi, voiceKeyService;
        /** The Android 6 MStar XGIMI: no `cmd`, a stock home ADB cannot disable, Settings text by static overlay. */
        final boolean legacyXgimi;
        /** Assets folder of ready-built translation overlays (VietHoa-<target package>.apk), installed in step 3. */
        final String overlays;

        Device(String label, String debloat, String stockHome, boolean xgimi, boolean voiceKeyService) {
            this(label, debloat, stockHome, xgimi, voiceKeyService, false);
        }

        Device(String label, String debloat, String stockHome, boolean xgimi, boolean voiceKeyService, boolean legacyXgimi) {
            this(label, debloat, stockHome, xgimi, voiceKeyService, legacyXgimi, null);
        }

        Device(String label, String debloat, String stockHome, boolean xgimi, boolean voiceKeyService, boolean legacyXgimi,
               String overlays) {
            this.label = label;
            this.debloat = debloat;
            this.stockHome = stockHome;
            this.xgimi = xgimi;
            this.voiceKeyService = voiceKeyService;
            this.legacyXgimi = legacyXgimi;
            this.overlays = overlays;
        }
    }

    /**
     * Firmwares with ready-built translation overlays (tools\xgimi-vi\rs10-overlays.ps1 -Device ...): XGIMI RS10
     * (China market, Android 9, m7642) and the XGIMI models on the IRONMAN firmware (Android 8.0, MSD848).
     */
    private static final String RS10_DEVICE = "m7642";
    private static final String RS10_OVERLAYS = "rs10-vi";
    private static final String IRONMAN_DEVICE = "ironman";
    private static final String IRONMAN_OVERLAYS = "ironman-vi";

    /** Package names of the overlays in an assets folder: "rs10-vi" holds vn.cleantv.overlay.rs10.<app>. */
    private static String overlayPrefix(String dir) {
        return "vn.cleantv.overlay." + dir.substring(0, dir.length() - "-vi".length()) + ".";
    }

    private static final String SETUP_WIZARD = "com.google.android.tungsten.setupwraith";
    /** Android 6 MStar XGIMI (synsepalum_YN): its stock home, and where the Settings translation overlay goes. */
    private static final String LEGACY_DEVICE = "synsepalum_YN";
    private static final String LEGACY_HOME = "com.xgimi.overseashome";
    private static final String LEGACY_OVERLAY = "/system/vendor/overlay/XgimiSettingsVi.apk";

    /** The update steps use `cmd` (Android 8+); older TVs work only with a profile of their own (legacyXgimi). */
    private static final int CMD_MIN_SDK = 26;

    private static Device detect(LocalAdb adb) throws IOException {
        // The XGIMI DOHKO build (USB-CapNhat-XGIMI-DOHKO) stops here on anything else, Android 6 XGIMI included,
        // before changing a thing
        if (BuildConfig.XGIMI_ONLY && !"horse_popular".equals(adb.shell("getprop ro.product.device"))) {
            String who = adb.shell("getprop ro.product.model") + ", Android " + adb.shell("getprop ro.build.version.release");
            throw new IllegalStateException("bản này chỉ dành cho máy chiếu XGIMI DOHKO (Android 14); máy này: " + who
                    + ". Dùng gói USB-CapNhat chung. Không thay đổi gì trên máy này");
        }
        if (LEGACY_DEVICE.equals(adb.shell("getprop ro.product.device"))) {
            return new Device("máy chiếu XGIMI Android 6", null, LEGACY_HOME, true, false, true);
        }
        int sdk;
        try {
            sdk = Integer.parseInt(adb.shell("getprop ro.build.version.sdk").trim());
        } catch (NumberFormatException e) {
            sdk = 0;
        }
        if (sdk < CMD_MIN_SDK) {
            throw new IllegalStateException("máy chạy Android " + adb.shell("getprop ro.build.version.release")
                    + " và chưa có hồ sơ riêng trong ứng dụng này. Không thay đổi gì trên máy này");
        }
        String model = adb.shell("getprop ro.product.model");
        // The XGIMI removal list was checked on the DOHKO (GMUI, Android 14) only; other XGIMI models just get the launcher
        if (!adb.shell("pm path " + STOCK_HOME).isEmpty() && "horse_popular".equals(adb.shell("getprop ro.product.device"))) {
            // XGIMI's own voice assistant is off there; the launcher's key service takes the mic button
            return new Device("máy chiếu XGIMI", "debloat.txt", STOCK_HOME, true, true);
        }
        if (RS10_DEVICE.equals(adb.shell("getprop ro.product.device")) && !adb.shell("pm path " + STOCK_HOME).isEmpty()) {
            // No removal list yet and XGIMI's home stays; Android 9 has no fabricated overlays, so the Vietnamese
            // text comes as installed overlay APKs
            return new Device("máy chiếu XGIMI RS10 (Android 9)", null, null, false, false, false, RS10_OVERLAYS);
        }
        if (IRONMAN_DEVICE.equals(adb.shell("getprop ro.product.device")) && !adb.shell("pm path " + STOCK_HOME).isEmpty()) {
            // One firmware for many XGIMI models (vendor/etc/xgimimodelname); same approach as the RS10
            return new Device("máy chiếu XGIMI (firmware IRONMAN, Android 8)", null, null, false, false, false, IRONMAN_OVERLAYS);
        }
        if ("onn".equalsIgnoreCase(adb.shell("getprop ro.product.manufacturer"))
                || "YOC".equals(adb.shell("getprop ro.product.device"))) {
            // com.gretzky.GlobalKey keeps the remote's mic (Google Assistant) and app buttons: no key service
            return new Device("onn 4K Streaming Box", "debloat-onn.txt",
                    "com.google.android.apps.tv.launcherx", false, false);
        }
        String label = model.toUpperCase(java.util.Locale.ROOT).contains("XGIMI")
                ? "XGIMI đời khác (chưa có danh sách tắt riêng)" : "máy chưa có hồ sơ riêng";
        return new Device(label, null, null, false, false);
    }

    /** Home has an "always" preference for the new launcher (made by the chooser's "始终"). */
    private boolean homeAlways(LocalAdb adb) throws IOException {
        return adb.shell("dumpsys package preferred-activities | grep -A1 '" + LAUNCHER + "/.MainActivity' | grep -c 'mAlways=true'")
                .trim().matches("[1-9][0-9]*");
    }

    /**
     * Makes the "always" Home preference the way a person would: opens Home, and in XGIMI's chooser picks the new
     * launcher, then "始终" (always), with remote keys. Brings this app back to the front afterwards.
     */
    private boolean chooseHomeAlways(LocalAdb adb) throws Exception {
        String label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(LAUNCHER, 0)).toString();
        adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
        Thread.sleep(2500);
        try {
            if (!adb.shell("dumpsys window | grep mCurrentFocus").contains("ResolverActivity")) return homeAlways(adb);
            // The chooser's items in order: the n-th needs n+1 Right presses (the first one only enters the grid)
            String ui = adb.shell("uiautomator dump /sdcard/cleantv-ui.xml >/dev/null; cat /sdcard/cleantv-ui.xml; rm -f /sdcard/cleantv-ui.xml");
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("text=\"([^\"]*)\" resource-id=\"com\\.xgimi\\.xgimiframeworkui:id/text\"").matcher(ui);
            int index = -1;
            for (int i = 0; m.find(); i++) if (m.group(1).equals(label)) { index = i; break; }
            if (index < 0) return false;
            for (int i = 0; i <= index; i++) adb.shell("input keyevent KEYCODE_DPAD_RIGHT; sleep 0.5");
            adb.shell("input keyevent KEYCODE_DPAD_CENTER; sleep 1");
            adb.shell("input keyevent KEYCODE_DPAD_DOWN; sleep 0.5; input keyevent KEYCODE_DPAD_DOWN; sleep 0.5");
            String focus = adb.shell("uiautomator dump /sdcard/cleantv-ui.xml >/dev/null; grep -o 'resource-id=\"[^\"]*\"[^>]*focused=\"true\"' /sdcard/cleantv-ui.xml; rm -f /sdcard/cleantv-ui.xml");
            if (!focus.contains("btn_always")) {
                adb.shell("input keyevent KEYCODE_BACK");
                return false;
            }
            adb.shell("input keyevent KEYCODE_DPAD_CENTER");
            Thread.sleep(1500);
            return homeAlways(adb);
        } finally {
            // Back to this app's log, above the launcher the choice has just opened
            adb.shell("am start -f 0x20000 -n " + getPackageName() + "/" + UpdaterActivity.class.getName());
        }
    }

    /** The installed launcher is byte for byte the one in this app's assets. */
    private boolean launcherUpToDate(LocalAdb adb) {
        try {
            String path = adb.shell("pm path " + LAUNCHER).replace("package:", "").split("\n")[0].trim();
            if (path.isEmpty()) return false;
            String installed = adb.shell("md5sum " + path).split("\\s+")[0];
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            try (InputStream in = getAssets().open("tvhome.apk")) {
                byte[] buf = new byte[64 * 1024];
                for (int r; (r = in.read(buf)) > 0; ) md.update(buf, 0, r);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format("%02x", b));
            return hex.toString().equalsIgnoreCase(installed);
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * After the launcher was replaced, the TV may be showing its home chooser (XGIMI's home is kept enabled):
     * close it with Back, so the screen returns to this app and "Trở về" opens the new launcher.
     */
    private void closeHomeChooser(LocalAdb adb) throws IOException {
        for (int i = 0; i < 3; i++) {
            if (!adb.shell("dumpsys window | grep mCurrentFocus").contains("ResolverActivity")) return;
            adb.shell("input keyevent KEYCODE_BACK");
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private String homeNow(LocalAdb adb) throws IOException {
        String resolved = last(adb.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"));
        // With two homes enabled (XGIMI's is kept) the query answers with the chooser, while Home and boot follow
        // the HOME role holder (Android 10+): report that one then
        if (resolved.contains("ResolverActivity")) {
            String role = last(adb.shell("cmd role get-role-holders --user 0 android.app.role.HOME 2>/dev/null"));
            if (role.matches("[A-Za-z0-9_.]+")) return role;
        }
        return resolved;
    }

    private void say(String line) {
        android.util.Log.i("CapNhat", line);   // the same log in logcat, for checking a TV from a PC
        ui.post(() -> {
            log.append(line + "\n");
            // scrollTo, not fullScroll: fullScroll also moves focus into the log, away from "Trở về"
            scroll.post(() -> scroll.scrollTo(0, Math.max(0, log.getHeight() - scroll.getHeight())));
        });
    }

    // ---------- Update ----------
    private void doUpdate(LocalAdb adb) throws Exception {
        Device device = detect(adb);
        say("Máy: " + adb.shell("getprop ro.product.model") + "  (firmware " + adb.shell("getprop ro.build.version.incremental")
                + ")  —  " + device.label);
        File dir = getExternalFilesDir(null);
        if (dir == null) throw new IOException("không có bộ nhớ trong");
        if (device.legacyXgimi) {
            doUpdateLegacy(adb, dir);
            return;
        }

        say("");
        say("== 1/3  Cài launcher mới nhất");
        // Replacing the launcher leaves the TV without a home for a moment, and with XGIMI's home kept enabled
        // Android then opens its home chooser: install only when the launcher actually changes
        boolean fresh = !launcherUpToDate(adb);
        String out;
        if (fresh) {
            // Streamed through ADB: a non-root adbd (onn) cannot read a file in this app's storage
            long size = getAssets().openFd("tvhome.apk").getLength();
            try (InputStream in = getAssets().open("tvhome.apk")) {
                out = adb.exec("cmd package install -r -S " + size, in);
            }
            if (!out.contains("Success")) throw new IllegalStateException("cài launcher thất bại: " + firstLine(out));
        } else {
            say("   OK  launcher trên máy đã là bản mới nhất (không cài lại)");
        }
        // Not on XGIMI: there set-home-activity can replace the chooser's "始终" preference (step 2 keeps that one)
        if (!device.xgimi) adb.shell("cmd package set-home-activity --user 0 " + LAUNCHER + "/.MainActivity");
        adb.shell("pm grant " + LAUNCHER + " android.permission.ACCESS_FINE_LOCATION");
        adb.shell("pm grant " + LAUNCHER + " android.permission.RECORD_AUDIO");   // voice search pill
        // Lets the launcher switch ADB back on later (keep_adb_on, and this app's request when ADB is off)
        adb.shell("pm grant " + LAUNCHER + " android.permission.WRITE_SECURE_SETTINGS");
        keepAdbOn(adb);
        if (device.voiceKeyService) {
            // The remote's microphone button opens voice search from any app: enable the launcher's key service
            adb.shell("appops set " + LAUNCHER + " ACCESS_RESTRICTED_SETTINGS allow");
            String acc = adb.shell("settings get secure enabled_accessibility_services").trim();
            if (!acc.contains("InputKeyService")) {
                acc = acc.isEmpty() || acc.equals("null") ? LAUNCHER + "/.InputKeyService" : acc + ":" + LAUNCHER + "/.InputKeyService";
                adb.shell("settings put secure enabled_accessibility_services " + acc);
                adb.shell("settings put secure accessibility_enabled 1");
            }
        } else {
            say("   phím micro trên remote giữ nguyên chức năng của máy");
        }
        if (fresh) {
            say("   đang tối ưu launcher…");
            adb.shell("cmd package compile -m speed -f " + LAUNCHER);
            closeHomeChooser(adb);
        }
        say("   OK  launcher " + adb.shell("dumpsys package " + LAUNCHER + " 2>/dev/null | grep -m1 versionName").replaceAll(".*=", ""));

        say("");
        say("== 2/3  Tắt quảng cáo và ứng dụng thừa");
        // Google TV: the Wi-Fi network saved during first setup belongs to the setup wizard, and Android deletes
        // it the moment the wizard is disabled. Then the wizard, and the stock home it would re-enable, stay on.
        // Only the network in use matters ("* ID" marks the connected one); the wizard's other saved networks go.
        // Parsed here rather than with grep -B/-A, which some TVs' toybox grep does not honour.
        boolean wifiOwnedByWizard = false;
        java.util.Set<String> wizardSsids = new java.util.TreeSet<>();
        String ssid = null;
        boolean connected = false;
        for (String line : adb.shell("dumpsys wifi | grep -E '^\\* ID: |^ID: |cname='").split("\n")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("SSID: (\"[^\"]*\")").matcher(line);
            if (line.startsWith("* ID: ") || line.startsWith("ID: ")) {
                ssid = m.find() ? m.group(1) : null;
                connected = line.startsWith("* ID: ");
            } else if (ssid != null && line.contains("cname=" + SETUP_WIZARD + " ")) {
                wizardSsids.add(ssid);
                if (connected) wifiOwnedByWizard = true;
                ssid = null;
            } else if (line.contains("cname=")) {
                ssid = null;   // the networks' block ends at their first owner line; later lists repeat them
            }
        }
        String wizardNetworks = android.text.TextUtils.join(" ", wizardSsids);   // String.join needs Android 8
        boolean wizardLeft = false;
        if (device.debloat == null) {
            say("   bỏ qua: chưa có danh sách tắt cho máy này (chỉ cài launcher)");
        } else {
            String disabled = adb.shell("pm list packages -d") + "\n";
            int n = 0;
            for (String p : readList(device.debloat)) {
                if (disabled.contains("package:" + p + "\n")) continue;
                if (adb.shell("pm path " + p).isEmpty()) continue;
                if (p.equals(SETUP_WIZARD) && wifiOwnedByWizard) { wizardLeft = true; continue; }
                if (p.equals(SETUP_WIZARD) && !wizardNetworks.isEmpty()) {
                    say("   (mạng Wi-Fi cũ không dùng sẽ bị xoá cùng trình thiết lập: " + wizardNetworks + ")");
                }
                // one pm command per shell: chained pm calls can stop after the first
                String r = adb.shell("pm disable-user --user 0 " + p);
                if (r.contains("disabled")) { n++; say("   tắt  " + p); } else say("   !!  " + p + ": " + r);
            }
            say(n > 0 ? "   OK  đã tắt thêm " + n + " gói" : "   OK  các gói đã tắt từ trước");
        }
        // XGIMI's home stays enabled (XGIMI features call it; disabling it caused errors): earlier updates turned
        // it off, so turn it back on. The new launcher still takes Home (below: the "始终" preference).
        if (device.xgimi && device.stockHome != null
                && (adb.shell("pm list packages -d") + "\n").contains("package:" + device.stockHome + "\n")) {
            adb.shell("pm enable --user 0 " + device.stockHome);
            say("   bật lại  " + device.stockHome + " (giao diện XGIMI, giữ để tránh lỗi)");
        }
        // Elsewhere the stock home goes last, once the new launcher is known to start
        if (device.stockHome != null && !device.xgimi && !adb.shell("pm path " + device.stockHome).isEmpty()
                && !(adb.shell("pm list packages -d") + "\n").contains("package:" + device.stockHome + "\n")) {
            if (wizardLeft) {
                say("   !!  Wi-Fi của máy do trình thiết lập Google giữ: chưa tắt màn hình chính cũ,");
                say("       vì tắt lúc này máy sẽ mất Wi-Fi. Vào Cài đặt > Mạng, quên mạng Wi-Fi rồi");
                say("       kết nối lại (nhập lại mật khẩu), sau đó bấm Cập nhật lần nữa.");
            } else if (!adb.shell("am start -W -n " + LAUNCHER + "/.MainActivity").contains("Status: ok")) {
                say("   !!  launcher mới chưa khởi động được: giữ màn hình chính cũ");
            } else {
                adb.shell("pm disable-user --user 0 " + device.stockHome);
                say("   tắt  " + device.stockHome + " (màn hình chính cũ)");
            }
        }
        if (device.xgimi) {
            // XGIMI's home stays enabled. On this firmware set-home-activity writes a Home preference without
            // "always", which replaces the one the chooser's "始终" made, and Home then shows the chooser
            // again: keep an "always" preference, or make one through the chooser itself
            // No homeNow() here: its `cmd package resolve-activity` turns that preference back to "not always"
            if (homeAlways(adb) || chooseHomeAlways(adb)) {
                say("   OK  nút Home mở thẳng launcher mới (không hiện hộp chọn)");
            } else {
                say("   !!  chưa đặt được: bấm Home, chọn \"Màn hình chính\" rồi bấm \"始终\" (luôn chọn) một lần");
            }
        } else {
            adb.shell("cmd package set-home-activity --user 0 " + LAUNCHER + "/.MainActivity");
            String home = homeNow(adb);
            say(home.contains(LAUNCHER) ? "   OK  nút Home mở launcher mới" : "   !!  nút Home đang mở: " + home);
        }

        say("");
        say("== 3/3  Việt hoá Cài đặt XGIMI");
        if (device.overlays != null) {
            installOverlays(adb, device.overlays);
        } else if (!device.xgimi) {
            say("   bỏ qua: chỉ dành cho máy chiếu XGIMI");
        } else if (!"0".equals(adb.shell("id -u"))) {
            say("   !!  ADB không có quyền root: bỏ qua bước này.");
        } else {
            File xml = copyAsset("vi.xml", new File(dir, "vi.xml"));
            // system_server opens the file for fabricate and cannot see this app's folder: hand it over via /data/local/tmp
            String tmp = "/data/local/tmp/xgimi-vi.xml";
            adb.shell("cp " + xml.getAbsolutePath() + " " + tmp);
            if (adb.shell("cmd overlay list " + SETTINGS).contains(OVERLAY)) adb.shell("cmd overlay disable --user 0 " + OVERLAY);
            out = adb.shell("cmd overlay fabricate --target " + SETTINGS + " --name VietHoa --file " + tmp);
            adb.shell("rm -f " + tmp);
            xml.delete();
            if (!out.isEmpty()) say("   " + out);
            adb.shell("cmd overlay enable --user 0 " + OVERLAY);
            adb.shell("am force-stop " + SETTINGS);
            // Proof that the new text is live: look one string up through the overlay
            String net = adb.shell("cmd overlay lookup " + SETTINGS + " " + SETTINGS + ":string/head_network_setting_text");
            say(out.isEmpty() && net.contains("Mạng") ? "   OK  đã Việt hoá" : "   !!  Việt hoá chưa áp dụng được (" + net + ")");
            keepVietnameseAfterBoot(adb, dir);
        }
        closeHomeChooser(adb);
        say("");
        say("Xong. Bấm \"Trở về\" (hoặc Home) để về màn hình chính mới.");
    }

    /**
     * Android 8 and 9 have no `cmd overlay fabricate`, but overlays installed like apps can be enabled by the shell there:
     * installs each assets/<dir>/VietHoa-<target>.apk whose target is on this TV, enables it and restarts the
     * apps on screen most. Installed overlays survive reboots; Restore uninstalls them.
     */
    private void installOverlays(LocalAdb adb, String dir) throws IOException {
        String[] apks = getAssets().list(dir);
        if (apks == null || apks.length == 0) {
            say("   !!  bản cập nhật này không có file dịch cho máy này");
            return;
        }
        int ok = 0, present = 0;
        for (String apk : apks) {
            if (!apk.startsWith("VietHoa-") || !apk.endsWith(".apk")) continue;
            String target = apk.substring("VietHoa-".length(), apk.length() - ".apk".length());
            if (adb.shell("pm path " + target).isEmpty()) continue;
            present++;
            String pkg = overlayPrefix(dir) + target.substring(target.lastIndexOf('.') + 1);
            long size = getAssets().openFd(dir + "/" + apk).getLength();
            String out;
            try (InputStream in = getAssets().open(dir + "/" + apk)) {
                out = adb.exec("cmd package install -r -S " + size, in);
            }
            if (!out.contains("Success")) {
                say("   !!  " + target + ": " + firstLine(out));
                continue;
            }
            adb.shell("cmd overlay enable --user 0 " + pkg);
            if (adb.shell("cmd overlay list " + target).contains("[x] " + pkg)) ok++;
            else say("   !!  " + target + ": đã cài nhưng chưa bật được");
        }
        for (String p : new String[]{SETTINGS, STOCK_HOME, "com.xgimi.shutdown"}) adb.shell("am force-stop " + p);
        say(ok == present && ok > 0 ? "   OK  đã Việt hoá " + ok + " ứng dụng XGIMI (Cài đặt, màn hình chính, thanh hệ thống…)"
                : "   !!  mới Việt hoá được " + ok + "/" + present + " ứng dụng");
    }

    /**
     * The DOHKO drops fabricated overlays at every boot. The Settings and power menu translations and
     * tools/xgimi-vi/dohko-boot.sh go to /data/local/tmp/cleantv, owned by root, and boot_script tells the
     * launcher to run boot.sh through the TV's adbd after each boot (BootScript); run once here for the power menu.
     */
    private void keepVietnameseAfterBoot(LocalAdb adb, File dir) throws IOException {
        String target = "/data/local/tmp/cleantv";
        adb.shell("rm -rf " + target + "; mkdir -p " + target);
        java.util.List<String[]> files = new java.util.ArrayList<>(java.util.Arrays.asList(new String[][]{
                {"vi.xml", "settings-vi.xml"}, {"shutdown-vi.xml", "shutdown-vi.xml"}, {"boot.sh", "boot.sh"}}));
        // The other XGIMI apps seen on screen (HDMI, SystemUI, installer, focus, Cua than ky...): ov-<package>.xml
        String[] apps = getAssets().list("apps");
        if (apps != null) for (String a : apps) files.add(new String[]{"apps/" + a, a});
        for (String[] f : files) {
            File local = copyAsset(f[0], new File(dir, f[1]));
            adb.shell("cp " + local.getAbsolutePath() + " " + target + "/" + f[1]);
            local.delete();
        }
        int expected = 2 + (apps == null ? 0 : apps.length);
        // Root-owned so apps cannot change what the boot script runs; system_server reads the translations
        adb.shell("chown -R root:root " + target + "; chmod 755 " + target + "; chmod 644 " + target + "/*.xml; chmod 700 " + target + "/boot.sh");
        Boolean set = putLauncherConfig(adb, "boot_script", true);
        String count = last(adb.shell("sh " + target + "/boot.sh"));
        String power = adb.shell("cmd overlay lookup com.xgimi.shutdown com.xgimi.shutdown:string/power_off");
        say(power.contains("Tắt máy") ? "   OK  menu tắt máy: tiếng Việt" : "   !!  menu tắt máy chưa Việt hoá được (" + power + ")");
        if (expected > 2) {
            say(String.valueOf(expected).equals(count) ? "   OK  Việt hoá thêm " + (expected - 2) + " ứng dụng XGIMI (HDMI, thanh hệ thống, cài ứng dụng…)"
                    : "   !!  mới Việt hoá được " + count + "/" + expected + " phần");
        }
        say(set != null && String.valueOf(expected).equals(count) ? "   OK  giữ tiếng Việt sau mỗi lần khởi động lại"
                : "   !!  chưa đặt được tự Việt hoá sau khởi động (" + count + ")");
    }

    /**
     * The Android 6 MStar XGIMI (examples/xgimi-synsepalum-android6): no `cmd`, and its stock home runs as system,
     * so ADB can neither disable nor hide it. The launcher takes Home through HomeKeyService
     * (home_redirect_package), and Settings gets its Vietnamese text from a static overlay that needs root, which
     * this userdebug firmware gives adbd. Both the overlay and the language take a reboot, done at the end.
     */
    private void doUpdateLegacy(LocalAdb adb, File dir) throws Exception {
        say("");
        say("== 1/3  Cài launcher mới nhất");
        // Android 6 pm has no -S (stdin): it installs from a path, and the shell user (group sdcard_rw) can read
        // this app's external folder there
        File launcherApk = copyAsset("tvhome.apk", new File(dir, "tvhome.apk"));
        String out = adb.shell("pm install -r " + launcherApk.getAbsolutePath());
        launcherApk.delete();
        if (!out.contains("Success")) throw new IllegalStateException("cài launcher thất bại: " + firstLine(out));
        adb.shell("pm grant " + LAUNCHER + " android.permission.ACCESS_FINE_LOCATION");
        adb.shell("pm grant " + LAUNCHER + " android.permission.RECORD_AUDIO");
        adb.shell("pm grant " + LAUNCHER + " android.permission.WRITE_SECURE_SETTINGS");
        keepAdbOn(adb);   // this firmware switches network ADB off at every boot; the launcher turns it back on
        say("   OK  launcher " + adb.shell("dumpsys package " + LAUNCHER + " 2>/dev/null | grep -m1 versionName").replaceAll(".*=", ""));

        say("");
        say("== 2/3  Màn hình chính");
        say("   bỏ qua tắt ứng dụng: chưa có danh sách cho máy này");
        Boolean set = putLauncherConfig(adb, "home_redirect_package", LEGACY_HOME);
        String acc = adb.shell("settings get secure enabled_accessibility_services").trim();
        if (!acc.contains("HomeKeyService")) {
            acc = acc.isEmpty() || acc.equals("null") ? LAUNCHER + "/.HomeKeyService" : acc + ":" + LAUNCHER + "/.HomeKeyService";
            adb.shell("settings put secure enabled_accessibility_services " + acc);
        }
        adb.shell("settings put secure accessibility_enabled 1");
        adb.shell("am start -n " + LAUNCHER + "/.MainActivity");
        say(set != null ? "   OK  nút Home mở launcher mới (màn hình XGIMI vẫn còn, launcher tự mở đè lên)"
                : "   !!  config.json của launcher bị lỗi: nút Home vẫn mở màn hình XGIMI");

        say("");
        say("== 3/3  Việt hoá Cài đặt XGIMI");
        LocalAdb root = adb;
        if (!"0".equals(adb.shell("id -u"))) {
            say("   chuyển ADB sang quyền root…");
            adb.restartAsRoot();
            try {
                root = reopen();
            } catch (LocalAdb.AuthRequired e) {
                throw e;
            } catch (IOException e) {
                say("   !!  không kết nối lại được ADB sau khi chuyển quyền root (" + e.getMessage() + ")");
                say("       Nếu máy tính đang kết nối ADB tới máy chiếu, ngắt kết nối đó rồi bấm Cập nhật lần nữa.");
                return;
            }
            if (!"0".equals(root.shell("id -u"))) {
                root.close();
                say("   !!  không lấy được quyền root: bỏ qua bước này");
                say("");
                say("Xong. Bấm Home để về màn hình chính mới.");
                return;
            }
        }
        try {
            boolean reboot = false;
            File apk = copyAsset("xgimi-settings-vi-a6.apk", new File(dir, "xgimi-settings-vi-a6.apk"));
            String sums = root.shell("md5sum " + apk.getAbsolutePath() + " " + LEGACY_OVERLAY + " 2>/dev/null");
            String[] lines = sums.split("\n");
            boolean same = lines.length == 2 && lines[0].split(" ")[0].equals(lines[1].split(" ")[0]);
            if (same) {
                say("   OK  Cài đặt đã có tiếng Việt từ trước");
            } else {
                root.shell("mount -o rw,remount /system");
                root.shell("mkdir -p /system/vendor/overlay; chmod 755 /system/vendor/overlay");
                root.shell("cp " + apk.getAbsolutePath() + " " + LEGACY_OVERLAY);
                root.shell("chmod 644 " + LEGACY_OVERLAY + "; chown root:root " + LEGACY_OVERLAY + "; sync");
                root.shell("mount -o ro,remount /system");
                boolean ok = root.shell("ls " + LEGACY_OVERLAY).contains(LEGACY_OVERLAY);
                say(ok ? "   OK  đã chép bản dịch Cài đặt" : "   !!  không chép được bản dịch Cài đặt");
                reboot = ok;
            }
            apk.delete();
            // "vi" or "vi-VN" (set by another app, e.g. HomeTV DinhLap on 192.168.31.150): both are Vietnamese already
            if (!root.shell("getprop persist.sys.locale").startsWith("vi")) {
                root.shell("setprop persist.sys.locale vi");
                say("   OK  ngôn ngữ hệ thống: tiếng Việt");
                reboot = true;
            }
            say("");
            if (reboot) {
                say("Xong. Máy sẽ khởi động lại sau 10 giây để áp dụng tiếng Việt.");
                Thread.sleep(10_000);
                try {
                    root.shell("reboot");
                } catch (IOException goingDown) {
                    // adbd stops with the system: expected
                }
            } else {
                say("Xong. Bấm Home để về màn hình chính mới.");
            }
        } finally {
            if (root != adb) root.close();
        }
    }

    /** Undoes doUpdateLegacy: XGIMI's home is Home again and the Settings translation goes (after a reboot). */
    private void doRestoreLegacy(LocalAdb adb) throws Exception {
        say("== Trả màn hình chính XGIMI");
        String acc = adb.shell("settings get secure enabled_accessibility_services").trim();
        String rest = acc.replace(LAUNCHER + "/.HomeKeyService", "").replace("::", ":").replaceAll("^:|:$", "");
        adb.shell("settings put secure enabled_accessibility_services '" + rest + "'");
        putLauncherConfig(adb, "home_redirect_package", "");
        say("   OK");
        say("== Bỏ Việt hoá Cài đặt");
        LocalAdb root = adb;
        if (!"0".equals(adb.shell("id -u"))) {
            adb.restartAsRoot();
            root = reopen();
        }
        try {
            if (!root.shell("ls " + LEGACY_OVERLAY + " 2>/dev/null").contains(LEGACY_OVERLAY)) {
                say("   OK  không có bản dịch để bỏ");
                say("");
                say("Đã khôi phục. Launcher mới vẫn còn; gỡ trong Cài đặt > Ứng dụng nếu muốn.");
                return;
            }
            root.shell("mount -o rw,remount /system");
            root.shell("rm -f " + LEGACY_OVERLAY + "; rm -f /data/resource-cache/*XgimiSettingsVi*; sync");
            root.shell("mount -o ro,remount /system");
            say("   OK  (ngôn ngữ vẫn là tiếng Việt; đổi bằng MoreLocale nếu muốn)");
            say("");
            say("Đã khôi phục. Máy sẽ khởi động lại sau 10 giây.");
            Thread.sleep(10_000);
            try {
                root.shell("reboot");
            } catch (IOException goingDown) {
                // expected
            }
        } finally {
            if (root != adb) root.close();
        }
    }

    // ---------- Bat ADB ----------
    /**
     * "Bật ADB": run() has already connected, through the launcher when ADB was off (connect()). Here ADB is on:
     * make sure it stays on, so the button also repairs a launcher installed before keep_adb_on existed.
     */
    private void doEnableAdb(LocalAdb adb) throws Exception {
        say("== Bật ADB");
        say("   OK  ADB đang bật (máy " + adb.shell("getprop ro.product.model") + ")");
        if (adb.shell("pm path " + LAUNCHER).isEmpty()) {
            say("   !!  máy chưa có launcher mới: bấm \"Cập nhật\" để ADB tự bật lại sau mỗi lần khởi động");
            return;
        }
        adb.shell("pm grant " + LAUNCHER + " android.permission.WRITE_SECURE_SETTINGS");
        keepAdbOn(adb);
        say("");
        say("Xong. ADB sẽ tự bật lại mỗi lần máy khởi động.");
    }

    // ---------- Restore ----------
    private void doRestore(LocalAdb adb) throws Exception {
        Device device = detect(adb);
        say("Máy: " + adb.shell("getprop ro.product.model") + "  —  " + device.label);
        if (device.legacyXgimi) {
            doRestoreLegacy(adb);
            return;
        }
        say("== Bật lại ứng dụng đã tắt");
        if (device.debloat != null) for (String p : readList(device.debloat)) adb.shell("pm enable --user 0 " + p);
        if (device.stockHome != null) {
            adb.shell("pm enable --user 0 " + device.stockHome);
            String stock = device.xgimi ? STOCK_HOME_ACTIVITY : last(adb.shell(
                    "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME "
                    + device.stockHome));
            if (stock.startsWith(device.stockHome + "/")) adb.shell("cmd package set-home-activity --user 0 " + stock);
        }
        say("   OK  nút Home mở: " + homeNow(adb));
        if (device.overlays != null) {
            say("== Bỏ Việt hoá");
            int n = 0;
            for (String line : adb.shell("pm list packages " + overlayPrefix(device.overlays)).split("\n")) {
                String pkg = line.trim().replace("package:", "");
                if (pkg.isEmpty()) continue;
                adb.shell("pm uninstall " + pkg);
                n++;
            }
            for (String p : new String[]{SETTINGS, STOCK_HOME, "com.xgimi.shutdown"}) adb.shell("am force-stop " + p);
            say("   OK  đã gỡ " + n + " file dịch");
        }
        if (device.xgimi) {
            say("== Bỏ Việt hoá Cài đặt");
            adb.shell("cmd overlay disable --user 0 " + OVERLAY);
            adb.shell("cmd overlay disable --user 0 com.android.shell:VietHoaTatMay");
            // the other XGIMI apps' translations (VietHoa_<app>)
            adb.shell("for o in $(cmd overlay list | grep -o 'com.android.shell:VietHoa_[A-Za-z0-9_]*'); do cmd overlay disable --user 0 $o; done");
            // and the boot script that would bring both back (keepVietnameseAfterBoot)
            adb.shell("rm -rf /data/local/tmp/cleantv");
            putLauncherConfig(adb, "boot_script", false);
            adb.shell("rm -f /data/resource-cache/*VietHoa*");
            adb.shell("am force-stop " + SETTINGS);
            adb.shell("am force-stop com.xgimi.shutdown");
            say("   OK");
        }
        say("");
        say("Đã khôi phục. Launcher mới vẫn còn; gỡ trong Cài đặt > Ứng dụng nếu muốn.");
    }

    /**
     * Turns on the launcher's keep_adb_on (AdbKeeper) in its config.json, keeping every other key the editor or a
     * theme set, so ADB comes back on after each reboot and this app can always connect.
     */
    private void keepAdbOn(LocalAdb adb) throws IOException {
        Boolean changed = putLauncherConfig(adb, "keep_adb_on", true);
        if (changed == null) say("   !!  config.json của launcher bị lỗi: không bật tự giữ ADB");
        else say(changed ? "   OK  launcher sẽ tự bật lại ADB khi khởi động" : "   OK  launcher đã tự bật lại ADB khi khởi động");
    }

    /**
     * Sets one key of the launcher's config.json, keeping every other key the editor or a theme set. Returns
     * false when it already had that value, true once written, null when the file is broken or the write failed.
     */
    private Boolean putLauncherConfig(LocalAdb adb, String key, Object value) throws IOException {
        String dir = "/sdcard/Android/data/" + LAUNCHER + "/files";
        String raw = adb.shell("cat " + dir + "/config.json 2>/dev/null");
        JSONObject cfg;
        try {
            cfg = raw.startsWith("{") ? new JSONObject(raw) : new JSONObject();
            if (value.equals(cfg.opt(key))) return false;
            cfg.put(key, value);
        } catch (JSONException e) {
            return null;
        }
        File local = new File(getExternalFilesDir(null), "config.json");
        try (OutputStream o = new FileOutputStream(local)) {
            o.write(cfg.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        local.setReadable(true, false);
        adb.shell("mkdir -p " + dir);
        adb.shell("cp " + local.getAbsolutePath() + " " + dir + "/config.json");
        adb.shell("chmod 644 " + dir + "/config.json");
        local.delete();
        return "1".equals(adb.shell("grep -c '\"" + key + "\"' " + dir + "/config.json")) ? Boolean.TRUE : null;
    }

    private File copyAsset(String name, File dest) throws IOException {
        try (InputStream in = getAssets().open(name); OutputStream o = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            for (int r; (r = in.read(buf)) > 0; ) o.write(buf, 0, r);
        }
        dest.setReadable(true, false);
        return dest;
    }

    private List<String> readList(String asset) throws IOException {
        List<String> items = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open(asset), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                int hash = line.indexOf('#');
                String p = (hash >= 0 ? line.substring(0, hash) : line).trim();
                if (!p.isEmpty()) items.add(p);
            }
        }
        return items;
    }

    /** First line only: a failed pm command prints a whole Java stack trace after its message. */
    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return (nl < 0 ? s : s.substring(0, nl)).trim();
    }

    private static String last(String s) {
        String[] lines = s.split("\n");
        return lines[lines.length - 1].trim();
    }
}
