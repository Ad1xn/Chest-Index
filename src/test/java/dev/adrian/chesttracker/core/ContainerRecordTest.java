package dev.adrian.chesttracker.core;

import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackEntry;
import dev.adrian.chesttracker.core.util.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContainerRecordTest {

    private static final long POS = BlockKey.pack(10, 64, -20);

    private static ContainerRecord chest(long tick, StackEntry... contents) {
        return new ContainerRecord(POS, 0, 5, Origin.PLAYER_PLACED, null,
                false, true, null, tick, List.of(contents));
    }

    @Test
    void aRereadAtALaterTickIsNotAChange() {
        assertTrue(chest(100, new StackEntry(1, 64)).sameDataAs(chest(999, new StackEntry(1, 64))));
    }

    @Test
    void aDifferentCountIsAChange() {
        assertFalse(chest(100, new StackEntry(1, 64)).sameDataAs(chest(100, new StackEntry(1, 63))));
    }

    @Test
    void addedAndRemovedStacksAreChanges() {
        ContainerRecord empty = chest(100);
        ContainerRecord full = chest(100, new StackEntry(1, 1));

        assertFalse(empty.sameDataAs(full));
        assertFalse(full.sameDataAs(empty));
    }

    @Test
    void aDifferentItemAtTheSameCountIsAChange() {
        assertFalse(chest(100, new StackEntry(1, 64)).sameDataAs(chest(100, new StackEntry(2, 64))));
    }

    @Test
    void stackOrderIsAChange() {
        ContainerRecord a = chest(100, new StackEntry(1, 1), new StackEntry(2, 1));
        ContainerRecord b = chest(100, new StackEntry(2, 1), new StackEntry(1, 1));

        assertFalse(a.sameDataAs(b));
    }

    @Test
    void ownershipAndClassificationAreChanges() {
        ContainerRecord base = chest(100);
        UUID owner = UUID.randomUUID();

        assertFalse(base.sameDataAs(base.withOrigin(Origin.NATURAL, owner)));
        assertFalse(base.sameDataAs(base.withUnlooted(true)));
    }

    @Test
    void anUnknownContentsRecordDiffersFromAKnownEmptyOne() {
        ContainerRecord unknown = ContainerRecord.locationOnly(POS, 0, 5, Origin.NATURAL, 100);
        ContainerRecord knownEmpty = chest(100);

        assertFalse(unknown.sameDataAs(knownEmpty));
    }

    @Test
    void nullIsNeverTheSame() {
        assertFalse(chest(100).sameDataAs(null));
    }
}
