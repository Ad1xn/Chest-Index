package dev.adrian.chestindex.core.util;

public final class BlockKey {
    private static final int X_BITS = 26;
    private static final int Z_BITS = 26;
    private static final int Y_BITS = 64 - X_BITS - Z_BITS;

    private static final int Y_SHIFT = 0;
    private static final int Z_SHIFT = Y_BITS;
    private static final int X_SHIFT = Y_BITS + Z_BITS;

    private static final long X_MASK = (1L << X_BITS) - 1;
    private static final long Z_MASK = (1L << Z_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;

    public static final int MIN_XZ = -(1 << (X_BITS - 1));
    public static final int MAX_XZ = (1 << (X_BITS - 1)) - 1;
    public static final int MIN_Y = -(1 << (Y_BITS - 1));
    public static final int MAX_Y = (1 << (Y_BITS - 1)) - 1;

    private BlockKey() {}

    public static long pack(int x, int y, int z) {
        return ((long) x & X_MASK) << X_SHIFT
             | ((long) z & Z_MASK) << Z_SHIFT
             | ((long) y & Y_MASK) << Y_SHIFT;
    }

    public static int x(long key) {
        return (int) (key << (64 - X_BITS - X_SHIFT) >> (64 - X_BITS));
    }

    public static int z(long key) {
        return (int) (key << (64 - Z_BITS - Z_SHIFT) >> (64 - Z_BITS));
    }

    public static int y(long key) {
        return (int) (key << (64 - Y_BITS - Y_SHIFT) >> (64 - Y_BITS));
    }

    public static boolean isRepresentable(int x, int y, int z) {
        return x >= MIN_XZ && x <= MAX_XZ
            && z >= MIN_XZ && z <= MAX_XZ
            && y >= MIN_Y && y <= MAX_Y;
    }

    public static long chunkOf(long key) {
        return chunkKey(x(key) >> 4, z(key) >> 4);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    public static int chunkX(long chunkKey) {
        return (int) chunkKey;
    }

    public static int chunkZ(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    public static double distanceSq(long a, long b) {
        double dx = (double) x(a) - x(b);
        double dy = (double) y(a) - y(b);
        double dz = (double) z(a) - z(b);
        return dx * dx + dy * dy + dz * dz;
    }

    public static String toString(long key) {
        return x(key) + ", " + y(key) + ", " + z(key);
    }
}
