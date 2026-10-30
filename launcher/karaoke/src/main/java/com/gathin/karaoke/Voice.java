package com.gathin.karaoke;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Says the song's name instead of typing it, in Vietnamese. Tries the system's speech screen first (Google TV
 * has one); a TV without it (the XGIMI DOHKO) gets the recogniser service behind a small "listening" dialog,
 * like the launcher's VoiceSearch.
 */
final class Voice {
    static final int REQUEST_SPEECH = 41, REQUEST_PERMISSION = 42;

    /** java.util.function needs Android 7; this app runs on 6. */
    interface Heard {
        void heard(String text);
    }

    private final Activity activity;
    private final Heard onHeard;
    private SpeechRecognizer recognizer;
    private AlertDialog dialog;

    Voice(Activity activity, Heard onHeard) {
        this.activity = activity;
        this.onHeard = onHeard;
    }

    private Intent speechIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, activity.getString(R.string.voice_prompt))
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.getPackageName());
    }

    /**
     * The speech screen to open. The onn box has two (Google TV's Katniss and Google TTS's), and without a choice
     * Android shows its "open with" chooser on every press, so one is picked: Katniss, else the first one.
     */
    private Intent speechScreen() {
        Intent intent = speechIntent();
        List<ResolveInfo> screens = activity.getPackageManager().queryIntentActivities(intent, 0);
        if (screens.isEmpty()) return null;
        ResolveInfo pick = screens.get(0);
        for (ResolveInfo ri : screens) {
            if (KATNISS.equals(ri.activityInfo.packageName)) pick = ri;
        }
        return intent.setClassName(pick.activityInfo.packageName, pick.activityInfo.name);
    }

    private static final String KATNISS = "com.google.android.katniss";

    void start() {
        Intent screen = speechScreen();
        if (screen != null) {
            try {
                activity.startActivityForResult(screen, REQUEST_SPEECH);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            toast(R.string.voice_unavailable);
            return;
        }
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION);
            return;
        }
        listen();
    }

    /** From the activity's onActivityResult. */
    void onResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;
        ArrayList<String> words = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (words != null && !words.isEmpty()) onHeard.heard(words.get(0));
    }

    /** From the activity's onRequestPermissionsResult. */
    void onPermission(boolean granted) {
        if (granted) listen();
        else toast(R.string.voice_permission);
    }

    private void listen() {
        stop();
        recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
        recognizer.setRecognitionListener(listener);
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.voice_prompt)
                .setMessage(" ")
                .setOnCancelListener(d -> stop())
                .show();
        recognizer.startListening(speechIntent());
    }

    void stop() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override public void onPartialResults(Bundle partial) {
            ArrayList<String> words = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (dialog != null && words != null && !words.isEmpty()) dialog.setMessage(words.get(0));
        }

        @Override public void onResults(Bundle results) {
            ArrayList<String> words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            stop();
            if (words != null && !words.isEmpty() && !words.get(0).trim().isEmpty()) onHeard.heard(words.get(0));
            else toast(R.string.voice_no_match);
        }

        @Override public void onError(int error) {
            stop();
            toast(error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                    ? R.string.error_network : R.string.voice_no_match);
        }
    };

    private void toast(int text) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }
}
