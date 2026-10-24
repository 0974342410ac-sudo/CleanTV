package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * web_remote (config.xml / config.json): a remote control page for phones without our Android app, iPhone above
 * all, at http://<TV address>:8686 on the home network. Safari's "Add to Home Screen" turns it into an app icon.
 * The page asks for a 4-digit PIN that the TV shows (toast, and Settings menu > Remote điện thoại); a right PIN
 * gives the browser a token it keeps. Keys and text reach the TV through its own ADB (127.0.0.1:5555, LocalAdb
 * with this app's key; keep_adb_on keeps it on), as `cmd input` (Android 12+) or `input`.
 */
final class WebRemote {
    static final int PORT = 8686;
    private static final String TAG = "TvHome.WebRemote";
    private static final String PREFS = "web_remote";
    private static final int MAX_BODY = 4096;
    /** Wrong PINs before a pause, and how long the pause is: 4 digits must not be guessable by trying them all. */
    private static final int MAX_TRIES = 5;
    private static final long LOCK_MS = 60_000;

    private static WebRemote running;

    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();
    private final ExecutorService clients = Executors.newFixedThreadPool(4);
    /** One thread for ADB, so keys reach the TV in order over one connection. */
    private final ScheduledExecutorService adbThread = Executors.newSingleThreadScheduledExecutor();
    /** The connection closes after this long without keys: Android 6 adbd serves one network client at a time. */
    private static final long ADB_IDLE_SECONDS = 20;
    private ScheduledFuture<?> adbIdle;
    private final Set<String> tokens = Collections.synchronizedSet(new HashSet<>());
    private LocalAdb adb;
    private String input;
    private int wrongTries;
    private long lockedUntil;

    private WebRemote(Context ctx) {
        this.ctx = ctx;
        tokens.addAll(prefs().getStringSet("tokens", Collections.emptySet()));
    }

    /** Starts the server once per process (Home's onCreate). */
    static synchronized void start(Context context) {
        if (running != null) return;
        running = new WebRemote(context.getApplicationContext());
        Thread t = new Thread(running::serve, "web-remote");
        t.setDaemon(true);
        t.start();
    }

    /** The PIN shown on the TV; made once and kept, so a phone that knows it keeps working. */
    static String pin(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String pin = p.getString("pin", null);
        if (pin == null) {
            pin = String.format(java.util.Locale.ROOT, "%04d", new SecureRandom().nextInt(10000));
            p.edit().putString("pin", pin).apply();
        }
        return pin;
    }

    /** http://<Wi-Fi or Ethernet IPv4>:8686, or null without a network. */
    static String address() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return "http://" + a.getHostAddress() + ":" + PORT;
                }
            }
        } catch (IOException ignored) {
            // no network
        }
        return null;
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- HTTP ----------

    private void serve() {
        try (ServerSocket server = new ServerSocket(PORT)) {
            Log.i(TAG, "listening on " + PORT);
            while (true) {
                Socket s = server.accept();
                clients.execute(() -> handle(s));
            }
        } catch (IOException e) {
            Log.w(TAG, "server stopped: " + e);
        }
    }

    private void handle(Socket s) {
        try (Socket socket = s) {
            socket.setSoTimeout(10_000);
            InputStream in = socket.getInputStream();
            String requestLine = readLine(in);
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0], path = parts[1];
            Map<String, String> headers = new HashMap<>();
            for (String line; (line = readLine(in)) != null && !line.isEmpty(); ) {
                int colon = line.indexOf(':');
                if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT), line.substring(colon + 1).trim());
            }
            String body = "";
            int length = 0;
            try {
                length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (NumberFormatException ignored) {
            }
            if (length > MAX_BODY) {
                respond(socket, 413, "text/plain", "too large".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (length > 0) {
                byte[] b = new byte[length];
                int read = 0;
                while (read < length) {
                    int n = in.read(b, read, length - read);
                    if (n < 0) break;
                    read += n;
                }
                body = new String(b, 0, read, StandardCharsets.UTF_8);
            }
            route(socket, method, path, headers, form(body));
        } catch (IOException e) {
            Log.i(TAG, "client: " + e);
        }
    }

    private void route(Socket socket, String method, String path, Map<String, String> headers, Map<String, String> form)
            throws IOException {
        if ("GET".equals(method) && ("/".equals(path) || path.startsWith("/?") || "/index.html".equals(path))) {
            respond(socket, 200, "text/html; charset=utf-8", rawResource(R.raw.remote));
            return;
        }
        if ("GET".equals(method) && "/icon.png".equals(path)) {
            respond(socket, 200, "image/png", icon());
            return;
        }
        if ("GET".equals(method) && "/manifest.json".equals(path)) {
            String m = "{\"name\":\"Remote TV\",\"short_name\":\"Remote TV\",\"start_url\":\"/\",\"display\":\"standalone\","
                    + "\"background_color\":\"#0E1116\",\"theme_color\":\"#0E1116\","
                    + "\"icons\":[{\"src\":\"/icon.png\",\"sizes\":\"180x180\",\"type\":\"image/png\"}]}";
            respond(socket, 200, "application/manifest+json", m.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!"POST".equals(method)) {
            respond(socket, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        switch (path) {
            case "/api/showpin":
                toast("Mã remote điện thoại: " + pin(ctx));
                json(socket, "{\"ok\":true}");
                return;
            case "/api/pin":
                json(socket, checkPin(form.get("pin")));
                return;
            default:
                break;
        }
        // Everything else needs a token from a right PIN
        String token = headers.get("x-token");
        if (token == null || !tokens.contains(token)) {
            respond(socket, 401, "application/json", "{\"ok\":false,\"error\":\"pin\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        switch (path) {
            case "/api/hello":
                json(socket, "{\"ok\":true,\"name\":\"" + jsonText(android.os.Build.MODEL) + "\"}");
                return;
            case "/api/key": {
                String code = form.getOrDefault("code", "");
                if (!code.matches("KEYCODE_[A-Z0-9_]{1,40}")) {
                    json(socket, "{\"ok\":false}");
                    return;
                }
                boolean longPress = "1".equals(form.get("long"));
                json(socket, run("keyevent " + (longPress ? "--longpress " : "") + code));
                return;
            }
            case "/api/text": {
                String text = form.getOrDefault("text", "");
                if (text.isEmpty() || text.length() > 200) {
                    json(socket, "{\"ok\":false}");
                    return;
                }
                json(socket, run("text " + shellText(text)));
                return;
            }
            default:
                respond(socket, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
        }
    }

    private synchronized String checkPin(String given) {
        long now = System.currentTimeMillis();
        if (now < lockedUntil) return "{\"ok\":false,\"error\":\"locked\",\"seconds\":" + ((lockedUntil - now) / 1000 + 1) + "}";
        if (given == null || !given.equals(pin(ctx))) {
            if (++wrongTries >= MAX_TRIES) {
                wrongTries = 0;
                lockedUntil = now + LOCK_MS;
            }
            return "{\"ok\":false,\"error\":\"wrong\"}";
        }
        wrongTries = 0;
        byte[] b = new byte[16];
        random.nextBytes(b);
        StringBuilder hex = new StringBuilder();
        for (byte x : b) hex.append(String.format("%02x", x));
        String token = hex.toString();
        tokens.add(token);
        prefs().edit().putStringSet("tokens", new HashSet<>(tokens)).apply();
        toast("Điện thoại đã kết nối remote");
        return "{\"ok\":true,\"token\":\"" + token + "\"}";
    }

    // ---------- ADB ----------

    /** Runs `input <args>` on the TV through its own ADB; one fresh retry after a dropped connection. */
    private String run(String args) {
        try {
            return adbThread.submit(() -> {
                if (adbIdle != null) adbIdle.cancel(false);
                adbIdle = adbThread.schedule(() -> {
                    if (adb != null) adb.close();
                    adb = null;
                }, ADB_IDLE_SECONDS, TimeUnit.SECONDS);
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        if (adb == null) {
                            adb = new LocalAdb(5555, AdbKey.load(ctx.getFilesDir()),
                                    () -> toast("TV đang hỏi \"Cho phép gỡ lỗi?\": chọn Luôn cho phép rồi bấm OK"));
                            if (input == null) input = adb.shell("cmd input keyevent 0 >/dev/null 2>&1 && echo ok").trim().equals("ok")
                                    ? "cmd input" : "input";
                        }
                        adb.shell(input + " " + args);
                        return "{\"ok\":true}";
                    } catch (IOException e) {
                        if (adb != null) adb.close();
                        adb = null;
                        if (attempt > 0) return "{\"ok\":false,\"error\":\"adb\"}";
                    }
                }
                return "{\"ok\":false,\"error\":\"adb\"}";
            }).get();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"adb\"}";
        }
    }

    /** `input text` argument: spaces as %s, the rest quoted for the shell (the TV types ASCII only). */
    private static String shellText(String t) {
        StringBuilder sb = new StringBuilder("'");
        for (char c : t.toCharArray()) {
            if (c == ' ') sb.append("%s");
            else if (c == '\'') sb.append("'\\''");
            else if (c >= 0x20 && c < 0x7f) sb.append(c);
        }
        return sb.append('\'').toString();
    }

    // ---------- helpers ----------

    private void toast(String text) {
        ui.post(() -> Toast.makeText(ctx, text, Toast.LENGTH_LONG).show());
    }

    private byte[] rawResource(int id) throws IOException {
        try (InputStream in = ctx.getResources().openRawResource(id)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /** The launcher's own icon as a 180px PNG, for Safari's home-screen icon. */
    private byte[] icon() {
        Drawable d = ctx.getApplicationInfo().loadIcon(ctx.getPackageManager());
        Bitmap bmp = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, 180, 180);
        d.draw(c);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    private static void json(Socket socket, String body) throws IOException {
        respond(socket, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(Socket socket, int code, String type, byte[] body) throws IOException {
        String reason = code == 200 ? "OK" : code == 401 ? "Unauthorized" : code == 404 ? "Not Found" : "Error";
        String head = "HTTP/1.1 " + code + " " + reason + "\r\nContent-Type: " + type + "\r\nContent-Length: "
                + body.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int c; (c = in.read()) != -1; ) {
            if (c == '\n') return line.toString("UTF-8").replace("\r", "");
            if (line.size() > 8192) throw new IOException("header too long");
            line.write(c);
        }
        return line.size() > 0 ? line.toString("UTF-8") : null;
    }

    private static Map<String, String> form(String body) {
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                map.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (IllegalArgumentException | java.io.UnsupportedEncodingException ignored) {
            }
        }
        return map;
    }

    private static String jsonText(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
