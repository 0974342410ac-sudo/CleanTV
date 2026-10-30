package com.gathin.karaoke;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The phone page: http://<TV address>:8687 on the home network, for any phone browser (iPhone too), no app to
 * install. The TV shows it as a QR code with the PIN in it ("Điện thoại" page); a right PIN gives the browser a
 * token it keeps. From the phone: search YouTube (the TV searches, like on its own screen), add songs to the list,
 * sing next, reorder, and pause / skip / seek the built-in player. Runs while the app's process lives; songs are
 * started only while the app is on screen (Android stops background apps from opening screens).
 * Like the launcher's WebRemote (port 8686), plain sockets, one short request per connection.
 */
final class PhoneRemote {
    static final int PORT = 8687;
    private static final String TAG = "Karaoke.Phone";
    private static final String PREFS = "phone_remote";
    private static final int MAX_BODY = 8192;

    private static PhoneRemote running;
    /** Screens of this app on display (KaraokeActivity, PlayerActivity): songs may be started only then. */
    static volatile int visible;

    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();
    private final ExecutorService clients = Executors.newFixedThreadPool(4);
    private final Set<String> tokens = Collections.synchronizedSet(new HashSet<>());
    private final RemoteApi.PinGate gate = new RemoteApi.PinGate();

    private PhoneRemote(Context ctx) {
        this.ctx = ctx;
        tokens.addAll(prefs().getStringSet("tokens", Collections.emptySet()));
    }

    /** Starts the server once per process. */
    static synchronized void start(Context context) {
        if (running != null) return;
        running = new PhoneRemote(context.getApplicationContext());
        Thread t = new Thread(running::serve, "phone-remote");
        t.setDaemon(true);
        t.start();
    }

    /** The PIN shown on the TV; made once and kept, so a phone that knows it keeps working. */
    static String pin(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String pin = p.getString("pin", null);
        if (pin == null) {
            pin = String.format(Locale.ROOT, "%04d", new SecureRandom().nextInt(10000));
            p.edit().putString("pin", pin).apply();
        }
        return pin;
    }

    /** A new PIN, and every phone paired so far has to pair again. */
    static void newPin(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("pin").remove("tokens").apply();
        if (running != null) running.tokens.clear();
    }

    /** What the QR codes carry: the address with the PIN, so scanning pairs at once; null without a network. */
    static String pairUrl(Context context) {
        String address = address();
        return address == null ? null : address + "/#pin=" + pin(context);
    }

