package dev.adrian.chestindex.server;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.platform.ContainerTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Trackers {
    private static volatile TrackerService current;
    private static volatile net.minecraft.server.MinecraftServer server;
    private static volatile dev.adrian.chestindex.server.scan.RegionScanner regionScanner;

    private static final Map<ResourceKey<Level>, String> DIMENSION_IDS = new ConcurrentHashMap<>();

    private static final Map<String, java.util.Set<Long>> DIRTY = new ConcurrentHashMap<>();

    private static final int DIRTY_BUDGET_PER_TICK = 48;

    private static final long DIRTY_SLICE_NANOS = 2_000_000L;

    private static final long TICK_TIME_BUDGET_NANOS = 45_000_000L;

    private Trackers() {}

    public static void setCurrent(TrackerService service, net.minecraft.server.MinecraftServer minecraftServer) {
        current = service;
        server = minecraftServer;
        regionScanner = new dev.adrian.chestindex.server.scan.RegionScanner(service);
    }

    public static net.minecraft.server.MinecraftServer server() {
        return server;
    }

    public static dev.adrian.chestindex.server.scan.RegionScanner regionScanner() {
        return regionScanner;
    }

    public static boolean isChunkLoaded(String dimensionId, long chunkKey) {
        net.minecraft.server.MinecraftServer current = server;
        if (current == null) return false;
        ServerLevel level = levelFor(dimensionId);
        return level != null && level.getChunkSource()
                .hasChunk(BlockKey.chunkX(chunkKey), BlockKey.chunkZ(chunkKey));
    }

    private static final Map<String, ServerLevel> LEVELS = new ConcurrentHashMap<>();

    public static ServerLevel levelFor(String dimensionId) {
        net.minecraft.server.MinecraftServer current = server;
        if (current == null) return null;

        ServerLevel cached = LEVELS.get(dimensionId);
        if (cached != null) return cached;

        for (ServerLevel level : current.getAllLevels()) {
            String id = dimensionId(level);
            LEVELS.putIfAbsent(id, level);
            if (id.equals(dimensionId)) return level;
        }
        return null;
    }

    public static TrackerService current() {
        return current;
    }

    public static void clear() {
        if (regionScanner != null) regionScanner.cancel();
        DIRTY.clear();
        regionScanner = null;
        server = null;
        current = null;
        DIMENSION_IDS.clear();
        LEVELS.clear();
    }

    public static String dimensionId(Level level) {
        return DIMENSION_IDS.computeIfAbsent(level.dimension(), key -> key.identifier().toString());
    }

    public static void onBlockChanged(Level level, BlockPos pos) {
        TrackerService tracker = current;
        if (tracker == null || !(level instanceof ServerLevel)) return;
        if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) return;

        long key = BlockKey.pack(pos.getX(), pos.getY(), pos.getZ());
        String dimensionId = dimensionId(level);
        if (tracker.index(dimensionId).get(key) == null) return;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !ContainerTypes.isContainer(blockEntity)) {
            tracker.remove(dimensionId, key);
        }
    }

    public static void onContainerChanged(Level level, BlockPos pos) {
        TrackerService tracker = current;
        if (tracker == null) return;

        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) return;

        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return;

        if (!ContainerTypes.isContainer(chunk.getBlockEntity(pos))) return;

        DIRTY.computeIfAbsent(dimensionId(level), id -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(BlockKey.pack(pos.getX(), pos.getY(), pos.getZ()));
    }

    public static int drainDirty(long tickTimeNanos) {
        if (tickTimeNanos > TICK_TIME_BUDGET_NANOS) return 0;

        TrackerService tracker = current;
        if (tracker == null || DIRTY.isEmpty()) return 0;

        int done = 0;
        long deadline = System.nanoTime() + DIRTY_SLICE_NANOS;
        var scanner = new dev.adrian.chestindex.server.scan.LiveScanner(tracker);

        for (Map.Entry<String, java.util.Set<Long>> entry : DIRTY.entrySet()) {
            ServerLevel level = levelFor(entry.getKey());
            java.util.Set<Long> positions = entry.getValue();
            if (level == null) {
                positions.clear();
                continue;
            }

            var iterator = positions.iterator();
            while (iterator.hasNext() && done < DIRTY_BUDGET_PER_TICK) {
                long pos = iterator.next();
                iterator.remove();
                scanner.refreshIfLoaded(level, entry.getKey(), pos);
                done++;
                if (System.nanoTime() >= deadline) return done;
            }
            if (done >= DIRTY_BUDGET_PER_TICK) break;
        }
        return done;
    }

    public static int refreshDirty(ServerLevel level, String dimensionId, java.util.Collection<Long> positions) {
        TrackerService tracker = current;
        if (tracker == null || level == null) return 0;

        java.util.Set<Long> dirty = DIRTY.get(dimensionId);
        if (dirty == null || dirty.isEmpty()) return 0;

        var scanner = new dev.adrian.chestindex.server.scan.LiveScanner(tracker);
        int refreshed = 0;
        for (Long pos : positions) {
            if (!dirty.remove(pos)) continue;
            scanner.refreshIfLoaded(level, dimensionId, pos);
            refreshed++;
        }
        return refreshed;
    }

    public static void liveScanChunk(String dimensionId, long chunkKey) {
        TrackerService tracker = current;
        if (tracker == null) return;
        ServerLevel level = levelFor(dimensionId);
        if (level == null) return;

        var chunk = level.getChunkSource()
                .getChunkNow(BlockKey.chunkX(chunkKey), BlockKey.chunkZ(chunkKey));
        if (chunk == null) return;
        new dev.adrian.chestindex.server.scan.LiveScanner(tracker)
                .scanChunk(level, chunk, dimensionId);
    }

    public static void onBlockPlaced(Level level, BlockPos pos, LivingEntity placer) {
        TrackerService tracker = current;
        if (tracker == null || !(level instanceof ServerLevel serverLevel)) return;
        if (!(placer instanceof Player player)) return;
        if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) return;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !ContainerTypes.isContainer(blockEntity)) return;

        String typeId = ContainerTypes.idOf(blockEntity);
        if (typeId == null) return;
        tracker.containerTypes().learn(typeId);

        String dimensionId = dimensionId(level);
        long key = BlockKey.pack(pos.getX(), pos.getY(), pos.getZ());
        long tick = serverLevel.getGameTime();

        ContainerRecord existing = tracker.index(dimensionId).get(key);
        if (existing != null) {
            tracker.record(dimensionId, existing.withOrigin(Origin.PLAYER_PLACED, player.getUUID()));
            return;
        }
        tracker.record(dimensionId, new ContainerRecord(
                key,
                tracker.palette().intern(dimensionId),
                tracker.palette().intern(typeId),
                Origin.PLAYER_PLACED,
                player.getUUID(),
                false,
                true,
                null,
                tick,
                java.util.List.of()));
    }
}
