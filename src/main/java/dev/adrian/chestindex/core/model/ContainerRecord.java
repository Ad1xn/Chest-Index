package dev.adrian.chestindex.core.model;

import dev.adrian.chestindex.core.util.BlockKey;

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
        List<StackEntry> contents,
        boolean originInferred,
        boolean inMachine
) {
    public ContainerRecord {
        Objects.requireNonNull(origin, "origin");
        if (origin == Origin.UNKNOWN) {
            origin = unlooted ? Origin.NATURAL : Origin.PLAYER_PLACED;
            originInferred = !unlooted;
        } else if (origin == Origin.NATURAL) {
            originInferred = false;
        }
        contents = contents == null ? List.of() : List.copyOf(contents);
        if (!contentsKnown && !contents.isEmpty()) {
            throw new IllegalArgumentException("contentsKnown=false but " + contents.size() + " stacks supplied");
        }
    }

    public ContainerRecord(long pos, int dimensionId, int typeId, Origin origin, UUID owner,
                           boolean unlooted, boolean contentsKnown, String customName,
                           long lastSeenTick, List<StackEntry> contents) {
        this(pos, dimensionId, typeId, origin, owner, unlooted, contentsKnown, customName,
                lastSeenTick, contents, false);
    }

    public ContainerRecord(long pos, int dimensionId, int typeId, Origin origin, UUID owner,
                           boolean unlooted, boolean contentsKnown, String customName,
                           long lastSeenTick, List<StackEntry> contents, boolean originInferred) {
        this(pos, dimensionId, typeId, origin, owner, unlooted, contentsKnown, customName,
                lastSeenTick, contents, originInferred, false);
    }

    public ContainerRecord inMachine(boolean nowInMachine) {
        if (nowInMachine == inMachine) return this;
        return new ContainerRecord(pos, dimensionId, typeId, origin, owner, unlooted, contentsKnown, customName,
                lastSeenTick, contents, originInferred, nowInMachine);
    }

    public static Origin decided(boolean generated) {
        return generated ? Origin.NATURAL : Origin.PLAYER_PLACED;
    }

    public boolean isPlacementGuess() {
        return origin == Origin.PLAYER_PLACED && originInferred;
    }

    private static int certainty(Origin origin, boolean inferred) {
        if (origin == Origin.PLAYER_PLACED) return inferred ? 1 : 3;
        return 2;
    }

    private boolean outranks(ContainerRecord other) {
        return certainty(origin, originInferred) > certainty(other.origin, other.originInferred);
    }

    public static ContainerRecord locationOnly(long pos, int dimensionId, int typeId, Origin origin, long tick) {
        return new ContainerRecord(pos, dimensionId, typeId, origin, null, false, false, null, tick, List.of());
    }

    public static ContainerRecord locationOnly(long pos, int dimensionId, int typeId, boolean generated, long tick) {
        return new ContainerRecord(pos, dimensionId, typeId, decided(generated), null, false, false,
                null, tick, List.of(), !generated);
    }

    public ContainerRecord inheriting(ContainerRecord previous) {
        if (previous == null) return this;

        ContainerRecord surer = previous.outranks(this) ? previous : this;
        UUID knownOwner = owner != null ? owner : previous.owner;
        if (surer == this && knownOwner == owner) return this;

        return new ContainerRecord(pos, dimensionId, typeId, surer.origin, knownOwner,
                unlooted, contentsKnown, customName, lastSeenTick, contents, surer.originInferred, inMachine);
    }

    public boolean sameDataAs(ContainerRecord other) {
        return other != null
                && pos == other.pos
                && dimensionId == other.dimensionId
                && typeId == other.typeId
                && origin == other.origin
                && originInferred == other.originInferred
                && inMachine == other.inMachine
                && unlooted == other.unlooted
                && contentsKnown == other.contentsKnown
                && Objects.equals(owner, other.owner)
                && Objects.equals(customName, other.customName)
                && contents.equals(other.contents);
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
        return new ContainerRecord(pos, dimensionId, typeId, origin, owner, unlooted, true, newCustomName, tick,
                newContents, originInferred, inMachine);
    }

    public ContainerRecord withOrigin(Origin newOrigin, UUID newOwner) {
        ContainerRecord seen = new ContainerRecord(pos, dimensionId, typeId, newOrigin, null, unlooted,
                contentsKnown, customName, lastSeenTick, contents, false);
        ContainerRecord surer = outranks(seen) ? this : seen;
        return new ContainerRecord(pos, dimensionId, typeId, surer.origin,
                newOwner != null ? newOwner : owner, unlooted, contentsKnown, customName, lastSeenTick,
                contents, surer.originInferred, inMachine);
    }

    public ContainerRecord withUnlooted(boolean nowUnlooted) {
        return new ContainerRecord(pos, dimensionId, typeId, origin, owner, nowUnlooted, contentsKnown, customName,
                lastSeenTick, contents, originInferred, inMachine);
    }
}
