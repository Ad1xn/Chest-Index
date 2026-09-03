package dev.adrian.chesttracker.core.model;

import dev.adrian.chesttracker.core.util.BlockKey;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ContainerRecord(
        long pos,
        int dimensionId,
        int typeId,
        Origin origin,
        UUID owner,
        boolean unlooted,
        boolean contentsKnown,
        String customName,
        long lastSeenTick,
        List<StackEntry> contents
) {

    public ContainerRecord {
        Objects.requireNonNull(origin, "origin");
        contents = contents == null ? List.of() : List.copyOf(contents);
        if (!contentsKnown && !contents.isEmpty()) {
            throw new IllegalArgumentException("contentsKnown=false but " + contents.size() + " stacks supplied");
        }
    }

    public static ContainerRecord locationOnly(long pos, int dimensionId, int typeId, Origin origin, long tick) {
        return new ContainerRecord(pos, dimensionId, typeId, origin, null, false, false, null, tick, List.of());
    }

    public long chunkKey() {
        return BlockKey.chunkOf(pos);
    }

    public boolean isEmpty() {
        return contentsKnown && contents.isEmpty();
    }

    public int totalItems() {
        int total = 0;
        for (StackEntry entry : contents) total += entry.count();
        return total;
    }

    public boolean containsItem(int itemId) {
        for (StackEntry entry : contents) {
            if (entry.itemId() == itemId) return true;
        }
        return false;
    }

    public ContainerRecord withContents(List<StackEntry> newContents, String newCustomName, long tick) {
        return new ContainerRecord(pos, dimensionId, typeId, origin, owner, unlooted, true, newCustomName, tick, newContents);
    }

    public ContainerRecord withOrigin(Origin newOrigin, UUID newOwner) {
        return new ContainerRecord(pos, dimensionId, typeId, origin.merge(newOrigin),
                newOwner != null ? newOwner : owner, unlooted, contentsKnown, customName, lastSeenTick, contents);
    }

    public ContainerRecord withUnlooted(boolean nowUnlooted) {
        return new ContainerRecord(pos, dimensionId, typeId, origin, owner, nowUnlooted, contentsKnown, customName, lastSeenTick, contents);
    }
}
