package dev.adrian.chestindex.client.index;

import dev.adrian.chestindex.ChestIndex;
import dev.adrian.chestindex.core.store.IndexCodec;
import dev.adrian.chestindex.core.index.WorldIndex;
import dev.adrian.chestindex.server.TrackerService;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClientIndex {

    private ClientIndex() {}

    private static TrackerService tracker;

    private static String serverKey;

    private static boolean dirty;

    private static long lastSaveAt;

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ChestIndex-ClientIndexSave");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private static final AtomicInteger outstanding = new AtomicInteger();

    private static final Map<String, Long> savedGenerations = new ConcurrentHashMap<>();

    private static final long SAVE_INTERVAL_MS = 60_000;

    public static boolean isBound() {
        return tracker != null;
    }

    public static TrackerService tracker() {
        return tracker;
    }

    public static String serverKey() {
        return serverKey;
    }

    public static void bind() {
        unbind();

        String key = currentServerKey();
        if (key == null) {
            ChestIndex.LOG.warn("No address for the server just joined; "
                    + "the client-side index is not being kept for it");
            return;
        }

        serverKey = key;
        tracker = new TrackerService(storageFor(key));
        tracker.load();
        dirty = false;
        lastSaveAt = System.currentTimeMillis();

        ChestIndex.LOG.info("Client-side index for {}: {} containers across {} dimension(s)",
                key, tracker.totalContainers(), tracker.dimensions().size());
    }

    public static void unbind() {
        if (tracker != null) save(true);
        tracker = null;
        serverKey = null;
        dirty = false;
        savedGenerations.clear();
    }

    public static void bindIfNeeded() {
        if (tracker != null) return;
        bind();
    }

    public static Path storageRoot() {
        return serverKey == null ? null : storageFor(serverKey);
    }

    public static void clearNow() {
        TrackerService current = tracker;
        if (current != null) current.clearIndexes();
        dirty = false;
        lastSaveAt = System.currentTimeMillis();
        savedGenerations.clear();
    }

    public static void touch() {
        dirty = true;
    }

    public static void tick() {
        if (tracker == null || !dirty) return;
        long now = System.currentTimeMillis();
        if (now - lastSaveAt < SAVE_INTERVAL_MS) return;
        saveNow();
    }

    public static void saveNow() {
        save(false);
    }

    private static void save(boolean force) {
        TrackerService current = tracker;
        if (current == null) return;

        lastSaveAt = System.currentTimeMillis();

        if (!force && outstanding.get() > 0) return;

        dirty = false;

        Path root = storageFor(serverKey);
        List<Save> saves = new ArrayList<>();
        for (String dimensionId : current.dimensions()) {
            WorldIndex index = current.index(dimensionId);
            if (index.isEmpty()) continue;
            long generation = current.generation(dimensionId);
            Long written = savedGenerations.get(dimensionId);
            if (written != null && written == generation) continue;
            saves.add(new Save(root.resolve(fileNameFor(dimensionId)), dimensionId, generation,
                    IndexCodec.Frozen.of(current.palette(), index)));
        }
        if (saves.isEmpty()) return;

        outstanding.incrementAndGet();
        WRITER.execute(() -> {
            try {
                for (Save save : saves) {
                    try {
                        IndexCodec.write(save.file(), save.frozen());
                        savedGenerations.put(save.dimensionId(), save.generation());
                    } catch (IOException e) {
                        ChestIndex.LOG.warn("Could not save the client index {}: {}",
                                save.file(), e.toString());
                    }
                }
            } finally {
                outstanding.decrementAndGet();
            }
        });
    }

    private record Save(Path file, String dimensionId, long generation, IndexCodec.Frozen frozen) {}

    private static Path storageFor(String key) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(ChestIndex.MOD_ID)
                .resolve("servers")
                .resolve(key);
    }

    private static String fileNameFor(String dimensionId) {
        return dimensionId.replace(':', '.').replace('/', '.') + ".idx";
    }

    private static String currentServerKey() {
        String address = dev.adrian.chestindex.client.Session.serverAddress();
        return address.isEmpty() ? null : sanitise(address);
    }

    static String sanitise(String address) {
        StringBuilder out = new StringBuilder(address.length());
        for (char c : address.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' ? c : '_');
        }
        String cleaned = out.toString().replaceAll("^[._-]+", "");
        return cleaned.isBlank() ? "server" : cleaned;
    }
}
