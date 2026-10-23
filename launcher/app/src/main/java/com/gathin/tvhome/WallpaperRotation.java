package com.gathin.tvhome;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

final class WallpaperRotation {
    static List<File> images(File directory) {
        List<File> result = new ArrayList<>();
        File[] entries = directory == null ? null : directory.listFiles();
        if (entries == null) return result;
        Arrays.sort(entries, Comparator.comparing(File::getName));
        for (File entry : entries) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (entry.isFile() && entry.length() > 0 && entry.length() <= 10 * 1024 * 1024
                    && (name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".png") || name.endsWith(".webp"))) {
                result.add(entry);
                if (result.size() == 100) break;
            }
        }
        return result;
    }

    static File find(List<File> images, String name) {
        for (File image : images) if (image.getName().equals(name)) return image;
        return null;
    }

    static File pick(List<File> images, String previous, Random random) {
        List<File> candidates = new ArrayList<>(images);
        if (candidates.size() > 1) candidates.removeIf(image -> image.getName().equals(previous));
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /** Time-of-day slots for wallpaper_by_time, as the greeting splits the day: 5h, 12h, 17h, 22h. */
    private static final String[] SLOTS = {"morning", "afternoon", "evening", "night"};

    static int slot(int hour) {
        if (hour >= 5 && hour < 12) return 0;
        if (hour >= 12 && hour < 17) return 1;
        if (hour >= 17 && hour < 22) return 2;
        return 3;
    }

    /**
     * The image named after the slot of this hour (morning.jpg, afternoon.png ...). A slot without one takes the
     * image of the slot before it, so a single night.jpg holds until morning; no slot image at all: the first image.
     */
    static File forHour(List<File> images, int hour) {
        int s = slot(hour);
        for (int i = 0; i < SLOTS.length; i++) {
            String want = SLOTS[(s - i + SLOTS.length) % SLOTS.length];
            for (File image : images) {
                String name = image.getName().toLowerCase(Locale.ROOT);
                int dot = name.lastIndexOf('.');
                if ((dot < 0 ? name : name.substring(0, dot)).equals(want)) return image;
            }
        }
        return images.isEmpty() ? null : images.get(0);
    }

    static boolean due(long previous, long now, long interval) {
        return interval > 0 && (previous == 0 || now < previous || now - previous >= interval);
    }
}