    /** http://<Wi-Fi or Ethernet IPv4>:8687, or null without a network. */
    static String address() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        return "http://" + a.getHostAddress() + ":" + PORT;
                    }
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
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                }
            }
            int length = 0;
            try {
                length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (NumberFormatException ignored) {
            }
            if (length > MAX_BODY) {
                respond(socket, 413, "text/plain", "too large".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String body = "";
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
            route(socket, method, path, headers, RemoteApi.form(body));
        } catch (IOException e) {
            Log.i(TAG, "client: " + e);
        } catch (JSONException e) {
            Log.w(TAG, "json: " + e);
        }
    }

    private void route(Socket socket, String method, String path, Map<String, String> headers,
                       Map<String, String> form) throws IOException, JSONException {
        if ("GET".equals(method) && ("/".equals(path) || path.startsWith("/?") || "/index.html".equals(path))) {
            respond(socket, 200, "text/html; charset=utf-8", rawResource(R.raw.phone));
            return;
        }
        if ("GET".equals(method) && "/manifest.json".equals(path)) {
            String m = "{\"name\":\"Karaoke\",\"short_name\":\"Karaoke\",\"start_url\":\"/\",\"display\":\"standalone\","
                    + "\"background_color\":\"#0B0B10\",\"theme_color\":\"#0B0B10\"}";
            respond(socket, 200, "application/manifest+json", m.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!"POST".equals(method)) {
            respond(socket, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if ("/api/pin".equals(path)) {
            json(socket, checkPin(form.get("pin")));
            return;
        }
        // Everything else needs a token from a right PIN
        String token = headers.get("x-token");
        if (token == null || !tokens.contains(token)) {
            respond(socket, 401, "application/json", "{\"ok\":false,\"error\":\"pin\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String answer = api(path, form);
        if (answer == null) respond(socket, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
        else json(socket, answer);
    }

    /** A call from the phone page: the JSON answer, null for an unknown path. */
    String api(String path, Map<String, String> form) throws JSONException {
        SongQueue queue = SongQueue.get(ctx);
        switch (path) {
            case "/api/state":
                return state().toString();
            case "/api/catalog":
                return catalog().toString();
            case "/api/search": {
                String q = form.getOrDefault("q", "").trim();
                if (q.isEmpty() || q.length() > 120) {
                    return error("empty");
                }
                String filter = "1".equals(form.get("trending")) ? YouTubeSearch.VIDEOS_THIS_MONTH
                        : YouTubeSearch.VIDEOS_ONLY;
                String query = "1".equals(form.get("exact")) ? q : SearchParser.query(q);
                try {
                    return RemoteApi.page(YouTubeSearch.search(query, filter)).toString();
                } catch (IOException | JSONException e) {
                    Log.w(TAG, "search failed: " + e);
                    return error("search");
                }
            }
            case "/api/more": {
                String more = form.get("more");
                if (more == null || more.isEmpty()) {
                    return error("empty");
                }
                try {
                    SearchParser.Page from = new SearchParser.Page(Collections.emptyList(), more, form.get("version"));
                    return RemoteApi.page(YouTubeSearch.more(from)).toString();
                } catch (IOException | JSONException e) {
                    Log.w(TAG, "more failed: " + e);
                    return error("search");
                }
            }
            case "/api/add": {
                Song s = RemoteApi.song(form);
                if (s == null) {
                    return error("song");
                }
                boolean added = queue.add(s);
                if (added) toast(ctx.getString(R.string.phone_added, s.title));
                return new JSONObject().put("ok", true).put("added", added).put("count", queue.size()).toString();
            }
            case "/api/priority": {
                Song s = RemoteApi.song(form);
                if (s == null) {
                    return error("song");
                }
                queue.addFirst(s);
                toast(ctx.getString(R.string.prioritized, s.title));
                return ok();
            }
            case "/api/play": {
                Song s = RemoteApi.song(form);
                if (s == null) {
                    return error("song");
                }
                return sing(s);
            }
            case "/api/next": {
                PlayerActivity p = PlayerActivity.current;
                if (p != null) {
                    p.remoteSkip();
                    return ok();
                }
                if (queue.size() == 0) {
                    return error("empty");
                }
                if (visible == 0) {
                    return error("tv");
                }
                return sing(queue.next());
            }
            case "/api/toggle":
            case "/api/seek":
            case "/api/stop": {
                PlayerActivity p = PlayerActivity.current;
                if (p == null) {
                    return error("player");
                }
                if (path.endsWith("toggle")) p.remoteToggle();
                else if (path.endsWith("stop")) p.remoteStop();
                else p.remoteSeek("-1".equals(form.get("dir")) ? -10 : 10);
                return ok();
            }
            case "/api/remove":
            case "/api/up": {
                int i = RemoteApi.index(form);
                // The phone names the song too: if the list changed meanwhile, the wrong one is not touched
                List<Song> now = queue.songs();
                if (i < 0 || i >= now.size() || !now.get(i).id.equals(form.get("id"))) {
                    return error("changed");
                }
                if (path.endsWith("remove")) queue.remove(i);
                else queue.moveToFront(i);
                return ok();
            }
            case "/api/clear":
                queue.clear();
                return ok();
            default:
                return null;
        }
    }

    /** Starts s on the TV, on the main thread; refused while the app is not on screen. */
    private String sing(Song s) {
        if (visible == 0) return error("tv");
        ui.post(() -> {
            Player.play(ctx, s);
            Toast.makeText(ctx, ctx.getString(R.string.phone_singing, s.title), Toast.LENGTH_SHORT).show();
        });
        return ok();
    }

    private JSONObject state() throws JSONException {
        SongQueue queue = SongQueue.get(ctx);
        PlayerActivity p = PlayerActivity.current;
        JSONObject o = new JSONObject().put("ok", true)
                .put("queue", RemoteApi.songsJson(queue.songs()))
                .put("tv", visible > 0)
                .put("external", Player.external(ctx));
        Song now = p == null ? null : p.nowSinging();
        if (now != null) o.put("now", RemoteApi.songJson(now)).put("paused", p.isPaused());
        return o;
    }

    /** Quick searches for the phone: genres (as on the TV) and singers, with photos already found. */
    private JSONObject catalog() throws JSONException {
        JSONArray genres = new JSONArray();
        for (KaraokeActivity.Genre g : KaraokeActivity.GENRE_LIST) {
            genres.put(new JSONObject().put("label", ctx.getString(g.label)).put("query", g.query));
        }
        JSONArray singers = new JSONArray();
        for (String name : ctx.getResources().getStringArray(R.array.singers)) {
            JSONObject s = new JSONObject().put("name", name);
            String photo = Avatars.cached(ctx, name);
            if (photo != null && !photo.isEmpty()) s.put("photo", photo);
            singers.put(s);
        }
        return new JSONObject().put("ok", true).put("genres", genres).put("singers", singers);
    }

    private String checkPin(String given) {
        String result = gate.check(given, pin(ctx), System.currentTimeMillis());
        if (result.startsWith("locked:")) {
            return "{\"ok\":false,\"error\":\"locked\",\"seconds\":" + result.substring(7) + "}";
        }
        if (!result.equals("ok")) return error("wrong");
        byte[] b = new byte[16];
        random.nextBytes(b);
        StringBuilder hex = new StringBuilder();
        for (byte x : b) hex.append(String.format(Locale.ROOT, "%02x", x));
        String token = hex.toString();
        tokens.add(token);
        prefs().edit().putStringSet("tokens", new HashSet<>(tokens)).apply();
        toast(ctx.getString(R.string.phone_connected));
        return "{\"ok\":true,\"token\":\"" + token + "\"}";
    }

    // ---------- helpers ----------

    private static String ok() {
        return "{\"ok\":true}";
    }

    private static String error(String what) {
        return "{\"ok\":false,\"error\":\"" + what + "\"}";
    }

    private void toast(String text) {
        ui.post(() -> Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show());
    }

    private byte[] rawResource(int id) throws IOException {
        try (InputStream in = ctx.getResources().openRawResource(id)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void json(Socket socket, String body) throws IOException {
        respond(socket, 200, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
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
}
