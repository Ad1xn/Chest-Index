package dev.adrian.chestindex.platform;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.store.StringPalette;
import dev.adrian.chestindex.core.util.BlockKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EntityContainers {
    private EntityContainers() {}

    public static final double RADIUS = 256.0;

    public record Found(ContainerRecord record, int entityId) {}

    public static List<Found> near(Level level, String dimensionId,
                                   StringPalette palette, long centre) {
        if (level == null) return List.of();

        int cx = BlockKey.x(centre);
        int cy = BlockKey.y(centre);
        int cz = BlockKey.z(centre);
        AABB box = new AABB(cx - RADIUS, cy - RADIUS, cz - RADIUS,
                cx + RADIUS, cy + RADIUS, cz + RADIUS);

        int dimensionKey = palette.intern(dimensionId);
        long tick = level.getGameTime();

        List<Found> records = new ArrayList<>();
        Set<Long> taken = new HashSet<>();

        for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof Container)) {
            int x = entity.getBlockX();
            int y = entity.getBlockY();
            int z = entity.getBlockZ();
            if (!BlockKey.isRepresentable(x, y, z)) continue;

            long pos = BlockKey.pack(x, y, z);
            if (!taken.add(pos)) continue;

            var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (key == null) continue;

            List<StackEntry> contents =
                    LiveContainerReader.read((Container) entity, palette);
            boolean generated = isGenerated(level, entity);
            records.add(new Found(new ContainerRecord(
                    pos,
                    dimensionKey,
                    palette.intern(key.toString()),
                    ContainerRecord.decided(generated),
                    null,
                    false,
                    true,
                    null,
                    tick,
                    contents,
                    !generated), entity.getId()));
        }
        return records;
    }

    private static boolean isGenerated(Level level, Entity entity) {
        if (entity instanceof ContainerEntity vehicle && vehicle.getContainerLootTable() != null) return true;
        BlockPos pos = entity.blockPosition();
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        return WorldGenProbe.isGenerated(level, chunk, pos, null);
    }
}
