package dev.adrian.chestindex.core.model;

import java.util.Locale;

public final class StackDetail {

    private StackDetail() {}

    public static final String ENCHANTMENT = "ench:";

    public static final String POTION = "potion:";

    public static final String LORE = "lore:";

    public static final String NAME = "name:";

    public static final int MAX_TEXT = 64;

    public static final int MAX_LORE_LINES = 4;

    public static String enchantment(String id) {
        return of(ENCHANTMENT, id);
    }

    public static String potion(String id) {
        return of(POTION, id);
    }

    public static String lore(String text) {
        return of(LORE, text);
    }

    public static String name(String text) {
        return of(NAME, text);
    }

    public static String of(String prefix, String value) {
        if (value == null) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_TEXT) trimmed = trimmed.substring(0, MAX_TEXT);
        return prefix + trimmed;
    }

    public static boolean is(String detail, String prefix) {
        return detail != null && detail.startsWith(prefix);
    }

    public static String value(String detail) {
        if (detail == null) return null;
        for (String prefix : new String[] {ENCHANTMENT, POTION, LORE, NAME}) {
            if (detail.startsWith(prefix)) return detail.substring(prefix.length());
        }
        return detail;
    }
}
