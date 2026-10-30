package com.gathin.karaoke;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Built-in player: YouTube's IFrame player in a WebView, so the app knows when a song ends and starts the next
 * one in the queue. Remote: OK / Play-Pause pauses, Left / Right jump 10 s, Fast-forward / Next / Channel up skips
 * to the next song, Back stops. A video whose owner forbids embedding (errors 101/150/152/153) or that will not
 * play in this TV's WebView opens in the YouTube app instead.
 */
public class PlayerActivity extends Activity {
    private static final String EXTRA_SONG = "song";
    /** The page's address as YouTube sees it (Referer and the IFrame API origin); must be https. */
    private static final String ORIGIN = "https://com.gathin.karaoke";
    private static final long NEXT_DELAY_MS = 4000, BANNER_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView banner;
    private volatile Song song;
    private volatile boolean paused;
    private boolean ready;

    /** The player on screen, for the phone page (PhoneRemote); null when none. */
    static volatile PlayerActivity current;

    // ---- Called by the phone page, from its threads ----

    Song nowSinging() {
        return song;
    }

    boolean isPaused() {
        return paused;
    }

    /** Sings s at once, in this player. */
    void playNow(Song s) {
        handler.post(() -> {
            handler.removeCallbacksAndMessages(null);
            song = s;
            nextNoticeShown = false;
            if (ready) js("load('" + s.id + "')");
            else if (web != null) web.loadDataWithBaseURL(ORIGIN + "/", page(s.id), "text/html", "utf-8", null);
            showBanner(getString(R.string.now_singing, s.title), BANNER_MS);
            handler.postDelayed(watch, WATCH_MS);
        });
    }

    void remoteToggle() {
        handler.post(() -> js("toggle()"));
    }

    void remoteSkip() {
        handler.post(this::skip);
    }

    void remoteSeek(int seconds) {
        handler.post(() -> js("seek(" + seconds + ")"));
    }

    void remoteStop() {
        handler.post(this::finish);
    }

