package dev.adrian.chesttracker.server;

import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackEntry;
import dev.adrian.chesttracker.core.util.BlockKey;
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

    @Test
    void dimensionsCountSeparately() {
        tracker.record(DIM, chest(1));

        assertNotEquals(0L, tracker.generation(DIM));
        assertEquals(0L, tracker.generation("minecraft:the_nether"));
    }
}
