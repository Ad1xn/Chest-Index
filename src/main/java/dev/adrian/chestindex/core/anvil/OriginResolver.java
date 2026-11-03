package dev.adrian.chestindex.core.anvil;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.MonsterRooms;
import dev.adrian.chestindex.core.util.WorldGenMarkers;

import java.util.List;

public final class OriginResolver {
    public interface World extends WorldGenMarkers.Blocks {
        List<Long> structureReferences(long chunkKey);

        List<ChunkExtractor.StructureBox> structuresStartingIn(long chunkKey);

        List<Long> spawnersIn(long chunkKey);
    }

    private OriginResolver() {}

    public static ContainerRecord resolve(ContainerRecord container, int chestTypeId, World world) {
        if (!container.isPlacementGuess()) return container;
        return isGenerated(container.pos(), container.typeId() == chestTypeId, world)
                ? container.withOrigin(Origin.NATURAL, null)
                : container;
    }

    public static boolean isGenerated(long pos, boolean chest, World world) {
        int x = BlockKey.x(pos);
        int y = BlockKey.y(pos);
        int z = BlockKey.z(pos);
        long chunk = BlockKey.chunkOf(pos);

        for (long start : world.structureReferences(chunk)) {
            for (ChunkExtractor.StructureBox box : world.structuresStartingIn(start)) {
                if (box.contains(x, y, z)) return true;
            }
        }

        if (chest && nearMonsterRoomSpawner(pos, world)) return true;

        return WorldGenMarkers.surroundedByWorldGen(pos, world);
    }

    private static boolean nearMonsterRoomSpawner(long pos, World world) {
        int x = BlockKey.x(pos);
        int z = BlockKey.z(pos);
        int reach = MonsterRooms.REACH;
        for (int cx = (x - reach) >> 4; cx <= (x + reach) >> 4; cx++) {
            for (int cz = (z - reach) >> 4; cz <= (z + reach) >> 4; cz++) {
                if (MonsterRooms.nearAnySpawner(pos, world.spawnersIn(BlockKey.chunkKey(cx, cz)))) {
                    return true;
                }
            }
        }
        return false;
    }
}
