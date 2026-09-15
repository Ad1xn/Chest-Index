package dev.adrian.chestindex.core.model;

import java.util.List;

/**
 * One item stack found inside a container, flattened out of any nesting.
 *
 * @param itemId     palette id of the item's registry name
 * @param count      how many
 * @param depth      0 for a stack sitting directly in the container, 1 for one
 *                   inside a shulker box in that container, and so on
 * @param customName anvil-renamed display name as plain text, or null. Kept so
 *                   searching for "spare pickaxe" works; rendering the real
 *                   styled name is the UI's job
 * @param details    palette ids of what distinguishes <em>this</em> stack from
 *                   any other of the same item - the enchantments on it, the
 *                   potion in it, the lore written on it. Empty for the vast
 *                   majority of stacks, which are plain
 */
public record StackEntry(int itemId, int count, int depth, String customName, List<Integer> details) {

    public StackEntry {
        if (count < 0) throw new IllegalArgumentException("count must not be negative: " + count);
        if (depth < 0) throw new IllegalArgumentException("depth must not be negative: " + depth);
        details = details == null ? List.of() : List.copyOf(details);
    }

    public StackEntry(int itemId, int count) {
        this(itemId, count, 0, null, List.of());
    }

    public StackEntry(int itemId, int count, int depth) {
        this(itemId, count, depth, null, List.of());
    }

    public StackEntry(int itemId, int count, int depth, String customName) {
        this(itemId, count, depth, customName, List.of());
    }

    /** True if this stack is inside another container rather than loose in this one. */
    public boolean isNested() {
        return depth > 0;
    }

    /**
     * Whether this stack carries one of these details.
     *
     * <p>Ids rather than text, resolved against the palette by whoever built
     * the query - the same trick the item filter uses, and for the same reason:
     * comparing ints costs nothing and comparing strings, once per stack per
     * keystroke, costs a great deal.
     */
    public boolean hasAnyDetail(java.util.function.IntPredicate wanted) {
        for (int id : details) {
            if (wanted.test(id)) return true;
        }
        return false;
    }
}
