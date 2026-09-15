package dev.adrian.chestindex.core.model;

import java.util.Locale;

/**
 * How a stack's distinguishing details are written down.
 *
 * <p>Two stacks of the same pickaxe are the same item and a different thing to
 * look for, and the index is keyed by item. So what makes them different is
 * stored beside the item id as a handful of short strings, interned into the
 * same palette everything else uses - an enchantment costs an int per stack
 * that has one, and nothing at all for the vast majority that do not.
 *
 * <p>Strings rather than a structure because the questions asked of them are
 * word matches, not lookups: nobody types the exact id of an enchantment, they
 * type "mend". A prefix keeps the kinds apart so {@code ench:} can be answered
 * without also matching a line of lore that happens to say "sharpness".
 *
 * <p>The vocabulary lives in {@code core} because three places have to agree on
 * it exactly: the live reader, the offline chunk reader, and the search that
 * resolves a typed term against the palette.
 */
public final class StackDetail {

    private StackDetail() {}

    /** An enchantment on the stack, or stored in it if it is a book. */
    public static final String ENCHANTMENT = "ench:";

    /** The potion a bottle, arrow or cauldron holds. */
    public static final String POTION = "potion:";

    /** One line of written lore. */
    public static final String LORE = "lore:";

    /** A name somebody gave it on an anvil. */
    public static final String NAME = "name:";

    /**
     * How much of one line of text is kept.
     *
     * <p>Lore can be a paragraph, and the index holds one of these per stack
     * per line. What a search needs is the words, and the words that matter are
     * at the front of the line - so it is cut rather than stored whole, which
     * bounds what a single hostile or merely verbose item can add to the file.
     */
    public static final int MAX_TEXT = 64;

    /** As many lines of lore as are worth keeping from one stack. */
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

    /**
     * Writes one detail, lower-cased and cut to length, or null if there is
     * nothing there to write.
     */
    public static String of(String prefix, String value) {
        if (value == null) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_TEXT) trimmed = trimmed.substring(0, MAX_TEXT);
        return prefix + trimmed;
    }

    /** Whether a stored detail is of this kind. */
    public static boolean is(String detail, String prefix) {
        return detail != null && detail.startsWith(prefix);
    }

    /**
     * The detail without its prefix, which is the part a search matches
     * against. Returns the whole thing if it carries no known prefix.
     */
    public static String value(String detail) {
        if (detail == null) return null;
        for (String prefix : new String[] {ENCHANTMENT, POTION, LORE, NAME}) {
            if (detail.startsWith(prefix)) return detail.substring(prefix.length());
        }
        return detail;
    }
}
