package dev.adrian.chestindex.platform;

import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.MonsterRooms;
import dev.adrian.chestindex.core.util.WorldGenMarkers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldGenProbe {
    private static final Map<Block, String> BLOCK_IDS = new ConcurrentHashMap<>();

    private WorldGenProbe() {}

    public static boolean isGenerated(Level level, LevelChunk chunk, BlockPos pos, String typeId) {
        if (level instanceof ServerLevel serverLevel && chunk != null && inStructurePiece(serverLevel, chunk, pos)) {
            return true;
        }
        if (MonsterRooms.CHEST_ID.equals(typeId) && nearMonsterRoomSpawner(level, pos)) return true;
        return surroundedByWorldGen(level, pos);
    }

    public static boolean inStructurePiece(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        var chunkSource = level.getChunkSource();

        for (var entry : chunk.getAllReferences().entrySet()) {
            for (long reference : entry.getValue()) {
                LevelChunk startChunk = chunkSource
                        .getChunkNow(ChunkPos.getX(reference), ChunkPos.getZ(reference));
                if (startChunk == null) continue;

                StructureStart start = startChunk.getStartForStructure(entry.getKey());
                if (start == null || !start.isValid()) continue;

                if (level.structureManager().structureHasPieceAt(pos, start)) return true;
            }
        }
        return false;
    }

    public static boolean nearMonsterRoomSpawner(Level level, BlockPos chest) {
        Resident blocks = new Resident(level);
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dx = -MonsterRooms.REACH; dx <= MonsterRooms.REACH; dx++) {
            for (int dz = -MonsterRooms.REACH; dz <= MonsterRooms.REACH; dz++) {
                probe.set(chest.getX() + dx, chest.getY(), chest.getZ() + dz);
                LevelChunk chunk = blocks.chunkAt(probe.getX(), probe.getZ());
                if (chunk != null && chunk.getBlockState(probe).is(Blocks.SPAWNER)) return true;
            }
        }
        return false;
    }

    public static boolean surroundedByWorldGen(Level level, BlockPos pos) {
        return WorldGenMarkers.surroundedByWorldGen(
                BlockKey.pack(pos.getX(), pos.getY(), pos.getZ()), new Resident(level));
    }

    private static String idOf(Block block) {
        return BLOCK_IDS.computeIfAbsent(block, b -> BuiltInRegistries.BLOCK.getKey(b).toString());
    }

    private static final class Resident implements WorldGenMarkers.Blocks {
        private final Level level;
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        private LevelChunk chunk;
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;

        Resident(Level level) {
            this.level = level;
        }

        LevelChunk chunkAt(int x, int z) {
            int cx = x >> 4;
            int cz = z >> 4;
            if (cx != chunkX || cz != chunkZ) {
                chunk = level.getChunkSource().getChunkNow(cx, cz);
                chunkX = cx;
                chunkZ = cz;
            }
            return chunk;
        }

        @Override
        public String idAt(int x, int y, int z) {
            LevelChunk at = chunkAt(x, z);
            if (at == null) return null;
            return idOf(at.getBlockState(probe.set(x, y, z)).getBlock());
        }
    }
}
