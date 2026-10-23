package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Voice search behind the top bar's Search pill. Uses the TV's default speech recogniser (on the XGIMI
 * DOHKO: Google's, in the Google TTS package), in Vietnamese. Saying an installed app's name opens it;
 * anything else is searched on YouTube, in TizenTube when installed, else the YouTube app.
 */
final class VoiceSearch {
    static final int PERMISSION_REQUEST = 77;
    private static final String[] YOUTUBE = {"io.gh.reisxd.tizentube.cobalt", "com.google.android.youtube.tv",
            "com.google.android.youtube"};

    private final Activity activity;
    private SpeechRecognizer recognizer;
    private Dialog dialog;
    private TextView heard;

    VoiceSearch(Activity activity) {
        this.activity = activity;
    }

    void start() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            typeSearch(R.string.voice_unavailable);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST);
            return;
        }
        stop();
        said.setLength(0);
        alternatives = null;
        segments = 0;
        finishing = false;
        listenStart = android.os.SystemClock.uptimeMillis();
        showDialog();
        listen(false);
        // A noisy room (or the projector's own fan) can keep the recogniser from hearing the end of speech, and a
        // lost release would keep it open: at the deadline it stops and what it has is used.
        handler.postDelayed(this::finish, LISTEN_MS);
    }

    /**
     * The recogniser ends each time the speaker pauses, so while the button is held a new session listens on
     * after every piece of speech, and the pieces are joined. Without a held button (the on-screen Search pill)
     * a follow-up session that hears nothing within MORE_MS ends the listen. LISTEN_MS caps a stuck listen.
     */
    private static final long LISTEN_MS = 20000, MORE_MS = 2000, GIVE_UP_MS = 6000;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final StringBuilder said = new StringBuilder();
    /** The recogniser's guesses when everything was said in one piece; app names match better against them. */
    private List<String> alternatives;
    private int segments;
    private boolean finishing;
    private long listenStart;
    private final Runnable noMoreSpeech = () -> { if (!this.keyHeld) done(); };

    private void listen(boolean followUp) {
        if (recognizer != null) recognizer.destroy();   // a fresh recogniser: reusing one right after results is "busy"
        recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
        recognizer.setRecognitionListener(listener);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.getPackageName());
        followUpSession = followUp;
        if (!followUp) micOpened = false;
        loudest = -100;
        recognizer.startListening(intent);
        android.util.Log.i(TAG, "listen followUp=" + followUp + " heard=\"" + said + "\"");
    }

    private static final String TAG = "TvHome.Voice";
    /** Loudest microphone level of this session, for the log. */
    private float loudest;
    /**
     * The recogniser got as far as listening (onReadyForSpeech). On the Android 6 XGIMI (.239) the remote's
     * microphone cannot be opened (no audio policy profile for it): the recogniser then fails at once, with
     * ERROR_NETWORK, before ever being ready, and typing is offered instead (typeSearch).
     */
    private boolean micOpened;

    /** The session listens on after speech: MORE_MS of silence from when the microphone is ready ends the listen. */
    private boolean followUpSession;

    /** No answer from the recogniser after it was told to stop: use what was heard, or say nothing was. */
    private void giveUp() {
        if (recognizer == null) return;
        done();
    }

    /**
     * Remote microphone button pressed: hold to talk. The XGIMI remote's microphone sends sound only while the
     * button is held (one press event, one release event), and the projector itself has no usable microphone,
     * so listening lasts while the button is held and ends at release (see released). A press can arrive twice
     * (key service and window), which only keeps listening.
     */
    void onKeyDown() {
        android.util.Log.i(TAG, "mic key down, listening=" + (recognizer != null));
        keyHeld = true;
        releasedEarly = false;
        handler.removeCallbacks(released);
        if (recognizer == null) {
            pressStart = android.os.SystemClock.uptimeMillis();
            start();
        }
    }

    /** Button released: end the listen shortly after, unless the press was only a tap (see released). */
    void onKeyUp(long heldMs) {
        android.util.Log.i(TAG, "mic key up after " + heldMs + " ms, listening=" + (recognizer != null));
        keyHeld = false;
        if (recognizer == null) {
            // Released before Home came up to listen (pressed in another app): a tap, nothing was heard
            releasedEarly = true;
            return;
        }
        handler.removeCallbacks(released);
        handler.postDelayed(released, RELEASE_MS);
    }

    /** Called by Home for a press made in another app, once Home is on screen. */
    void startFromKey() {
        if (releasedEarly) {
            releasedEarly = false;
            toast(activity.getString(R.string.voice_hold_hint));
            return;
        }
        pressStart = android.os.SystemClock.uptimeMillis();
        start();
    }

    /** Release settles after RELEASE_MS; a press shorter than TAP_MS heard nothing, so it shows how to talk. */
    private static final long RELEASE_MS = 250, TAP_MS = 700;
    private boolean keyHeld, releasedEarly;
    private long pressStart;
    private final Runnable released = this::onReleased;

    private void onReleased() {
        if (keyHeld || recognizer == null) return;
        if (android.os.SystemClock.uptimeMillis() - pressStart - RELEASE_MS < TAP_MS && said.length() == 0) {
            stop();
            toast(activity.getString(R.string.voice_hold_hint));
        } else {
            finish();
        }
    }

    /** Stops the microphone; the recogniser's last results end the listen (or giveUp does). */
    private void finish() {
        if (recognizer == null || finishing) return;
        finishing = true;
        handler.removeCallbacks(noMoreSpeech);
        recognizer.stopListening();
        handler.postDelayed(this::giveUp, GIVE_UP_MS);
    }

    /** Ends the listen and acts on everything heard. */
    private void done() {
        String text = said.toString().trim();
        List<String> candidates = segments == 1 && alternatives != null ? alternatives : null;
        stop();
        if (text.isEmpty()) {
            toast(activity.getString(R.string.voice_no_match));
            return;
        }
        if (candidates == null) {
            candidates = new ArrayList<>();
            candidates.add(text);
        }
        act(candidates);
    }

    void stop() {
        if (recognizer != null) android.util.Log.i(TAG, "stop");
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private void showDialog() {
        float dp = activity.getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        int pad = (int) (24 * dp);
        panel.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(activity);
        title.setText(R.string.voice_listening);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_mic, 0, 0, 0);
        title.setCompoundDrawablePadding((int) (12 * dp));
        title.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(title);
        heard = new TextView(activity);
        heard.setTextColor(0xFFB8C4CE);
        heard.setTextSize(22);
        heard.setPadding(0, (int) (14 * dp), 0, 0);
        panel.addView(heard);

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setOnCancelListener(d -> stop());
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.y = (int) (90 * dp);
            lp.width = (int) (560 * dp);
            w.setAttributes(lp);
        }
        dialog.show();
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {
            android.util.Log.i(TAG, "ready");
            micOpened = true;
            if (followUpSession) handler.postDelayed(noMoreSpeech, MORE_MS);
        }
        @Override public void onBeginningOfSpeech() {
            android.util.Log.i(TAG, "speech begins");
            handler.removeCallbacks(noMoreSpeech);
        }
        @Override public void onRmsChanged(float rmsdB) {
            // Level of what the microphone hears, logged when it reaches a new high: silence stays near -2
            if (rmsdB > loudest + 2) {
                loudest = rmsdB;
                android.util.Log.i(TAG, "level " + rmsdB);
            }
        }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { android.util.Log.i(TAG, "speech ends"); }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override public void onPartialResults(Bundle partial) {
            ArrayList<String> words = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (heard != null && words != null && !words.isEmpty()) {
                handler.removeCallbacks(noMoreSpeech);
                heard.setText((said + " " + words.get(0)).trim());
            }
        }

        @Override public void onResults(Bundle results) {
            ArrayList<String> words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            android.util.Log.i(TAG, "results " + words);
            handler.removeCallbacks(noMoreSpeech);
            if (words != null && !words.isEmpty() && !words.get(0).trim().isEmpty()) {
                if (said.length() > 0) said.append(' ');
                said.append(words.get(0).trim());
                segments++;
                alternatives = words;
                if (heard != null) heard.setText(said);
            }
            // Hands-free: the speaker may only have paused; listen on until the deadline or a quiet follow-up
            boolean timeLeft = android.os.SystemClock.uptimeMillis() - listenStart < LISTEN_MS - MORE_MS;
            if (finishing || !timeLeft) done();
            else listen(said.length() > 0);
        }

        @Override public void onError(int error) {
            android.util.Log.i(TAG, "error " + error + " finishing=" + finishing);
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    || error == SpeechRecognizer.ERROR_CLIENT && finishing) {
                // Silence: after some speech that is the end; before any, keep listening until the deadline
                boolean timeLeft = android.os.SystemClock.uptimeMillis() - listenStart < LISTEN_MS - MORE_MS;
                if (said.length() > 0 || finishing || !timeLeft) done();
                else listen(false);
                return;
            }
            stop();
            // The microphone never opened (the recogniser reports that as a network, audio or client error): no
            // amount of retrying helps, so offer the keyboard instead
            if (!micOpened && said.length() == 0 && (error == SpeechRecognizer.ERROR_NETWORK
                    || error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_CLIENT)) {
                typeSearch(R.string.voice_type_title);
                return;
            }
            int msg;
            switch (error) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = R.string.voice_no_match; break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                case SpeechRecognizer.ERROR_SERVER: msg = R.string.voice_network; break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: msg = R.string.voice_permission; break;
                case 12: /* ERROR_LANGUAGE_NOT_SUPPORTED */
                case 13: /* ERROR_LANGUAGE_UNAVAILABLE */ msg = R.string.voice_language; break;
                default:
                    toast(activity.getString(R.string.voice_error, error));
                    return;
            }
            toast(activity.getString(msg));
        }
    };

    /**
     * Search by keyboard, for a TV whose microphone cannot be used: the same glass panel with a text field, and
     * what is typed goes the same way as speech (an app's name opens it, anything else is searched on YouTube).
     */
    private void typeSearch(int titleRes) {
        float dp = activity.getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        int pad = (int) (24 * dp);
        panel.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(activity);
        title.setText(titleRes);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        panel.addView(title);
        android.widget.EditText field = new android.widget.EditText(activity);
        field.setHint(R.string.voice_type_hint);
        field.setHintTextColor(0xFF8A96A3);
        field.setTextColor(0xFFFFFFFF);
        field.setTextSize(22);
        field.setSingleLine(true);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (14 * dp);
        panel.addView(field, lp);

        Dialog typing = new Dialog(activity);
        typing.requestWindowFeature(Window.FEATURE_NO_TITLE);
        typing.setContentView(panel);
        field.setOnEditorActionListener((v, action, event) -> {
            String text = v.getText().toString().trim();
            if (text.isEmpty()) return true;
            typing.dismiss();
            List<String> candidates = new ArrayList<>();
            candidates.add(text);
            act(candidates);
            return true;
        });
        Window w = typing.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams wl = w.getAttributes();
            wl.y = (int) (90 * dp);
            wl.width = (int) (560 * dp);
            w.setAttributes(wl);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        typing.show();
        field.requestFocus();
    }

    /** Opens the app whose name was said, else searches YouTube for what was heard. */
    private void act(List<String> candidates) {
        PackageManager pm = activity.getPackageManager();
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        for (String said : candidates) {
            String s = fold(said);
            if (s.isEmpty()) continue;
            for (ResolveInfo ri : apps) {
                if (ri.activityInfo == null || ri.activityInfo.packageName.equals(activity.getPackageName())) continue;
                String name = fold(String.valueOf(ri.loadLabel(pm)));
                if (name.length() >= 2 && (s.equals(name) || s.contains(name) || name.equals(s))) {
                    Intent open = pm.getLeanbackLaunchIntentForPackage(ri.activityInfo.packageName);
                    if (open == null) open = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                    if (open != null) {
                        toast(activity.getString(R.string.voice_opening, ri.loadLabel(pm)));
                        activity.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        return;
                    }
                }
            }
        }
        String query = candidates.get(0).trim();
        Uri uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query));
        for (String pkg : YOUTUBE) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                toast(activity.getString(R.string.voice_searching, query));
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        toast(activity.getString(R.string.voice_no_match));
    }

    /** Lower case without Vietnamese marks or spaces, so "FPT play" matches "FPT Play" and "tivi 360" fails kindly. */
    static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", "");
    }

    private void toast(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }
}
