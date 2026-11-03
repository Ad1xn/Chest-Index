package dev.adrian.chestindex.core;

import dev.adrian.chestindex.core.anvil.ChunkExtractor;
import dev.adrian.chestindex.core.anvil.NbtCompound;
import dev.adrian.chestindex.core.anvil.OriginResolver;
import dev.adrian.chestindex.core.anvil.SectionProbe;
import dev.adrian.chestindex.core.index.WorldIndex;
import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.store.IndexCodec;
import dev.adrian.chestindex.core.store.StringPalette;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.WorldGenMarkers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OriginDecisionTest {
    private static final long POS = BlockKey.pack(5, 40, 5);
    private static final int CHEST = 1;

    private static ContainerRecord guess() {
        return new ContainerRecord(POS, 0, CHEST, Origin.PLAYER_PLACED, null, false, true, null, 1L, List.of(), true);
    }

    private static ContainerRecord generated() {
        return new ContainerRecord(POS, 0, CHEST, Origin.NATURAL, null, false, true, null, 2L, List.of());
    }

    private static ContainerRecord seenPlaced() {
        return new ContainerRecord(POS, 0, CHEST, Origin.PLAYER_PLACED, UUID.randomUUID(),
                false, true, null, 3L, List.of());
    }

    @Test
    void unknownIsNeverStored() {
        ContainerRecord plain = new ContainerRecord(POS, 0, CHEST, Origin.UNKNOWN, null, false, true, null, 0L, List.of());
        assertEquals(Origin.PLAYER_PLACED, plain.origin());
        assertTrue(plain.isPlacementGuess());

        ContainerRecord loot = new ContainerRecord(POS, 0, CHEST, Origin.UNKNOWN, null, true, false, null, 0L, List.of());
        assertEquals(Origin.NATURAL, loot.origin());
        assertFalse(loot.originInferred());
    }

    @Test
    void evidenceBeatsAGuessAndASeenPlacementBeatsEvidence() {
        assertEquals(Origin.NATURAL, guess().inheriting(generated()).origin());
        assertEquals(Origin.NATURAL, generated().inheriting(guess()).origin());

        ContainerRecord placed = generated().inheriting(seenPlaced());
        assertEquals(Origin.PLAYER_PLACED, placed.origin());
        assertFalse(placed.isPlacementGuess());
        assertEquals(Origin.PLAYER_PLACED, seenPlaced().inheriting(generated()).origin());
    }

    @Test
    void theGuessFlagSurvivesTheIndexFile(@TempDir Path dir) throws IOException {
        StringPalette palette = new StringPalette();
        palette.intern("minecraft:overworld");
        palette.intern("minecraft:chest");
        WorldIndex index = new WorldIndex(0);
        index.put(guess());
        index.put(new ContainerRecord(BlockKey.pack(9, 40, 9), 0, CHEST, Origin.PLAYER_PLACED, null,
                false, true, null, 1L, List.of()));

        Path file = dir.resolve("o.idx");
        IndexCodec.write(file, palette, index);
        WorldIndex read = IndexCodec.read(file).index();

        assertTrue(read.get(POS).isPlacementGuess());
        assertFalse(read.get(BlockKey.pack(9, 40, 9)).isPlacementGuess());
    }

    @Test
    void oneStrongMarkerOrSeveralWeakOnesMeanWorldGen() {
        Map<Long, String> blocks = new HashMap<>();
        WorldGenMarkers.Blocks world = (x, y, z) -> blocks.get(BlockKey.pack(x, y, z));

        blocks.put(BlockKey.pack(5, 39, 5), "minecraft:stone_bricks");
        assertFalse(WorldGenMarkers.surroundedByWorldGen(POS, world));

        blocks.put(BlockKey.pack(6, 40, 5), "minecraft:end_portal_frame");
        assertTrue(WorldGenMarkers.surroundedByWorldGen(POS, world));

        blocks.clear();
        for (int dx = -1; dx < WorldGenMarkers.WEAK_NEEDED - 2; dx++) {
            blocks.put(BlockKey.pack(5 + dx, 39, 5), "minecraft:mossy_cobblestone");
        }
        assertFalse(WorldGenMarkers.surroundedByWorldGen(POS, world));
        blocks.put(BlockKey.pack(5, 39, 6), "minecraft:cracked_stone_bricks");
        assertTrue(WorldGenMarkers.surroundedByWorldGen(POS, world));
    }

    @Test
    void readsABlockOutOfPackedSectionData() {
        String[] names = {"minecraft:air", "minecraft:stone", "minecraft:spawner"};
        long[] data = new long[256];
        int index = (3 << 8) | (2 << 4) | 1;
        data[index / 16] |= 2L << ((index % 16) * 4);

        List<NbtCompound> palette = new java.util.ArrayList<>();
        for (String name : names) palette.add(new NbtCompound(Map.of("Name", name)));
        NbtCompound states = new NbtCompound(Map.of("palette", palette, "data", data));
        NbtCompound section = new NbtCompound(Map.of("Y", (byte) 2, "block_states", states));
        SectionProbe probe = SectionProbe.of(new NbtCompound(Map.of("sections", List.of(section))));

        assertEquals("minecraft:spawner", probe.idAt(16 * 3 + 1, 2 * 16 + 3, 2));
        assertEquals("minecraft:air", probe.idAt(16 * 3, 2 * 16 + 3, 2));
        assertNull(probe.idAt(1, 200, 2));
    }

    private static final class StubWorld implements OriginResolver.World {
        final Map<Long, List<Long>> references = new HashMap<>();
        final Map<Long, List<ChunkExtractor.StructureBox>> starts = new HashMap<>();
        final Map<Long, List<Long>> spawners = new HashMap<>();
        final Map<Long, String> blocks = new LinkedHashMap<>();

        public List<Long> structureReferences(long chunk) { return references.getOrDefault(chunk, List.of()); }
        public List<ChunkExtractor.StructureBox> structuresStartingIn(long chunk) { return starts.getOrDefault(chunk, List.of()); }
        public List<Long> spawnersIn(long chunk) { return spawners.getOrDefault(chunk, List.of()); }
        public String idAt(int x, int y, int z) { return blocks.get(BlockKey.pack(x, y, z)); }
    }

    @Test
    void aStructureStartingInAnotherRegionStillCounts() {
        StubWorld world = new StubWorld();
        long farStart = BlockKey.chunkKey(-40, 0);
        world.references.put(BlockKey.chunkOf(POS), List.of(farStart));
        world.starts.put(farStart, List.of(new ChunkExtractor.StructureBox("minecraft:village_plains", 0, 30, 0, 10, 50, 10)));

        assertEquals(Origin.NATURAL, OriginResolver.resolve(guess(), CHEST, world).origin());
    }

    @Test
    void aMonsterRoomSpawnerInTheNextChunkCounts() {
        StubWorld world = new StubWorld();
        long chestAtEdge = BlockKey.pack(1, 40, 5);
        world.spawners.put(BlockKey.chunkKey(-1, 0), List.of(BlockKey.pack(-2, 40, 5)));

        ContainerRecord edge = new ContainerRecord(chestAtEdge, 0, CHEST, Origin.UNKNOWN, null,
                false, true, null, 0L, List.of());
        assertEquals(Origin.NATURAL, OriginResolver.resolve(edge, CHEST, world).origin());
        assertEquals(Origin.PLAYER_PLACED, OriginResolver.resolve(edge, CHEST + 1, world).origin());
    }

    @Test
    void withNothingFoundTheGuessStands() {
        ContainerRecord resolved = OriginResolver.resolve(guess(), CHEST, new StubWorld());
        assertEquals(Origin.PLAYER_PLACED, resolved.origin());
        assertTrue(resolved.isPlacementGuess());
    }

    @Test
    void evidenceIsNotSecondGuessed() {
        StubWorld world = new StubWorld();
        world.blocks.put(BlockKey.pack(5, 39, 5), "minecraft:spawner");
        assertSame(seenPlacedFixed, OriginResolver.resolve(seenPlacedFixed, CHEST, world));
    }

    private final ContainerRecord seenPlacedFixed = seenPlaced();
}
