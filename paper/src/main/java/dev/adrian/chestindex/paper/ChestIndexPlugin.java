package dev.adrian.chestindex.paper;

import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.core.util.BlockKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChestIndexPlugin extends JavaPlugin implements PluginMessageListener {
    private static final int SIGNAL_INTERVAL_TICKS = 10;
    private static final int DIRTY_BUDGET_PER_TICK = 48;
    private static final long DIRTY_SLICE_NANOS = 2_000_000L;
    private static final double BUSY_TICK_MS = 45.0;

    private record Watcher(String dimensionId, long generation) {}

    private final Map<UUID, Watcher> watchers = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> dirty = new ConcurrentHashMap<>();

    private Settings settings;
    private Tracker tracker;
    private RegionScan regionScan;
    private LiveScan liveScan;
    private Queries queries;
    private BukkitTask ticker;
    private int sinceLastSignal;

    static String dimensionId(World world) {
        return world.getKey().toString();
    }

    Settings settings() {
        return settings;
    }

    Tracker tracker() {
        return tracker;
    }

    RegionScan regionScan() {
        return regionScan;
    }

    LiveScan liveScan() {
        return liveScan;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig());

        tracker = new Tracker(getDataFolder().toPath().resolve("index"), getLogger());
        tracker.load();
        regionScan = new RegionScan(tracker, getLogger());
        liveScan = new LiveScan(tracker);
        queries = new Queries(tracker, this);
        getLogger().info("ChestIndex ready: " + tracker.totalContainers() + " containers restored");

        for (String channel : Channels.INCOMING) getServer().getMessenger().registerIncomingPluginChannel(this, channel, this);
        for (String channel : Channels.OUTGOING) getServer().getMessenger().registerOutgoingPluginChannel(this, channel);

        getServer().getPluginManager().registerEvents(new Listeners(this), this);

        var command = getCommand("chestindex");
        if (command != null) {
            Commands commands = new Commands(this);
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }

        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);

        Map<String, Set<Long>> legacy = tracker.takeLegacyGuesses();
        if (settings.scanOnStart() || !legacy.isEmpty()) {
            Bukkit.getScheduler().runTask(this, () -> startScan(legacy, settings.scanOnStart()));
        }

        for (Player player : Bukkit.getOnlinePlayers()) greet(player);
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (regionScan != null) regionScan.cancel();
        if (tracker != null) {
            tracker.save();
            getLogger().info("ChestIndex saved " + tracker.totalContainers() + " containers");
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        watchers.clear();
        dirty.clear();
    }

    void reloadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig());
        for (Player player : Bukkit.getOnlinePlayers()) greet(player);
    }

    List<RegionScan.Dimension> dimensions() {
        List<RegionScan.Dimension> dimensions = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            String id = dimensionId(world);
            Path regionDir = RegionScan.regionDirOf(world.getWorldFolder().toPath(), id);
            if (regionDir != null) dimensions.add(new RegionScan.Dimension(id, regionDir));
        }
        return dimensions;
    }

    boolean startScan(Map<String, Set<Long>> refine, boolean scan) {
        long tick = Bukkit.getWorlds().isEmpty() ? 0L : Bukkit.getWorlds().get(0).getGameTime();
        return regionScan.start(dimensions(), tick, refine, scan, this::announce);
    }

    private void announce(String line) {
        Bukkit.getScheduler().runTask(this, () -> {
            Component message = Component.text("ChestIndex  ", NamedTextColor.GRAY)
                    .append(Component.text(line, NamedTextColor.WHITE));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (Queries.isOperator(player)) player.sendMessage(message);
            }
        });
    }

    World worldFor(String dimensionId) {
        for (World world : Bukkit.getWorlds()) {
            if (dimensionId(world).equals(dimensionId)) return world;
        }
        return null;
    }

    private boolean isChunkLoaded(String dimensionId, long chunkKey) {
        World world = worldFor(dimensionId);
        return world != null && world.isChunkLoaded(BlockKey.chunkX(chunkKey), BlockKey.chunkZ(chunkKey));
    }

    private void liveScanChunk(String dimensionId, long chunkKey) {
        World world = worldFor(dimensionId);
        if (world == null || !world.isChunkLoaded(BlockKey.chunkX(chunkKey), BlockKey.chunkZ(chunkKey))) return;
        Chunk chunk = world.getChunkAt(BlockKey.chunkX(chunkKey), BlockKey.chunkZ(chunkKey));
        liveScan.scanChunk(chunk, dimensionId);
    }

    void markDirty(World world, int x, int y, int z) {
        if (!BlockKey.isRepresentable(x, y, z)) return;
        dirty.computeIfAbsent(dimensionId(world), id -> ConcurrentHashMap.newKeySet()).add(BlockKey.pack(x, y, z));
    }

    int refreshDirty(String dimensionId, Collection<Long> positions) {
        Set<Long> pending = dirty.get(dimensionId);
        if (pending == null || pending.isEmpty()) return 0;
        World world = worldFor(dimensionId);
        if (world == null) return 0;
        int refreshed = 0;
        for (Long pos : positions) {
            if (!pending.remove(pos)) continue;
            liveScan.refreshIfLoaded(world, dimensionId, pos);
            refreshed++;
        }
        return refreshed;
    }

    private void tick() {
        boolean busy = Bukkit.getAverageTickTime() > BUSY_TICK_MS;
        if (!busy) drainDirty();
        flushChanges();
        if (!busy) regionScan.drain(this::isChunkLoaded, this::liveScanChunk, settings.indexUnlootedGeneratedChests());
    }

    private void drainDirty() {
        if (dirty.isEmpty()) return;
        int done = 0;
        long deadline = System.nanoTime() + DIRTY_SLICE_NANOS;
        for (Map.Entry<String, Set<Long>> entry : dirty.entrySet()) {
            World world = worldFor(entry.getKey());
            Set<Long> positions = entry.getValue();
            if (world == null) {
                positions.clear();
                continue;
            }
            var iterator = positions.iterator();
            while (iterator.hasNext() && done < DIRTY_BUDGET_PER_TICK) {
                long pos = iterator.next();
                iterator.remove();
                liveScan.refreshIfLoaded(world, entry.getKey(), pos);
                done++;
                if (System.nanoTime() >= deadline) return;
            }
            if (done >= DIRTY_BUDGET_PER_TICK) return;
        }
    }

    private void flushChanges() {
        if (watchers.isEmpty()) return;
        if (++sinceLastSignal < SIGNAL_INTERVAL_TICKS) return;
        sinceLastSignal = 0;

        for (Map.Entry<UUID, Watcher> entry : watchers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                watchers.remove(entry.getKey());
                continue;
            }
            String dimensionId = dimensionId(player.getWorld());
            long generation = tracker.generation(dimensionId);
            Watcher watcher = entry.getValue();
            if (watcher.generation() == generation && watcher.dimensionId().equals(dimensionId)) continue;
            watchers.put(entry.getKey(), new Watcher(dimensionId, generation));
            send(player, Channels.INDEX_CHANGED, Channels.INDEX_CHANGED_BODY);
        }
    }

    void forget(Player player) {
        watchers.remove(player.getUniqueId());
    }

    void greet(Player player) {
        send(player, Channels.HELLO, Channels.hello(new QueryDto.Hello(QueryDto.Hello.PROTOCOL_VERSION,
                Queries.mayQuery(player, settings.access()))));
    }

    private void send(Player player, String channel, byte[] body) {
        if (!player.getListeningPluginChannels().contains(channel)) return;
        player.sendPluginMessage(this, channel, body);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        try {
            switch (channel) {
                case Channels.SUMMARY_REQUEST -> send(player, Channels.SUMMARY_RESPONSE,
                        Channels.summaryResponse(queries.summarise(player, Channels.summaryRequest(message))));
                case Channels.CONTAINER_REQUEST -> send(player, Channels.CONTAINER_RESPONSE,
                        Channels.containerResponse(queries.containers(player, Channels.containerRequest(message))));
                case Channels.STATUS_REQUEST -> send(player, Channels.STATUS_RESPONSE,
                        Channels.statusResponse(queries.status(player, Channels.statusRequest(message))));
                case Channels.SUBSCRIBE -> {
                    if (!Channels.subscribe(message)) {
                        watchers.remove(player.getUniqueId());
                        return;
                    }
                    String dimensionId = dimensionId(player.getWorld());
                    watchers.put(player.getUniqueId(), new Watcher(dimensionId, tracker.generation(dimensionId)));
                }
                default -> { }
            }
        } catch (IllegalArgumentException malformed) {
            getLogger().fine("Ignoring a malformed " + channel + " from " + player.getName() + ": "
                    + malformed.getMessage());
        }
    }
}
