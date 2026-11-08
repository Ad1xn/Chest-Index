package dev.adrian.chestindex.core;

import dev.adrian.chestindex.core.index.IndexQuery;
import dev.adrian.chestindex.core.index.WorldIndex;
import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.store.IndexCodec;
import dev.adrian.chestindex.core.store.StringPalette;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.BuiltEvidence;
import dev.adrian.chestindex.core.util.ShulkerMachines;
import dev.adrian.chestindex.core.util.WorldGenMarkers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltEvidenceTest {
    private static final int X = 10;
    private static final int Y = 64;
    private static final int Z = 10;
    private static final long POS = BlockKey.pack(X, Y, Z);
    private static final int STONE = 7;
    private static final int COBBLESTONE = 8;

    private static final class Grid implements WorldGenMarkers.Blocks {
        final Map<Long, String> ids = new HashMap<>();
        final Map<Long, String> facings = new HashMap<>();

        Grid put(int dx, int dy, int dz, String id, String facing) {
            long key = BlockKey.pack(X + dx, Y + dy, Z + dz);
            ids.put(key, id);
            if (facing != null) facings.put(key, facing);
            return this;
        }

        @Override
        public String idAt(int x, int y, int z) {
            return ids.getOrDefault(BlockKey.pack(x, y, z), "minecraft:air");
        }

        @Override
        public String facingAt(int x, int y, int z) {
            return facings.get(BlockKey.pack(x, y, z));
        }
    }

    private static ContainerRecord generated(List<StackEntry> contents) {
        return new ContainerRecord(POS, 0, 1, Origin.NATURAL, null, false, true, null, 1L, contents);
    }

    @Test
    void storageAmountsOfOneItemMeanSomeoneBuiltIt() {
        ContainerRecord stored = generated(List.of(new StackEntry(STONE, 64), new StackEntry(STONE, 64),
                new StackEntry(COBBLESTONE, 64)));
        ContainerRecord settled = BuiltEvidence.settle(stored, null);
        assertEquals(Origin.PLAYER_PLACED, settled.origin());
        assertFalse(settled.originInferred());
    }

    @Test
    void ordinaryLootStaysGenerated() {
        ContainerRecord loot = generated(List.of(new StackEntry(STONE, 12), new StackEntry(COBBLESTONE, 40)));
        assertSame(loot, BuiltEvidence.settle(loot, new Grid()));
    }

    @Test
    void aFilledShulkerInsideIsBuilt() {
        ContainerRecord loot = generated(List.of(new StackEntry(STONE, 1), new StackEntry(COBBLESTONE, 1, 1)));
        assertEquals(Origin.PLAYER_PLACED, BuiltEvidence.settle(loot, null).origin());
    }

    @Test
    void aHopperNextToItMeansBuilt() {
        Grid grid = new Grid().put(0, -1, 0, "minecraft:hopper", "down");
        assertEquals(Origin.PLAYER_PLACED, BuiltEvidence.settle(generated(List.of()), grid).origin());
    }

    @Test
    void anUnloadedNeighbourIsNotRedstone() {
        WorldGenMarkers.Blocks unloaded = (x, y, z) -> null;
        ContainerRecord loot = generated(List.of());
        assertSame(loot, BuiltEvidence.settle(loot, unloaded));
    }

    @Test
    void lootTablesAreNeverOverruled() {
        ContainerRecord unlooted = new ContainerRecord(POS, 0, 1, Origin.NATURAL, null, true, false, null, 1L,
                List.of());
        Grid grid = new Grid().put(0, -1, 0, "minecraft:hopper", "down");
        assertSame(unlooted, BuiltEvidence.settle(unlooted, grid));
    }

    @Test
    void builtEvidenceOutranksAStoredGeneratedRecord() {
        ContainerRecord stored = generated(List.of());
        ContainerRecord fresh = BuiltEvidence.settle(
                new ContainerRecord(POS, 0, 1, Origin.PLAYER_PLACED, null, false, true, null, 2L,
                        List.of(new StackEntry(STONE, 500)), true), null);
        assertEquals(Origin.PLAYER_PLACED, fresh.inheriting(stored).origin());
    }

    @Test
    void aDispenserFacingTheShulkerIsALoader() {
        Grid grid = new Grid().put(-1, 0, 0, "minecraft:dispenser", "east");
        assertTrue(ShulkerMachines.inMachine(POS, grid));
    }

    @Test
    void aPistonFacingTheShulkerIsALoader() {
        Grid grid = new Grid().put(0, 0, 1, "minecraft:sticky_piston", "north");
        assertTrue(ShulkerMachines.inMachine(POS, grid));
    }

    @Test
    void aFedShulkerThatOpensIsStorage() {
        Grid grid = new Grid().put(-1, 0, 0, "minecraft:hopper", "east").put(0, 0, 0, "minecraft:shulker_box", "up");
        assertFalse(ShulkerMachines.inMachine(POS, grid));
    }

    @Test
    void aFedShulkerThatCannotOpenIsAMachine() {
        Grid grid = new Grid().put(0, 1, 0, "minecraft:hopper", "down").put(0, 0, 0, "minecraft:shulker_box", "up");
        assertTrue(ShulkerMachines.inMachine(POS, grid));
    }

    @Test
    void aBlockedDisplayShulkerIsStorage() {
        Grid grid = new Grid().put(0, 1, 0, "minecraft:glass", null).put(0, 0, 0, "minecraft:shulker_box", "up");
        assertFalse(ShulkerMachines.inMachine(POS, grid));
    }

    @Test
    void aHopperPointingAwayDoesNotFeedIt() {
        Grid grid = new Grid().put(-1, 0, 0, "minecraft:hopper", "west").put(0, 1, 0, "minecraft:stone", null)
                .put(0, 0, 0, "minecraft:shulker_box", "up");
        assertFalse(ShulkerMachines.inMachine(POS, grid));
    }

    @Test
    void machineShulkersHideWithTheMachinesAndSurviveASave(@TempDir Path dir) throws IOException {
        StringPalette palette = new StringPalette();
        int dim = palette.intern("minecraft:overworld");
        int shulker = palette.intern("minecraft:shulker_box");
        WorldIndex index = new WorldIndex(dim);
        index.put(new ContainerRecord(POS, dim, shulker, Origin.PLAYER_PLACED, null, false, true, null, 1L,
                List.of(new StackEntry(STONE, 1))).inMachine(true));

        IndexQuery hidden = IndexQuery.builder().item(STONE).hideMachineParts(true).build();
        IndexQuery shown = IndexQuery.builder().item(STONE).build();
        assertTrue(index.query(hidden).isEmpty());
        assertEquals(1, index.query(shown).size());

        Path file = dir.resolve("overworld.idx");
        IndexCodec.write(file, palette, index);
        assertTrue(IndexCodec.read(file).index().get(POS).inMachine());
    }
}