    static Intent intent(Context c, Song song) {
        String json;
        try {
            json = song.toJson().toString();
        } catch (JSONException e) {
            json = "{}";
        }
        return new Intent(c, PlayerActivity.class).putExtra(EXTRA_SONG, json);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            song = Song.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_SONG)));
        } catch (JSONException | NullPointerException e) {
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        web = new WebView(this);
        // Keys go to dispatchKeyEvent below, never into the page
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.setBackgroundColor(0xFF000000);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Karaoke");
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        banner = (TextView) getLayoutInflater().inflate(R.layout.player_banner, root, false);
        root.addView(banner);
        String pairUrl = PhoneRemote.pairUrl(this);
        if (pairUrl != null) {
            qrBox = getLayoutInflater().inflate(R.layout.player_qr, root, false);
            int px = Math.round(70 * getResources().getDisplayMetrics().density);
            ((android.widget.ImageView) qrBox.findViewById(R.id.player_qr)).setImageBitmap(Qr.bitmap(pairUrl, px));
            root.addView(qrBox);
        }
        search = new PlayerSearch(this, root);
        setContentView(root);
        web.loadDataWithBaseURL(ORIGIN + "/", page(song.id), "text/html", "utf-8", null);
        showBanner(getString(R.string.now_singing, song.title) + "\n" + getString(R.string.player_hint), BANNER_MS);
        current = this;
    }

    /** The search strip over the video (Up opens it), and the QR code it covers while open. */
    private PlayerSearch search;
    private View qrBox;

    private void openSearch() {
        hideBanner.run();
        if (qrBox != null) qrBox.setVisibility(View.GONE);
        search.open();
    }

    private void closeSearch() {
        search.close();
        if (qrBox != null) qrBox.setVisibility(View.VISIBLE);
        placeBanner();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Voice.REQUEST_SPEECH && search != null) search.voice().onResult(resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == Voice.REQUEST_PERMISSION && search != null) {
            search.voice().onPermission(grantResults.length > 0 && grantResults[0] == 0);
        }
    }

    private static String page(String videoId) {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}"
                + "</style></head><body><div id='p'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var player,target=-1,targetAt=0;"
                // YouTube's own subtitles would sit over the karaoke lyrics burnt into the video
                + "function noCaptions(){try{player.unloadModule('captions');player.unloadModule('cc');}catch(x){}}"
                + "function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',"
                + "videoId:'" + videoId + "',playerVars:{autoplay:1,controls:0,rel:0,playsinline:1,fs:0,"
                + "iv_load_policy:3,disablekb:1,cc_load_policy:0,origin:'" + ORIGIN + "'},events:{"
                + "onReady:function(e){e.target.playVideo();noCaptions();Karaoke.ready();},"
                + "onApiChange:function(){noCaptions();},"
                + "onStateChange:function(e){if(e.data==1)noCaptions();Karaoke.state(e.data);},"
                + "onError:function(e){Karaoke.error(e.data);}}});}"
                + "function load(id){target=-1;player.loadVideoById(id);}"
                + "function toggle(){if(player.getPlayerState()==1)player.pauseVideo();else player.playVideo();}"
                // getCurrentTime() lags behind a seek: presses in quick succession add up from the last target
                + "function seek(d){var now=Date.now();"
                + "var base=(target>=0&&now-targetAt<2000)?target:player.getCurrentTime();"
                + "target=Math.max(0,Math.min(base+d,player.getDuration()-1));targetAt=now;"
                + "player.seekTo(target,true);}"
                + "function pause(){if(player&&player.pauseVideo)player.pauseVideo();}"
                + "</script></body></html>";
    }

    /** Called from the page's script, on a WebView thread. Public: the WebView calls its methods by reflection. */
    public final class Bridge {
        @JavascriptInterface public void ready() {
            handler.post(() -> ready = true);
        }

        @JavascriptInterface public void state(int s) {
            handler.post(() -> {
                if (s == 0) {                                         // YT.PlayerState.ENDED
                    // The player can say so twice: the second would take (or, on an empty list, close) one more
                    if (nextPending) android.util.Log.i("Karaoke", "repeated end ignored");
                    else songEnded();
                }
                if (s == 1) nextPending = false;
                paused = s == 2;
                if (s == 2) showBanner(getString(R.string.paused, song.title), 0);
                else if (s == 1) hideBannerLater(1500);
            });
        }

        @JavascriptInterface public void error(int code) {
            handler.post(() -> {
                android.util.Log.i("Karaoke", "player error " + code + " for " + song.id);
                // Not embeddable (101, 150, and 152/153 when YouTube rejects the WebView): the YouTube app can
                Player.openInYouTube(PlayerActivity.this, song);
                finish();
            });
        }
    }

    /** The next song was taken from the list and has not started playing yet. */
    private boolean nextPending;

    private void songEnded() {
        Song next = SongQueue.get(this).next();
        if (next == null) {
            finish();
            return;
        }
        android.util.Log.i("Karaoke", "next song " + next.id);
        nextPending = true;
        song = next;
        nextNoticeShown = false;
        Library.get(this).sung(next);
        showBanner(getString(R.string.next_up, next.title), 0);
        handler.postDelayed(() -> {
            js("load('" + next.id + "')");
            showBanner(getString(R.string.now_singing, next.title), BANNER_MS);
        }, NEXT_DELAY_MS);
    }

    private void skip() {
        handler.removeCallbacksAndMessages(null);
        songEnded();
        handler.postDelayed(watch, WATCH_MS);
    }

    /** Seconds left in the song at which the next one is announced, like a karaoke box's "Bài kế" line. */
    private static final int NOTICE_S = 20;
    private static final long WATCH_MS = 3000;
    private boolean nextNoticeShown;

    /** Every few seconds: how long the song still runs; near its end the next song is shown at the top. */
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            handler.postDelayed(this, WATCH_MS);
            if (web == null || !ready || nextNoticeShown) return;
            web.evaluateJavascript(
                    "player&&player.getDuration?Math.floor(player.getDuration()-player.getCurrentTime()):-1",
                    value -> {
                        int left;
                        try {
                            left = (int) Double.parseDouble(value);
                        } catch (NumberFormatException | NullPointerException e) {
                            return;
                        }
                        if (left >= 0 && left <= 60) android.util.Log.i("Karaoke", "song ends in " + left + " s");
                        SongQueue q = SongQueue.get(PlayerActivity.this);
                        Song next = q.peek();
                        if (left > 0 && left <= NOTICE_S && next != null && !nextNoticeShown) {
                            nextNoticeShown = true;
                            showBanner(getString(R.string.next_soon, next.title, q.size()), (left + 1) * 1000L);
                        }
                    });
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean media = k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PLAY
                || k == KeyEvent.KEYCODE_MEDIA_PAUSE || k == KeyEvent.KEYCODE_MEDIA_NEXT
                || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || k == KeyEvent.KEYCODE_MEDIA_REWIND;
        // Search panel open: the remote moves through it; Back closes it, the media keys still drive the song
        if (search != null && search.isOpen() && !media) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP && !search.back()) closeSearch();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (k == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_SEARCH || k == KeyEvent.KEYCODE_MENU) {
            if (e.getRepeatCount() == 0 && search != null) {
                openSearch();
            }
            return true;
        }
        if (!ready) return true;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (e.getRepeatCount() == 0) js("toggle()");
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                js("seek(-10)");
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                js("seek(10)");
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (e.getRepeatCount() == 0) skip();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_INFO:
                showBanner(bannerText(), BANNER_MS);
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    private String bannerText() {
        Song next = SongQueue.get(this).songs().isEmpty() ? null : SongQueue.get(this).songs().get(0);
        String now = getString(R.string.now_singing, song.title);
        return next == null ? now : now + "\n" + getString(R.string.next_up, next.title);
    }

    private final Runnable hideBanner = () -> banner.setVisibility(View.GONE);

    /** Shows the line at the top (below the search strip while it is open); ms 0 keeps it until the next one. */
    private void showBanner(String text, long ms) {
        banner.setText(text);
        placeBanner();
        banner.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideBanner);
        if (ms > 0) handler.postDelayed(hideBanner, ms);
    }

    private void placeBanner() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) banner.getLayoutParams();
        float dp = getResources().getDisplayMetrics().density;
        lp.topMargin = search != null && search.isOpen() ? search.height() + Math.round(10 * dp) : Math.round(24 * dp);
        banner.setLayoutParams(lp);
    }

    private void hideBannerLater(long ms) {
        handler.removeCallbacks(hideBanner);
        handler.postDelayed(hideBanner, ms);
    }

    private void js(String script) {
        if (web != null) web.evaluateJavascript(script, null);
    }

    /** The song was playing when something covered the player (the voice screen): it goes on when we are back. */
    private boolean playOnReturn;

    @Override
    protected void onPause() {
        super.onPause();
        playOnReturn = ready && !paused;
        js("pause()");
        handler.removeCallbacks(watch);
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        if (playOnReturn) js("player.playVideo()");
        playOnReturn = false;
        handler.removeCallbacks(watch);
        handler.postDelayed(watch, WATCH_MS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        PhoneRemote.visible++;
    }

    @Override
    protected void onStop() {
        PhoneRemote.visible--;
        super.onStop();
    }

    /** A closing player takes no more songs from the phone: Player.play starts a new one. */
    @Override
    public void finish() {
        if (current == this) current = null;
        super.finish();
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        handler.removeCallbacksAndMessages(null);
        if (search != null) search.destroy();
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
