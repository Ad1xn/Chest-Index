package dev.adrian.chestindex.core.util;

import java.util.Locale;

/**
 * How typed text is matched against a registry id.
 *
 * <p>The ids being searched are {@code minecraft:light_blue_wool}. What people
 * type is {@code blue wool} - the name the game shows them, with the spaces
 * they read. A plain substring test answers "no match" to that, which is the
 * mod telling a player that the thing they are looking at does not exist.
 *
 * <p>So the needle is split on whitespace and every word has to appear
 * somewhere in the id. That covers the spaced name, the underscored id, and a
 * half-remembered word order - {@code wool blue} finds it too - while still
 * narrowing as more is typed, which is the whole job of a search box.
 *
 * <p>The alternative, replacing spaces with underscores, only works when the
 * words are in the id's order and adjacent in it. {@code blue wool} would find
 * {@code blue_wool} and miss {@code light_blue_wool}, which is the case that
 * prompted this.
 */
public final class SearchText {

    private SearchText() {}

    /** No words, which matches everything. */
    private static final String[] NONE = new String[0];

    /**
     * The words in a query, lower-cased.
     *
     * <p>Split once per query rather than per candidate: a world with a large
     * palette runs this against thousands of ids, and re-splitting the same
     * two words for each of them is the difference the search box was hitching
     * on.
     */
    public static String[] tokens(String text) {
        if (text == null) return NONE;
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return NONE;
        return trimmed.split("\\s+");
    }

    /** Whether an id contains every word of the query. */
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
