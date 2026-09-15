package dev.adrian.chestindex.server;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.core.util.BlockKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineFilterTest {

    private static final String DIM = "minecraft:overworld";
    private static final String ITEM = "minecraft:gunpowder";

    private static final long CHEST_AT = BlockKey.pack(4, 64, 8);
    private static final long HOPPER_AT = BlockKey.pack(6, 64, 8);

    private TrackerService tracker;

    @BeforeEach
    void setUp(@TempDir Path storage) {
        tracker = new TrackerService(storage);
        tracker.record(DIM, container(CHEST_AT, "minecraft:chest"));
        tracker.record(DIM, container(HOPPER_AT, "minecraft:hopper"));
    }

    private ContainerRecord container(long pos, String typeId) {
        return new ContainerRecord(pos,
                tracker.palette().intern(DIM),
                tracker.palette().intern(typeId),
                Origin.PLAYER_PLACED, null, false, true, null, 1,
                List.of(new StackEntry(tracker.palette().intern(ITEM), 3)));
    }

    private static QueryDto.Filters hidingMachines() {
        return new QueryDto.Filters(true, false, false, true, 0);
    }

    private List<QueryDto.ContainerHit> hitsFor(QueryDto.Filters filters, String text) {
        QueryDto.ContainerRequest request = new QueryDto.ContainerRequest(
                1, List.of(ITEM), filters, 0, DIM, text);
        return QueryService.containers(tracker, request, CHEST_AT, DIM, null,
                QueryService.Refresher.NONE).hits();
    }

    private static boolean marks(List<QueryDto.ContainerHit> hits, String typeId) {
        return hits.stream().anyMatch(hit -> typeId.equals(hit.typeId()));
    }

    @Test
    void hopperIsNotMarked() {
        List<QueryDto.ContainerHit> hits = hitsFor(hidingMachines(), "");

        assertTrue(marks(hits, "minecraft:chest"));
        assertFalse(marks(hits, "minecraft:hopper"));
        assertEquals(1, hits.size());
    }

    @Test
    void hopperIsMarkedWhenShown() {
        List<QueryDto.ContainerHit> hits = hitsFor(
                new QueryDto.Filters(true, true, false, true, 0), "");

        assertTrue(marks(hits, "minecraft:hopper"));
        assertEquals(2, hits.size());
    }

    @Test
    void aTypeTermDoesNotUnhideEveryMachine() {
        List<QueryDto.ContainerHit> hits = hitsFor(hidingMachines(), "in:chest");

        assertFalse(marks(hits, "minecraft:hopper"));
    }
}
