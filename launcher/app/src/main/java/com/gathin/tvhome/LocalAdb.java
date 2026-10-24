package com.gathin.tvhome;

// Copied from launcher/updater (vn.cleantv.updater.LocalAdb), used by the web remote (WebRemote).

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal ADB client for the TV's own adbd on 127.0.0.1:5555 (or a TV's network ADB, from the phone build), enough
 * to run shell commands. Where adbd needs no
 * key (ro.adb.secure=0, the XGIMI DOHKO) it connects at once; where it asks for one (the onn 4K box) it signs
 * with {@link AdbKey}, and the first time offers the public key so the TV shows "Allow USB debugging?".
 */
final class LocalAdb implements AutoCloseable {
    /** Told when the TV is showing its "Allow USB debugging?" prompt for this app's key. */
    interface Prompt { void waiting(); }

    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    /** How long the TV's prompt may wait for the user's answer. */
    private static final int PROMPT_MS = 90_000;
    private static final int CNXN = 0x4e584e43, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257, AUTH = 0x48545541;
    private static final int VERSION = 0x01000000, MAX_PAYLOAD = 4096;
    /**
     * How long adbd may take to answer the handshake. Android 6 adbd names every network connection "host" and
     * silently drops one while another is open (never read, never closed), and the socket read timeout does not
     * fire there, so a watchdog closes the socket instead.
     */
    private static final int HANDSHAKE_MS = 10_000;

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextId = 1;
    private volatile long deadline;
    private volatile boolean handshakeOver, stalled;

    /** adbd wants a key this app could not provide, or the user did not allow it on the TV. */
    static final class AuthRequired extends IOException {
        AuthRequired(String why) { super(why); }
    }

    LocalAdb(int port, AdbKey key, Prompt prompt) throws IOException {
        this("127.0.0.1", port, key, prompt);
    }

