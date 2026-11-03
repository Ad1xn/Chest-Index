package dev.adrian.chestindex.core.anvil;

import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.MonsterRooms;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AnvilWorldView implements OriginResolver.World, Closeable {
    private static final Set<String> KEYS = Set.of("block_entities", "structures", SectionProbe.SECTIONS_KEY);
    private static final int CHUNKS_KEPT = 96;
    private static final int REGIONS_KEPT = 6;

    private record ChunkView(List<Long> references, List<ChunkExtractor.StructureBox> starts,
                             List<Long> spawners, SectionProbe blocks) {
        static final ChunkView ABSENT = new ChunkView(List.of(), List.of(), List.of(), SectionProbe.of(null));
    }

    private final Path regionDir;

    private final Map<Long, ChunkView> chunks = new LinkedHashMap<>(CHUNKS_KEPT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ChunkView> eldest) {
            return size() > CHUNKS_KEPT;
        }
    };

    private final Map<Long, RegionFile> regions = new LinkedHashMap<>(REGIONS_KEPT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, RegionFile> eldest) {
            if (size() <= REGIONS_KEPT) return false;
            closeQuietly(eldest.getValue());
            return true;
        }
    };

    private int chunksRead;

    public AnvilWorldView(Path regionDir) {
        this.regionDir = regionDir;
    }

    public int chunksRead() {
        return chunksRead;
    }

    @Override
    public List<Long> structureReferences(long chunkKey) {
        return chunk(chunkKey).references();
    }

    @Override
    public List<ChunkExtractor.StructureBox> structuresStartingIn(long chunkKey) {
        return chunk(chunkKey).starts();
    }

    @Override
    public List<Long> spawnersIn(long chunkKey) {
        return chunk(chunkKey).spawners();
    }

    @Override
    public String idAt(int x, int y, int z) {
        return chunk(BlockKey.chunkKey(x >> 4, z >> 4)).blocks().idAt(x, y, z);
    }

    private ChunkView chunk(long chunkKey) {
        ChunkView cached = chunks.get(chunkKey);
        if (cached != null) return cached;

        ChunkView view = read(chunkKey);
        chunks.put(chunkKey, view);
        return view;
    }

    private ChunkView read(long chunkKey) {
        int chunkX = BlockKey.chunkX(chunkKey);
        int chunkZ = BlockKey.chunkZ(chunkKey);
        RegionFile region = region(chunkX >> 5, chunkZ >> 5);
        if (region == null) return ChunkView.ABSENT;

        NbtCompound nbt;
        try {
            nbt = region.readChunk(chunkX & 31, chunkZ & 31, KEYS);
        } catch (IOException torn) {
            return ChunkView.ABSENT;
        }
        if (nbt == null) return ChunkView.ABSENT;
        chunksRead++;

        List<Long> spawners = new ArrayList<>(0);
        for (NbtCompound blockEntity : nbt.getCompoundList("block_entities")) {
            if (!MonsterRooms.SPAWNER_ID.equals(blockEntity.getString("id"))) continue;
            int x = blockEntity.getInt("x", Integer.MIN_VALUE);
            int y = blockEntity.getInt("y", Integer.MIN_VALUE);
            int z = blockEntity.getInt("z", Integer.MIN_VALUE);
            if (x == Integer.MIN_VALUE || !BlockKey.isRepresentable(x, y, z)) continue;
            spawners.add(BlockKey.pack(x, y, z));
        }

        return new ChunkView(ChunkExtractor.structureReferences(nbt), ChunkExtractor.extractStructureBoxes(nbt),
                spawners, SectionProbe.of(nbt));
    }

    private RegionFile region(int regionX, int regionZ) {
        long key = BlockKey.chunkKey(regionX, regionZ);
        if (regions.containsKey(key)) return regions.get(key);

        Path file = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
        RegionFile opened = null;
        if (Files.isRegularFile(file)) {
            try {
                opened = RegionFile.open(file);
            } catch (IOException unreadable) {
                opened = null;
            }
        }
        regions.put(key, opened);
        return opened;
    }

    private static void closeQuietly(RegionFile file) {
        if (file == null) return;
        try {
            file.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        regions.values().forEach(AnvilWorldView::closeQuietly);
        regions.clear();
        chunks.clear();
    }
}
