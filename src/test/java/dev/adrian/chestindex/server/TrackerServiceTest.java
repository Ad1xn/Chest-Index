package dev.adrian.chestindex.server;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.util.BlockKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TrackerServiceTest {
    private static final String DIM = "minecraft:overworld";
    private static final long POS = BlockKey.pack(4, 64, 8);

    private TrackerService tracker;

    @BeforeEach
    void setUp(@TempDir Path storage) {
        tracker = new TrackerService(storage);
    }

    private ContainerRecord chest(long tick, StackEntry... contents) {
        return new ContainerRecord(POS, tracker.palette().intern(DIM),
                tracker.palette().intern("minecraft:chest"),
                Origin.PLAYER_PLACED, null, false, true, null, tick, List.of(contents));
    }

    @Test
    void anUnknownDimensionStartsAtZero() {
        assertEquals(0L, tracker.generation("minecraft:the_nether"));
    }

    @Test
    void recordingSomethingNewIsAChange() {
        long before = tracker.generation(DIM);
        tracker.record(DIM, chest(1, new StackEntry(1, 5)));

        assertNotEquals(before, tracker.generation(DIM));
    }

    @Test
    void rereadingTheSameContainerIsNotAChange() {
        tracker.record(DIM, chest(1, new StackEntry(1, 5)));
        long after = tracker.generation(DIM);

        tracker.record(DIM, chest(2, new StackEntry(1, 5)));
        tracker.record(DIM, chest(3, new StackEntry(1, 5)));

        assertEquals(after, tracker.generation(DIM));
    }

    @Test
    void changedContentsAreAChange() {
        tracker.record(DIM, chest(1, new StackEntry(1, 5)));
        long after = tracker.generation(DIM);

        tracker.record(DIM, chest(2, new StackEntry(1, 4)));

        assertNotEquals(after, tracker.generation(DIM));
    }

    @Test
    void removingSomethingIsAChange() {
        tracker.record(DIM, chest(1));
        long after = tracker.generation(DIM);

        tracker.remove(DIM, POS);

        assertNotEquals(after, tracker.generation(DIM));
    }

    @Test
    void removingNothingIsNotAChange() {
        tracker.record(DIM, chest(1));
        long after = tracker.generation(DIM);

        tracker.remove(DIM, BlockKey.pack(999, 64, 999));

        assertEquals(after, tracker.generation(DIM));
    }

    @Test
    void reconcilingIsAChangeOnlyWhenSomethingWasDropped() {
        tracker.record(DIM, chest(1));
        long after = tracker.generation(DIM);

        tracker.reconcileChunk(DIM, BlockKey.chunkOf(POS), Set.of(POS));
        assertEquals(after, tracker.generation(DIM));

        tracker.reconcileChunk(DIM, BlockKey.chunkOf(POS), Set.of());
        assertNotEquals(after, tracker.generation(DIM));
    }

    private ContainerRecord placedBy(java.util.UUID owner, long tick, StackEntry... contents) {
        return new ContainerRecord(POS, tracker.palette().intern(DIM),
                tracker.palette().intern("minecraft:chest"),
                Origin.PLAYER_PLACED, owner, false, true, null, tick, List.of(contents));
    }

    private ContainerRecord observed(long tick, StackEntry... contents) {
        return new ContainerRecord(POS, tracker.palette().intern(DIM),
                tracker.palette().intern("minecraft:chest"),
                Origin.UNKNOWN, null, false, true, null, tick, List.of(contents));
    }

    @Test
    void aRereadDoesNotErasePlacementOrOwner() {
        java.util.UUID owner = java.util.UUID.randomUUID();
        tracker.record(DIM, placedBy(owner, 1));

        tracker.record(DIM, observed(2, new StackEntry(1, 5)));

        ContainerRecord stored = tracker.index(DIM).get(POS);
        assertEquals(Origin.PLAYER_PLACED, stored.origin());
        assertEquals(owner, stored.owner());
        assertEquals(1, stored.contents().size());
    }

    @Test
    void evidenceOverrulesAPlacementGuess() {
        tracker.record(DIM, observed(1));
        assertEquals(Origin.PLAYER_PLACED, tracker.index(DIM).get(POS).origin());
        assertTrue(tracker.index(DIM).get(POS).isPlacementGuess());

        tracker.record(DIM, new ContainerRecord(POS, tracker.palette().intern(DIM),
                tracker.palette().intern("minecraft:chest"), Origin.NATURAL, null,
                true, false, null, 2, List.of()));

        assertEquals(Origin.NATURAL, tracker.index(DIM).get(POS).origin());
    }

    @Test
    void openingAGeneratedChestStopsItBeingUnlooted() {
        tracker.record(DIM, new ContainerRecord(POS, tracker.palette().intern(DIM),
                tracker.palette().intern("minecraft:chest"), Origin.NATURAL, null,
                true, false, null, 1, List.of()));

        tracker.record(DIM, observed(2, new StackEntry(1, 3)));

        ContainerRecord stored = tracker.index(DIM).get(POS);
        assertFalse(stored.unlooted());
        assertTrue(stored.contentsKnown());
        assertEquals(Origin.NATURAL, stored.origin());
    }

    @Test
    void inheritedClassificationIsNotReportedAsAChange() {
        java.util.UUID owner = java.util.UUID.randomUUID();
        tracker.record(DIM, placedBy(owner, 1, new StackEntry(1, 5)));
        long after = tracker.generation(DIM);

        tracker.record(DIM, observed(2, new StackEntry(1, 5)));

        assertEquals(after, tracker.generation(DIM));
    }

    @Test
    void dimensionsCountSeparately() {
        tracker.record(DIM, chest(1));

        assertNotEquals(0L, tracker.generation(DIM));
        assertEquals(0L, tracker.generation("minecraft:the_nether"));
    }

    @Test
    void aRereadStillRefreshesWhenItWasLastSeen() {
        tracker.record(DIM, chest(1, new StackEntry(1, 5)));
        tracker.record(DIM, chest(9, new StackEntry(1, 5)));

        assertEquals(9L, tracker.index(DIM).get(POS).lastSeenTick());
    }

    @Test
    void aRereadLeavesTheSummaryCacheAlone() {
        tracker.record(DIM, chest(1, new StackEntry(1, 5)));
        var query = dev.adrian.chestindex.core.index.IndexQuery.builder().build();
        var first = tracker.index(DIM).summarise(query);

        tracker.record(DIM, chest(2, new StackEntry(1, 5)));

        assertSame(first, tracker.index(DIM).summarise(query));
    }

    @Test
    void remappingKeepsWhatDistinguishesAStack() {
        var foreign = new dev.adrian.chestindex.core.store.StringPalette();
        int itemId = foreign.intern("minecraft:diamond_pickaxe");
        int detailId = foreign.intern("ench:mending");
        ContainerRecord source = new ContainerRecord(POS, foreign.intern(DIM),
                foreign.intern("minecraft:chest"), Origin.PLAYER_PLACED, null, false, true,
                null, 1, List.of(new StackEntry(itemId, 1, 0, null, List.of(detailId))));

        ContainerRecord remapped = tracker.remap(source, foreign, DIM);

        List<Integer> details = remapped.contents().get(0).details();
        assertEquals(1, details.size());
        assertEquals("ench:mending", tracker.palette().value(details.get(0)));
    }

    @Test
    void aFormat3IndexLoadsWithNoUndecidedOriginAndNamesItsChunksForRefinement(@TempDir Path storage)
            throws java.io.IOException {
        long guessPos = BlockKey.pack(40, 64, 40);
        long lootPos = BlockKey.pack(41, 64, 40);
        long placedPos = BlockKey.pack(-20, 64, 3);
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(raw))) {
            out.writeInt(0x43544958);
            out.writeInt(3);
            out.writeInt(0);
            writeVarInt(out, 2);
            out.writeUTF(DIM);
            out.writeUTF("minecraft:chest");
            writeVarInt(out, 3);
            legacyRecord(out, guessPos, Origin.UNKNOWN.ordinal() | (1 << 3));
            legacyRecord(out, lootPos, Origin.UNKNOWN.ordinal() | (1 << 2));
            legacyRecord(out, placedPos, Origin.PLAYER_PLACED.ordinal() | (1 << 3));
        }
        java.nio.file.Files.write(storage.resolve("minecraft.overworld.idx"), raw.toByteArray());

        TrackerService loaded = new TrackerService(storage);
        loaded.load();

        for (ContainerRecord record : loaded.index(DIM).all()) {
            assertNotEquals(Origin.UNKNOWN, record.origin());
        }
        assertTrue(loaded.index(DIM).get(guessPos).isPlacementGuess());
        assertEquals(Origin.NATURAL, loaded.index(DIM).get(lootPos).origin());
        assertFalse(loaded.index(DIM).get(placedPos).isPlacementGuess());

        assertEquals(java.util.Map.of(DIM, Set.of(BlockKey.chunkOf(guessPos))), loaded.takeLegacyGuesses());
        assertTrue(loaded.takeLegacyGuesses().isEmpty());
    }

    private static void legacyRecord(java.io.DataOutputStream out, long pos, int flags) throws java.io.IOException {
        out.writeLong(pos);
        writeVarInt(out, 0);
        writeVarInt(out, 1);
        out.writeByte(flags);
        writeVarLong(out, 7L);
        writeVarInt(out, 0);
    }

    private static void writeVarInt(java.io.DataOutputStream out, int value) throws java.io.IOException {
        writeVarLong(out, Integer.toUnsignedLong((value << 1) ^ (value >> 31)));
    }

    private static void writeVarLong(java.io.DataOutputStream out, long value) throws java.io.IOException {
        long remaining = value;
        while ((remaining & ~0x7FL) != 0) {
            out.writeByte((int) (remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.writeByte((int) remaining);
    }
}
