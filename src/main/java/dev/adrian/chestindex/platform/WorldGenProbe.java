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

    public enum Verdict { GENERATED, PLACED, UNSURE }

    public static Verdict check(Level level, LevelChunk chunk, BlockPos pos, String typeId) {
        Resident blocks = new Resident(level, chunk);
        if (level instanceof ServerLevel serverLevel && chunk != null
                && inStructurePiece(serverLevel, chunk, pos, blocks)) {
            return Verdict.GENERATED;
        }
        if (MonsterRooms.CHEST_ID.equals(typeId) && nearMonsterRoomSpawner(pos, blocks)) return Verdict.GENERATED;
        if (WorldGenMarkers.surroundedByWorldGen(BlockKey.pack(pos.getX(), pos.getY(), pos.getZ()), blocks)) {
            return Verdict.GENERATED;
        }
        return blocks.missed ? Verdict.UNSURE : Verdict.PLACED;
    }

    public static boolean isGenerated(Level level, LevelChunk chunk, BlockPos pos, String typeId) {
        return check(level, chunk, pos, typeId) == Verdict.GENERATED;
    }

    private static boolean inStructurePiece(ServerLevel level, LevelChunk chunk, BlockPos pos, Resident blocks) {
        for (var entry : chunk.getAllReferences().entrySet()) {
            for (long reference : entry.getValue()) {
                LevelChunk startChunk = blocks.chunkAt(ChunkPos.getX(reference) << 4, ChunkPos.getZ(reference) << 4);
                if (startChunk == null) continue;

                StructureStart start = startChunk.getStartForStructure(entry.getKey());
                if (start == null || !start.isValid()) continue;

                if (level.structureManager().structureHasPieceAt(pos, start)) return true;
            }
        }
        return false;
    }

    private static boolean nearMonsterRoomSpawner(BlockPos chest, Resident blocks) {
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

    private static String idOf(Block block) {
        return BLOCK_IDS.computeIfAbsent(block, b -> BuiltInRegistries.BLOCK.getKey(b).toString());
    }

    private static final class Resident implements WorldGenMarkers.Blocks {
        private final Level level;
        private final LevelChunk own;
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        private LevelChunk chunk;
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;
        boolean missed;

        Resident(Level level, LevelChunk own) {
            this.level = level;
            this.own = own;
        }

        LevelChunk chunkAt(int x, int z) {
            int cx = x >> 4;
            int cz = z >> 4;
            if (cx != chunkX || cz != chunkZ) {
                chunk = own != null && ChunkPosCompat.x(own.getPos()) == cx && ChunkPosCompat.z(own.getPos()) == cz
                        ? own
                        : level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) missed = true;
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
