package dev.adrian.chestindex.core.util;

import java.util.Set;

public final class Bits {
    private Bits() {}

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

    public static final long[] EMPTY = new long[0];

    public static boolean test(long[] mask, int id) {
        if (id < 0) return false;
        int word = id >> 6;
        return word < mask.length && (mask[word] & (1L << (id & 63))) != 0;
    }
}
