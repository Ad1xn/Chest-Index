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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static entry point the mixins call into.
 *
 * <p>Mixins cannot be handed dependencies, so the hooks need somewhere to find
 * the active {@link TrackerService}. There is exactly one server at a time -
 * an integrated one in singleplayer, or the dedicated one - so a single slot is
 * enough, and a null slot simply means "not running", which keeps the hooks
 * inert on a client connected to a remote server.
 */
public final class Trackers {

    private static volatile TrackerService current;
    private static volatile net.minecraft.server.MinecraftServer server;
    private static volatile dev.adrian.chestindex.server.scan.RegionScanner regionScanner;

    /** Dimension ids are needed on a hot path; building the string each time is not free. */
    private static final Map<ResourceKey<Level>, String> DIMENSION_IDS = new ConcurrentHashMap<>();

    /**
     * Containers whose contents changed and need re-reading.
     *
     * <p>A set, so an item sorter firing twenty times a second collapses into a
     * single entry, and only a bounded number are re-read per tick.
     */
    private static final Map<String, java.util.Set<Long>> DIRTY = new ConcurrentHashMap<>();

    /** Re-reads per tick. Enough to feel instant, small enough to be invisible. */
    private static final int DIRTY_BUDGET_PER_TICK = 48;

    /**
     * Past this much of a tick already spent, the drain waits for a better one.
     *
     * <p>The budget above is a count, and a count is only "invisible" while a
     * tick has room to spare. Every loaded furnace, hopper and crafter reports
     * a change as it runs, so on a base with enough of them the drain is
     * saturated permanently - forty-eight full container re-reads every tick,
     * for as long as those chunks stay loaded, on top of whatever made the tick
     * slow in the first place. Staleness is already covered where it shows:
     * {@link #refreshDirty} re-reads the containers about to be displayed.
     *
     * <p>The same threshold the region scanner drains against, for the same
     * reason - see {@code RegionScanner#drain}.
     */
    private static final long TICK_TIME_BUDGET_NANOS = 45_000_000L; // 45ms of a 50ms tick

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

    /** Whether a chunk is loaded, keyed the way the region scanner asks. */
    public static boolean isChunkLoaded(String dimensionId, long chunkKey) {
        net.minecraft.server.MinecraftServer current = server;
        if (current == null) return false;
        ServerLevel level = levelFor(dimensionId);
        return level != null && level.getChunkSource()
                .hasChunk(BlockKey.chunkX(chunkKey), BlockKey.chunkZ(chunkKey));
    }

    /**
     * Levels by dimension id, resolved once each.
     *
     * <p>Asked on hot paths - once per chunk applied during a scan, and once
     * per dimension per tick while draining changes - and the answer cannot
     * change while a server is running. Without this each call walked every
     * level in the server building and comparing strings to find one it had
     * already found sixty times that tick.
     */
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
        // These hold ServerLevels, which hold the whole world. Leaving them
        // behind would keep a stopped server's worlds alive for as long as the
        // game runs.
        LEVELS.clear();
    }

    public static String dimensionId(Level level) {
        return DIMENSION_IDS.computeIfAbsent(level.dimension(), key -> key.identifier().toString());
    }

    /**
     * Called after any block change on the server.
     *
     * <p>Hooking the single choke point through which every block change flows
     * is deliberate: it catches player breaks, explosions, pistons, fire and
     * bulk world edits alike. Per-event listeners miss most of those.
     */
    public static void onBlockChanged(Level level, BlockPos pos) {
        TrackerService tracker = current;
        if (tracker == null || !(level instanceof ServerLevel)) return;
        if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) return;

        long key = BlockKey.pack(pos.getX(), pos.getY(), pos.getZ());
        String dimensionId = dimensionId(level);
        if (tracker.index(dimensionId).get(key) == null) return;

        // Something we had indexed changed. If a container is still there the
        // next scan or save refreshes it; if not, it must leave the index now.
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !ContainerTypes.isContainer(blockEntity)) {
            tracker.remove(dimensionId, key);
        }
    }

    /**
     * Called whenever a block entity reports a change.
     *
     * <p>Marks it for re-reading; the work happens on the tick drain. Without
     * this the index only ever learns a container's contents when its chunk
     * unloads, so filling a chest you just placed would never show up.
     *
     * <p>Marking is all this does, and it has to stay that way: a single item
     * sorter produces hundreds of these per tick, and the set is what collapses
     * them into one entry each.
     */
    public static void onContainerChanged(BlockEntity blockEntity) {
        TrackerService tracker = current;
        if (tracker == null) return;

        // Cheapest test first. This runs inside setChanged, which is one of the
        // hottest methods in the game - every furnace, hopper and crafter calls
        // it as it works - and the great majority of those are not containers
        // at all. An instanceof against the object already in hand settles it
        // without reaching through to the level.
        if (!ContainerTypes.isContainer(blockEntity)) return;

        Level level = blockEntity.getLevel();
        // setChanged fires on the client too, where there is nothing to index.
        if (!(level instanceof ServerLevel)) return;

        BlockPos pos = blockEntity.getBlockPos();
        if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) return;

        DIRTY.computeIfAbsent(dimensionId(level), id -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(BlockKey.pack(pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * Re-reads a bounded number of changed containers.
     *
     * @return how many were refreshed this tick
     */
    public static int drainDirty(long tickTimeNanos) {
        if (tickTimeNanos > TICK_TIME_BUDGET_NANOS) return 0;

        TrackerService tracker = current;
        if (tracker == null || DIRTY.isEmpty()) return 0;

        int done = 0;
        // One scanner for the whole drain. It holds nothing but the tracker, so
        // building a fresh one per dimension per tick was allocating for no
        // reason on a path that runs twenty times a second.
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
            }
            if (done >= DIRTY_BUDGET_PER_TICK) break;
        }
        return done;
    }

    /**
     * Re-reads only those of {@code positions} that have actually changed since
     * they were last read.
     *
     * <p>Used before showing results. Re-reading every container about to be
     * displayed would be mostly wasted work: {@link #onContainerChanged} marks
     * anything that changes and {@link #drainDirty} re-reads it every tick, so
     * a loaded container is normally already current. The dirty set is exactly
     * the ones that are not, and it is usually empty.
     *
     * <p>Positions handled here are taken off the dirty set, so the next drain
     * does not read them a second time.
     *
     * @return how many were actually re-read
     */
    public static int refreshDirty(ServerLevel level, String dimensionId, java.util.Collection<Long> positions) {
        TrackerService tracker = current;
        if (tracker == null || level == null) return 0;

        java.util.Set<Long> dirty = DIRTY.get(dimensionId);
        if (dirty == null || dirty.isEmpty()) return 0;

        var scanner = new dev.adrian.chestindex.server.scan.LiveScanner(tracker);
        int refreshed = 0;
        for (Long pos : positions) {
            // remove() reports whether it was there, so this both tests and
            // claims the position in one step.
            if (!dirty.remove(pos)) continue;
            scanner.refreshIfLoaded(level, dimensionId, pos);
            refreshed++;
        }
        return refreshed;
    }

    /** Indexes one currently-loaded chunk, for chunks the region scan cannot use. */
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

    /** Called when a player places a block, so we can attribute ownership. */
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
        // A freshly placed container is empty, and we know that for a fact
        // rather than merely failing to see inside it.
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
