package dev.adrian.chestindex.server.scan;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.platform.ChunkPosCompat;
import dev.adrian.chestindex.platform.ContainerTypes;
import dev.adrian.chestindex.platform.LiveContainerReader;
import dev.adrian.chestindex.server.TrackerService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LiveScanner {

    private final TrackerService tracker;

    public LiveScanner(TrackerService tracker) {
        this.tracker = tracker;
    }

    public record ScanResult(int chunksScanned, int chunksSkipped, int containersFound, int staleRemoved) {}

    public ScanResult scanAround(ServerLevel level, BlockPos center, int chunkRadius) {
        String dimensionId = level.dimension().identifier().toString();
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        int scanned = 0;
        int skipped = 0;
        int found = 0;
        int removed = 0;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    skipped++;
                    continue;
                }
                ChunkScan result = scanChunk(level, chunk, dimensionId);
                scanned++;
                found += result.found();
                removed += result.removed();
            }
        }
        return new ScanResult(scanned, skipped, found, removed);
    }

    public record ChunkScan(int found, int removed) {}

    public ChunkScan scanChunk(ServerLevel level, LevelChunk chunk, String dimensionId) {
        long tick = level.getGameTime();
        Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();

        long chunkKey = BlockKey.chunkKey(ChunkPosCompat.x(chunk.getPos()), ChunkPosCompat.z(chunk.getPos()));

        if (blockEntities.isEmpty()
                && tracker.index(dimensionId).positionsInChunk(chunkKey).isEmpty()) {
            return new ChunkScan(0, 0);
        }

        Set<Long> actual = new HashSet<>();
        int found = 0;

        for (Map.Entry<BlockPos, BlockEntity> entry : blockEntities.entrySet()) {
            BlockEntity blockEntity = entry.getValue();
            if (!ContainerTypes.isContainer(blockEntity)) continue;

            BlockPos pos = entry.getKey();
            if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) continue;

            String typeId = ContainerTypes.idOf(blockEntity);
            if (typeId == null) continue;
            tracker.containerTypes().learn(typeId);

            long key = BlockKey.pack(pos.getX(), pos.getY(), pos.getZ());
            actual.add(key);
            tracker.record(dimensionId, toRecord(blockEntity, key, typeId, dimensionId, tick,
                    naturalityOf(level, chunk, pos, key, dimensionId)));
            found++;
        }

        int removed = tracker.reconcileChunk(dimensionId, chunkKey, actual);
        return new ChunkScan(found, removed);
    }

    public boolean refreshIfLoaded(ServerLevel level, String dimensionId, long pos) {
        int x = BlockKey.x(pos);
        int y = BlockKey.y(pos);
        int z = BlockKey.z(pos);
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return true;
        }

        BlockPos blockPos = new BlockPos(x, y, z);
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity == null || !ContainerTypes.isContainer(blockEntity)) {
            tracker.remove(dimensionId, pos);
            return false;
        }

        String typeId = ContainerTypes.idOf(blockEntity);
        if (typeId == null) return true;
        tracker.record(dimensionId, toRecord(blockEntity, pos, typeId, dimensionId, level.getGameTime(),
                naturalityOf(level, chunk, blockPos, pos, dimensionId)));
        return true;
    }

    private Origin naturalityOf(ServerLevel level, LevelChunk chunk, BlockPos blockPos,
                                long pos, String dimensionId) {
        if (tracker.index(dimensionId).get(pos) != null) return Origin.UNKNOWN;
        if (chunk == null) return Origin.UNKNOWN;
        return structurePieceAt(level, chunk, blockPos) ? Origin.NATURAL : Origin.UNKNOWN;
    }

    private boolean structurePieceAt(ServerLevel level, LevelChunk chunk, BlockPos blockPos) {
        var chunkSource = level.getChunkSource();

        for (var entry : chunk.getAllReferences().entrySet()) {
            for (long reference : entry.getValue()) {
                LevelChunk startChunk = chunkSource
                        .getChunkNow(ChunkPos.getX(reference), ChunkPos.getZ(reference));
                if (startChunk == null) continue;

                StructureStart start = startChunk.getStartForStructure(entry.getKey());
                if (start == null || !start.isValid()) continue;

                if (level.structureManager().structureHasPieceAt(blockPos, start)) return true;
            }
        }
        return false;
    }

    private ContainerRecord toRecord(BlockEntity blockEntity, long pos, String typeId,
                                     String dimensionId, long tick, Origin observed) {
        boolean unlooted = blockEntity instanceof RandomizableContainerBlockEntity randomizable
                && randomizable.getLootTable() != null;

        int dimId = tracker.palette().intern(dimensionId);
        int typeKey = tracker.palette().intern(typeId);

        if (unlooted) {
            return new ContainerRecord(pos, dimId, typeKey, Origin.NATURAL, null,
                    true, false, null, tick, List.of());
        }

        List<StackEntry> contents = LiveContainerReader.read((Container) blockEntity, tracker.palette());
        return new ContainerRecord(pos, dimId, typeKey, observed, null,
                false, true, null, tick, contents);
    }
}
