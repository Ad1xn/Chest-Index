package dev.adrian.chesttracker.core.model;

public record StackEntry(int itemId, int count, int depth, String customName) {

    public StackEntry {
        if (count < 0) throw new IllegalArgumentException("count must not be negative: " + count);
        if (depth < 0) throw new IllegalArgumentException("depth must not be negative: " + depth);
    }

    public StackEntry(int itemId, int count) {
        this(itemId, count, 0, null);
    }

    public StackEntry(int itemId, int count, int depth) {
        this(itemId, count, depth, null);
    }

    public boolean isNested() {
        return depth > 0;
    }
}
