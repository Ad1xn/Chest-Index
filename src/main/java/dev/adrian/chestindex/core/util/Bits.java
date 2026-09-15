package dev.adrian.chestindex.core.util;

import java.util.Set;

/**
 * A bitset over small non-negative ints, held as a plain {@code long[]}.
 *
 * <p>Exists for one reason: palette ids are small dense ints, and asking
 * {@code Set<Integer>.contains(int)} boxes on every call. A query tests item
 * membership once per stack, so on a world with sixty thousand containers of
 * fourteen stacks that is most of a million {@link Integer} allocations per
 * keystroke - which measured as the single largest cost in the search path.
 *
 * <p>A {@code null} mask means "no constraint" rather than "matches nothing",
 * matching how empty filter sets read in {@link
 * dev.adrian.chestindex.core.index.IndexQuery}, so callers can skip the test
 * entirely instead of consulting an all-ones mask.
 */
public final class Bits {

    private Bits() {}

    /**
     * Packs {@code ids} into a mask, or returns null when there is nothing to
     * constrain.
     *
     * <p>Negative ids are dropped rather than rejected: {@code -1} is what
     * {@link dev.adrian.chestindex.core.store.StringPalette#lookup} returns
     * for a string it has never interned, and a query naming an unknown item
     * should find nothing, not throw.
     */
    public static long[] of(Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) return null;

        int max = -1;
        for (Integer id : ids) {
            if (id != null && id > max) max = id;
        }
        if (max < 0) return EMPTY;

        long[] mask = new long[(max >> 6) + 1];
        for (Integer id : ids) {
            if (id == null || id < 0) continue;
            mask[id >> 6] |= 1L << (id & 63);
        }
        return mask;
    }

    /**
     * A mask that matches nothing, distinct from null.
     *
     * <p>Needed because "the caller asked for ids, and none of them are
     * representable" must exclude everything, whereas null means the caller
     * asked for no constraint at all.
     */
    public static final long[] EMPTY = new long[0];

    /** Whether {@code id} is set. Out-of-range and negative ids are simply absent. */
    public static boolean test(long[] mask, int id) {
        if (id < 0) return false;
        int word = id >> 6;
        return word < mask.length && (mask[word] & (1L << (id & 63))) != 0;
    }
}
