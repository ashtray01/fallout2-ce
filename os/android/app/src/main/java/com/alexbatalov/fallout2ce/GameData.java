package com.alexbatalov.fallout2ce;

import java.io.File;
import java.util.Locale;

/** Rules shared by startup validation and the SAF importer. */
final class GameData {
    static final String IMPORT_MARKER = ".import-in-progress";

    static File find(File directory, String name) {
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.getName().equalsIgnoreCase(name)) return child;
            }
        }
        return null;
    }

    static boolean hasArchives(File directory) {
        if (directory == null) return false;
        File master = find(directory, "master.dat");
        File critter = find(directory, "critter.dat");
        return master != null && master.isFile() && master.canRead() && master.length() > 0
            && critter != null && critter.isFile() && critter.canRead() && critter.length() > 0;
    }

    static boolean isReady(File directory) {
        return hasArchives(directory) && !new File(directory, IMPORT_MARKER).exists();
    }

    // Repair only Windows absolute paths with known local equivalents.
    static String portableConfig(String text, File directory) {
        String section = "";
        StringBuilder result = new StringBuilder();
        for (String line : text.split("(?<=\n)", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.contains("]")) {
                section = trimmed.substring(1, trimmed.indexOf(']')).toLowerCase(Locale.ROOT);
            }
            int equals = line.indexOf('=');
            if (equals >= 0 && !trimmed.startsWith(";") && !trimmed.startsWith("#")) {
                String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(equals + 1).trim().replace('\\', '/');
                if (value.matches("(?i)^[a-z]:/.*") || value.startsWith("//")) {
                    File replacement = null;
                    if (section.equals("system")) {
                        if (key.equals("master_dat")) replacement = find(directory, "master.dat");
                        if (key.equals("critter_dat")) replacement = find(directory, "critter.dat");
                        if (key.equals("master_patches") || key.equals("critter_patches")) {
                            if (value.replaceAll("/+$", "").toLowerCase(Locale.ROOT).endsWith("/data")) {
                                replacement = find(directory, "data");
                            }
                        }
                    } else if (section.equals("sound") && (key.equals("music_path1") || key.equals("music_path2"))) {
                        File data = find(directory, "data");
                        File sound = data == null ? null : find(data, "sound");
                        replacement = sound == null ? null : find(sound, "music");
                        if (replacement == null) {
                            sound = find(directory, "sound");
                            replacement = sound == null ? null : find(sound, "music");
                        }
                    }
                    if (replacement != null) {
                        String path = directory.toURI().relativize(replacement.toURI()).getPath();
                        line = line.substring(0, equals + 1) + path
                            + (line.endsWith("\r\n") ? "\r\n" : line.endsWith("\n") ? "\n" : "");
                    }
                }
            }
            result.append(line);
        }
        return result.toString();
    }
}
