package vn.cleantv.updater;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Phone build: looks for a newer build of this app at BuildConfig.UPDATE_URL (version.json on the latest GitHub
 * Release, written by updater\build-phone.ps1) and installs it through PackageInstaller, which asks the user to
 * confirm. Nothing is sent but the GET requests.
 */
final class Updates {
    /** Sent back to UpdaterActivity by PackageInstaller with the session's status. */
    static final String ACTION_INSTALL_STATUS = "vn.cleantv.updater.INSTALL_STATUS";

    static final class Info {
        final int code;
        final String name, apk;

        Info(int code, String name, String apk) {
            this.code = code;
            this.name = name;
            this.apk = apk;
        }
    }

    interface Found { void newer(Info info); }

    interface Failed { void error(String why); }

    private Updates() {
    }

    /** Calls back (on a background thread) only when the release is newer than this build; quiet offline. */
    static void check(Activity activity, Found found) {
        if (BuildConfig.UPDATE_URL.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject v = new JSONObject(new String(get(BuildConfig.UPDATE_URL, 64 * 1024), StandardCharsets.UTF_8));
                Info info = new Info(v.getInt("versionCode"), v.optString("versionName", ""), v.getString("apk"));
                if (info.code > BuildConfig.VERSION_CODE && info.apk.startsWith("https://")) found.newer(info);
            } catch (IOException | JSONException e) {
                android.util.Log.i("CapNhat", "update check: " + e);
            }
        }, "update-check").start();
    }

    /** Downloads the release APK into an install session and commits it; the system shows its install prompt. */
    static void install(Activity activity, Info info, Failed failed) {
        new Thread(() -> {
            try {
                byte[] apk = get(info.apk, 64 * 1024 * 1024);
                PackageInstaller pi = activity.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(activity.getPackageName());
                int id = pi.createSession(params);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    try (OutputStream o = s.openWrite("update.apk", 0, apk.length)) {
                        o.write(apk);
                        s.fsync(o);
                    }
                    Intent status = new Intent(activity, UpdaterActivity.class).setAction(ACTION_INSTALL_STATUS);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    s.commit(PendingIntent.getActivity(activity, id, status, flags).getIntentSender());
                }
            } catch (IOException | RuntimeException e) {
                failed.error(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }, "update-install").start();
    }

    /** PackageInstaller's answer: show its confirmation screen, or report the outcome. Returns a line for the log. */
    static String onStatus(Activity activity, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) activity.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) return "Đã cài bản mới.";
        return "!! Chưa cài được bản mới: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
    }

    private static byte[] get(String url, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(30_000);
        c.setInstanceFollowRedirects(true);   // GitHub release downloads redirect to its file host (https -> https)
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[64 * 1024];
                for (int r; (r = in.read(buf)) > 0; ) {
                    out.write(buf, 0, r);
                    if (out.size() > max) throw new IOException("tệp quá lớn");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
