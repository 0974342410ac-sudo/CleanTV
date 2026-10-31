package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Volume keys from an HDMI device on an MStar TV. A box whose remote sends CEC volume (the onn 4K box) reaches tvos, which logs
 * them and goes no further: neither Android nor TvCecManager hears of them. So while an input is showing this follows tvos's
 * log and changes the TV's volume. Needs READ_LOGS, which a launcher in /system/priv-app has; without it nothing matches.
 */
final class HdmiCecVolume {
    /** A CEC message from any device to the TV (logical address 0): User Control Pressed, volume up, down or mute. */
    private static final Pattern PRESS = Pattern.compile("Length:\\[3\\] [0-9a-f]0 44 4([123])");

    private final AudioManager audio;
    private Process logcat;

    HdmiCecVolume(Context context) {
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    synchronized void start() {
        if (logcat != null) return;
        final Process process;
        try {
            process = new ProcessBuilder("logcat", "-v", "raw", "-T", "1", "-s", "tvos:I").redirectErrorStream(true).start();
        } catch (IOException e) {
            return;
        }
        logcat = process;
        Thread reader = new Thread(() -> follow(process), "HdmiCecVolume");
        reader.setDaemon(true);
        reader.start();
    }

    synchronized void stop() {
        if (logcat != null) logcat.destroy();
        logcat = null;
    }

    private void follow(Process process) {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            // -T 1 starts with the newest old line, which may be an old press
            lines.readLine();
            String line;
            while ((line = lines.readLine()) != null) {
                Matcher press = PRESS.matcher(line);
                if (press.find()) adjust(press.group(1).charAt(0));
            }
        } catch (IOException e) {
            // Stopped; the next window change starts it again
        }
        synchronized (this) {
            if (logcat == process) logcat = null;
        }
    }

    private void adjust(char key) {
        int direction = key == '1' ? AudioManager.ADJUST_RAISE : key == '2' ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_TOGGLE_MUTE;
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
    }
}
