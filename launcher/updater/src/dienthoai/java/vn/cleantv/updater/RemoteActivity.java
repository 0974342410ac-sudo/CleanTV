package vn.cleantv.updater;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phone build: a remote control for the TV, over the same network ADB the updater uses. Each button sends
 * `input keyevent` through one LocalAdb connection kept open while the screen is shown (reopened after a drop);
 * arrows and volume repeat while held. Text goes through `input text`, which only types plain ASCII.
 */
public final class RemoteActivity extends Activity {
    static final String EXTRA_HOST = "host";

    private final Handler ui = new Handler(Looper.getMainLooper());
    /** One thread, so key presses reach the TV in order. */
    private final ExecutorService adbThread = Executors.newSingleThreadExecutor();
    private String host;
    private LocalAdb adb;
    private TextView status;
    private float dp;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        host = getIntent().getStringExtra(EXTRA_HOST);
        dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF0E1116);
        int pad = (int) (20 * dp);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Điều khiển TV");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        root.addView(title);
        status = new TextView(this);
        status.setTextColor(0xFFAAB4BE);
        status.setTextSize(14);
        status.setText("Đang kết nối " + host + " …");
        status.setPadding(0, (int) (4 * dp), 0, (int) (16 * dp));
        root.addView(status);

        // Power, Home, Back, Menu
        LinearLayout top = row();
        top.addView(small(key("Nguồn", "KEYCODE_POWER", false)), weight());
        top.addView(small(key("Home", "KEYCODE_HOME", false)), weight());
        top.addView(small(key("Quay lại", "KEYCODE_BACK", false)), weight());
        top.addView(small(key("Menu", "KEYCODE_MENU", false)), weight());
        root.addView(top, full());

        // D-pad with OK in the middle
        GridLayout pad3 = new GridLayout(this);
        pad3.setColumnCount(3);
        pad3.setRowCount(3);
        int cell = (int) (92 * dp);
        String[][] grid = {{null, "▲", null}, {"◀", "OK", "▶"}, {null, "▼", null}};
        String[][] codes = {{null, "KEYCODE_DPAD_UP", null},
                {"KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_CENTER", "KEYCODE_DPAD_RIGHT"},
                {null, "KEYCODE_DPAD_DOWN", null}};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                View v;
                if (grid[r][c] == null) {
                    v = new View(this);
                } else {
                    boolean ok = "OK".equals(grid[r][c]);
                    Button b = key(grid[r][c], codes[r][c], !ok);
                    b.setTextSize(ok ? 22 : 26);
                    if (ok) {
                        b.setBackground(pill(0xFF2F7DF6, 46 * dp));
                        // Hold OK = long press on the TV (e.g. the launcher's Settings menu)
                        b.setOnLongClickListener(x -> {
                            send(input + " keyevent --longpress KEYCODE_DPAD_CENTER");
                            buzz();
                            return true;
                        });
                    }
                    v = b;
                }
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(r), GridLayout.spec(c));
                lp.width = cell;
                lp.height = cell;
                lp.setMargins((int) (4 * dp), (int) (4 * dp), (int) (4 * dp), (int) (4 * dp));
                pad3.addView(v, lp);
            }
        }
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.topMargin = (int) (18 * dp);
        pp.bottomMargin = (int) (18 * dp);
        root.addView(pad3, pp);

        // Volume and playback
        LinearLayout vol = row();
        vol.addView(key("🔉 −", "KEYCODE_VOLUME_DOWN", true), weight());
        vol.addView(key("🔇", "KEYCODE_VOLUME_MUTE", false), weight());
        vol.addView(key("🔊 +", "KEYCODE_VOLUME_UP", true), weight());
        root.addView(vol, full());
        LinearLayout play = row();
        play.addView(key("⏪", "KEYCODE_MEDIA_REWIND", true), weight());
        play.addView(key("⏯", "KEYCODE_MEDIA_PLAY_PAUSE", false), weight());
        play.addView(key("⏩", "KEYCODE_MEDIA_FAST_FORWARD", true), weight());
        play.addView(key("🔍", "KEYCODE_SEARCH", false), weight());
        root.addView(play, full());

        // Typing into the TV's focused text field
        LinearLayout typeRow = row();
        EditText text = new EditText(this);
        text.setHint("Gõ chữ gửi sang TV (không dấu)");
        text.setHintTextColor(0xFF6B7684);
        text.setTextColor(Color.WHITE);
        text.setSingleLine(true);
        text.setImeOptions(EditorInfo.IME_ACTION_SEND);
        typeRow.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button sendText = button("Gửi");
        Runnable doSend = () -> {
            String t = text.getText().toString();
            if (t.isEmpty()) return;
            send(input + " text " + shellText(t));
            text.setText("");
        };
        sendText.setOnClickListener(v -> doSend.run());
        text.setOnEditorActionListener((v, action, ev) -> {
            doSend.run();
            return true;
        });
        typeRow.addView(sendText);
        LinearLayout.LayoutParams tp = full();
        tp.topMargin = (int) (14 * dp);
        root.addView(typeRow, tp);
        LinearLayout edit = row();
        edit.addView(key("⌫ Xoá", "KEYCODE_DEL", true), weight());
        edit.addView(key("↵ Enter", "KEYCODE_ENTER", false), weight());
        root.addView(edit, full());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        adbThread.execute(() -> {
            if (adb != null) adb.close();
        });
        adbThread.shutdown();
    }

    // ---------- ADB ----------

    /**
     * `input` starts a Java process on the TV for every key (half a second or more on Android 9-11);
     * `cmd input` (Android 12+) talks to the input service directly. Chosen once per connection.
     */
    private volatile String input = "input";

    private void connect() {
        adbThread.execute(() -> {
            try {
                open();
                // KEYCODE_UNKNOWN does nothing; older Android has no "input" service for cmd and fails
                if (adb.shell("cmd input keyevent 0 >/dev/null 2>&1 && echo ok").trim().equals("ok")) input = "cmd input";
                ui.post(() -> status.setText("Đã kết nối " + host));
            } catch (IOException e) {
                ui.post(() -> status.setText("Chưa kết nối được " + host + " (" + e.getMessage() + "). Bấm một nút để thử lại."));
            }
        });
    }

    /** On the ADB thread: a connection, opened again when the last one dropped. */
    private void open() throws IOException {
        if (adb != null) return;
        AdbKey key;
        try {
            key = AdbKey.load(getFilesDir());
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("không tạo được khoá ADB", e);
        }
        adb = new LocalAdb(host, 5555, key, () -> ui.post(() ->
                status.setText("Trên TV đang hỏi \"Cho phép gỡ lỗi?\": chọn Luôn cho phép rồi bấm OK.")));
    }

    private void send(String command) {
        adbThread.execute(() -> {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    open();
                    adb.shell(command);
                    if (attempt > 0) ui.post(() -> status.setText("Đã kết nối " + host));
                    return;
                } catch (IOException e) {
                    if (adb != null) adb.close();
                    adb = null;   // dropped (TV asleep, Wi-Fi): one fresh try
                    if (attempt > 0) ui.post(() -> status.setText("Mất kết nối " + host + " (" + e.getMessage() + ")"));
                }
            }
        });
    }

    /** `input text` argument: spaces as %s, everything else quoted for the shell. ASCII only on the TV side. */
    private static String shellText(String t) {
        StringBuilder sb = new StringBuilder("'");
        for (char c : t.toCharArray()) {
            if (c == ' ') sb.append("%s");
            else if (c == '\'') sb.append("'\\''");
            else sb.append(c);
        }
        return sb.append('\'').toString();
    }

    // ---------- buttons ----------

    /** A key button; with repeat, holding it keeps sending (arrows, volume, delete). */
    private Button key(String label, String keycode, boolean repeat) {
        Button b = button(label);
        if (!repeat) {
            b.setOnClickListener(v -> {
                send(input + " keyevent " + keycode);
                buzz();
            });
            return b;
        }
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            send(input + " keyevent " + keycode);
            ui.postDelayed(tick[0], 180);
        };
        b.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                v.setPressed(true);
                buzz();
                send(input + " keyevent " + keycode);
                ui.postDelayed(tick[0], 450);
            } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setPressed(false);
                ui.removeCallbacks(tick[0]);
            }
            return true;
        });
        return b;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        b.setBackground(pill(0xFF262C36, 22 * dp));
        return b;
    }

    /** Four buttons share the top row: smaller text, so "Quay lại" fits a phone's width on one line. */
    private static Button small(Button b) {
        b.setTextSize(15);
        b.setSingleLine(true);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private void buzz() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) v.vibrate(15);
    }

    private static GradientDrawable pill(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (56 * dp), 1f);
        lp.setMargins((int) (4 * dp), (int) (4 * dp), (int) (4 * dp), (int) (4 * dp));
        return lp;
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
