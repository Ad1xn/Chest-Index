package dev.adrian.chestindex.core.util;

import java.util.Locale;

public final class SearchText {
    private SearchText() {}

    private static final String[] NONE = new String[0];

    public static String[] tokens(String text) {
        if (text == null) return NONE;
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return NONE;
        return trimmed.split("\\s+");
    }

    public static boolean matches(String id, String[] tokens) {
        if (tokens.length == 0) return true;
        if (id == null) return false;
        String candidate = id.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (!candidate.contains(token)) return false;
        }
        return true;
    }
}
