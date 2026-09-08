package dev.adrian.chesttracker.core.model;

import java.util.List;

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

    public boolean isNested() {
        return depth > 0;
    }

    public boolean hasAnyDetail(java.util.function.IntPredicate wanted) {
        for (int id : details) {
            if (wanted.test(id)) return true;
        }
        return false;
    }
}
