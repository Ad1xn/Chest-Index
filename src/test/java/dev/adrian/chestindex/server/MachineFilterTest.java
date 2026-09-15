package dev.adrian.chestindex.server;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.core.util.BlockKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hiding machines has to hide them from the markers, not only from the grid.
 *
 * <p>Reported from a live world: hoppers were being boxed while items ran
 * through them, with the machines filter off. A hopper holds what it is
 * carrying for a tick or two, so it matches a search for that item exactly as a
 * chest does - the filter is the only thing between it and a marker, and a
 * marker on a hopper is guidance to a container whose contents will be gone
 * before the player gets there.
 */
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

    /** What the search screen and the Litematica buttons both send. */
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
    @DisplayName("a hopper carrying the item is not marked while machines are hidden")
    void hopperIsNotMarked() {
        List<QueryDto.ContainerHit> hits = hitsFor(hidingMachines(), "");

        assertTrue(marks(hits, "minecraft:chest"), "the chest should still be marked");
        assertFalse(marks(hits, "minecraft:hopper"),
                "a hopper was marked with the machines filter off");
        assertEquals(1, hits.size(), "only the chest should be marked");
    }

    @Test
    @DisplayName("and is marked once machines are shown, so the filter is what decides")
    void hopperIsMarkedWhenShown() {
        List<QueryDto.ContainerHit> hits = hitsFor(
                new QueryDto.Filters(true, true, false, true, 0), "");

        assertTrue(marks(hits, "minecraft:hopper"), "showing machines should mark the hopper");
        assertEquals(2, hits.size());
    }

    /**
     * The case a search term opens up.
     *
     * <p>Naming a type in the search box replaces the exclusion set outright,
     * so that asking for a type cannot be refused by a filter hiding it. That is
     * right for the type actually named and wrong for every other - the
     * exclusion is dropped wholesale rather than for the one type in question.
     */
    @Test
    @DisplayName("a term naming one container type does not un-hide the others")
    void aTypeTermDoesNotUnhideEveryMachine() {
        List<QueryDto.ContainerHit> hits = hitsFor(hidingMachines(), "in:chest");

        assertFalse(marks(hits, "minecraft:hopper"),
                "asking for chests marked hoppers as well");
    }
}