    /** Another device's network ADB (the phone build): same protocol, the TV's address instead of loopback. */
    LocalAdb(String host, int port, AdbKey key, Prompt prompt) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3000);
        socket.setSoTimeout(120_000);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        deadline = System.currentTimeMillis() + HANDSHAKE_MS;
        Thread watchdog = new Thread(() -> {
            while (!handshakeOver) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    stalled = true;
                    close();
                    return;
                }
                try {
                    Thread.sleep(Math.min(left, 500));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "adb-handshake");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            handshake(key, prompt);
        } catch (IOException e) {
            close();
            if (stalled && !(e instanceof AuthRequired)) throw new IOException("ADB của máy không trả lời (có thể đang có kết nối ADB khác)", e);
            throw e;
        } finally {
            handshakeOver = true;
        }
    }

    private void handshake(AdbKey key, Prompt prompt) throws IOException {
        send(CNXN, VERSION, MAX_PAYLOAD, "host::\0".getBytes(StandardCharsets.US_ASCII));
        boolean signed = false, offered = false;
        while (true) {
            Msg m;
            try {
                m = read();
            } catch (IOException e) {
                // A "Deny", or no answer: adbd closes the connection or stays silent
                if (offered) throw new AuthRequired("chưa được cho phép trên TV");
                throw e;
            }
            if (m.cmd == CNXN) {
                socket.setSoTimeout(120_000);
                return;
            }
            if (m.cmd != AUTH || m.arg0 != AUTH_TOKEN) continue;
            if (key == null) throw new AuthRequired("máy cần khoá ADB");
            if (!signed) {
                // Known key ("Always allow" was ticked before): the signature alone gets CNXN
                signed = true;
                try {
                    send(AUTH, AUTH_SIGNATURE, 0, key.sign(m.data));
                } catch (java.security.GeneralSecurityException e) {
                    throw new IOException("không ký được bằng khoá ADB", e);
                }
            } else if (!offered) {
                // Unknown key: offer it, the TV asks the user
                offered = true;
                send(AUTH, AUTH_RSAPUBLICKEY, 0, key.publicKeyPayload());
                socket.setSoTimeout(PROMPT_MS);
                deadline = System.currentTimeMillis() + PROMPT_MS;   // the user needs time to answer the prompt
                if (prompt != null) prompt.waiting();
            } else {
                throw new AuthRequired("TV từ chối khoá");
            }
        }
    }

    /** Runs one shell command and returns its output (stdout and stderr, as adb shell prints them). */
    synchronized String shell(String command) throws IOException {
        android.util.Log.d("TvHome.Adb", "shell: " + command);
        int local = nextId++;
        send(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        int remote = 0;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        while (true) {
            Msg m = read();
            if (m.arg1 != local) continue;          // a late message for an earlier stream
            if (m.cmd == OKAY) {
                remote = m.arg0;
            } else if (m.cmd == WRTE) {
                result.write(m.data);
                send(OKAY, local, m.arg0, new byte[0]);
            } else if (m.cmd == CLSE) {
                send(CLSE, local, remote != 0 ? remote : m.arg0, new byte[0]);
                break;
            }
        }
        return result.toString("UTF-8").trim();
    }

    /**
     * Runs a command that reads {@code data} from its input, as `adb install` streams an APK into
     * `cmd package install -S <size>`. Needed where adbd is not root: the shell user cannot read a file in
     * another app's storage, so the APK goes through the connection instead of a path.
     */
    synchronized String exec(String command, java.io.InputStream data) throws IOException {
        int local = nextId++;
        send(OPEN, local, 0, ("exec:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int remote = 0;
        while (remote == 0) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == OKAY) remote = m.arg0;
            else if (m.cmd == CLSE) throw new IOException("adbd refused " + command);
        }
        byte[] buf = new byte[MAX_PAYLOAD];
        boolean closed = false;
        for (int n; !closed && (n = data.read(buf)) > 0; ) {
            send(WRTE, local, remote, java.util.Arrays.copyOf(buf, n));
            while (true) {                          // each write waits for its OKAY; output may come first
                Msg m = read();
                if (m.arg1 != local) continue;
                if (m.cmd == OKAY) break;
                if (m.cmd == WRTE) {
                    result.write(m.data);
                    send(OKAY, local, remote, new byte[0]);
                } else if (m.cmd == CLSE) {
                    closed = true;
                    break;
                }
            }
        }
        while (!closed) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == WRTE) {
                result.write(m.data);
                send(OKAY, local, remote, new byte[0]);
            } else if (m.cmd == CLSE) {
                closed = true;
            }
        }
        send(CLSE, local, remote, new byte[0]);
        android.util.Log.i("TvHome.Adb", "exec done: " + command);
        return result.toString("UTF-8").trim();
    }

    /**
     * Asks adbd to restart as root, as `adb root` does (userdebug firmware only). adbd drops every connection
     * when it restarts, so this one is closed afterwards; connect again a few seconds later.
     */
    synchronized void restartAsRoot() {
        try {
            int local = nextId++;
            // Android 6 adbd answers "restarting adbd as root" but never closes the stream: stop at the answer
            socket.setSoTimeout(5000);
            send(OPEN, local, 0, "root:\0".getBytes(StandardCharsets.US_ASCII));
            while (true) {
                Msg m = read();
                if (m.arg1 != local) continue;
                if (m.cmd == CLSE) break;
                if (m.cmd == WRTE && new String(m.data, StandardCharsets.UTF_8).contains("restarting")) break;
            }
        } catch (IOException expected) {
            // adbd went away mid-answer: it is restarting
        }
        close();
    }

    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~cmd);
        out.write(h.array());
        out.write(data);
        out.flush();
    }

    private static final class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    private Msg read() throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = h.getInt();
        m.arg0 = h.getInt();
        m.arg1 = h.getInt();
        int len = h.getInt();
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    @Override
    public void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }
}
