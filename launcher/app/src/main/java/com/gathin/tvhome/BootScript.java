package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.util.Log;

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
 * boot_script (config.json): after boot, runs /data/local/tmp/cleantv/boot.sh through the TV's own adbd, as the
 * shell (root where adbd is root). The XGIMI DOHKO (Android 14) drops fabricated overlays at every boot, so the
 * Vietnamese Settings and power-menu text are fabricated again from there (tools/xgimi-vi/dohko-boot-vi.ps1 puts
 * the files in place, owned by root). Only for TVs whose adbd needs no key (ro.adb.secure=0); elsewhere the
 * connection is refused and nothing happens.
 */
final class BootScript {
    private static final String TAG = "TvHome.BootScript";
    static final String PATH = "/data/local/tmp/cleantv/boot.sh";
    private static final int CNXN = 0x4e584e43, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;

    private BootScript() {
    }

    /** On a background thread; finishes the boot broadcast when done. */
    static void run(BroadcastReceiver.PendingResult pending) {
        new Thread(() -> {
            try {
                // adbd comes up with the network, and the overlay service must be ready: try for about a minute
                for (int i = 0; i < 12; i++) {
                    try {
                        String out = shell("[ -f " + PATH + " ] && sh " + PATH + " 2>&1 || echo no-script");
                        Log.i(TAG, "boot.sh: " + out);
                        return;
                    } catch (IOException e) {
                        Log.i(TAG, "adbd not ready (" + e.getMessage() + ")");
                        Thread.sleep(5000);
                    }
                }
            } catch (InterruptedException ignored) {
            } finally {
                pending.finish();
            }
        }, "boot-script").start();
    }

    /** One shell command over 127.0.0.1:5555, without a key; returns its output. */
    private static String shell(String command) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
            socket.setSoTimeout(30_000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            send(out, CNXN, 0x01000000, 4096, "host::\0".getBytes(StandardCharsets.US_ASCII));
            // AUTH instead of CNXN: this adbd wants a key, which the launcher does not have
            if (read(in)[0] != CNXN) throw new IOException("adbd asks for a key");
            send(out, OPEN, 1, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            int remote = 0;
            while (true) {
                int[] h = read(in);
                if (h[2] != 1) continue;
                if (h[0] == OKAY) remote = h[1];
                else if (h[0] == WRTE) {
                    result.write(lastData);
                    send(out, OKAY, 1, remote, new byte[0]);
                } else if (h[0] == CLSE) {
                    break;
                }
            }
            return result.toString("UTF-8").trim();
        }
    }

    private static byte[] lastData = new byte[0];

    private static void send(OutputStream out, int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~cmd);
        out.write(h.array());
        out.write(data);
        out.flush();
    }

    /** {cmd, arg0, arg1}; the payload goes to lastData (one thread uses this class at a time). */
    private static int[] read(DataInputStream in) throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = h.getInt(), arg0 = h.getInt(), arg1 = h.getInt(), len = h.getInt();
        lastData = new byte[len];
        in.readFully(lastData);
        return new int[]{cmd, arg0, arg1};
    }
}
