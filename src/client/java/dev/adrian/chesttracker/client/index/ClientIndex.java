package dev.adrian.chesttracker.client.index;

import dev.adrian.chesttracker.ChestTracker;
import dev.adrian.chesttracker.core.store.IndexCodec;
import dev.adrian.chesttracker.core.index.WorldIndex;
import dev.adrian.chesttracker.server.TrackerService;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClientIndex {

    private ClientIndex() {}

    private static TrackerService tracker;

    private static String serverKey;

    private static boolean dirty;

    private static long lastSaveAt;

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
            ChestTracker.LOG.warn("No address for the server just joined; "
                    + "the client-side index is not being kept for it");
            return;
        }

        serverKey = key;
        tracker = new TrackerService(storageFor(key));
        tracker.load();
        dirty = false;
        lastSaveAt = System.currentTimeMillis();

        ChestTracker.LOG.info("Client-side index for {}: {} containers across {} dimension(s)",
                key, tracker.totalContainers(), tracker.dimensions().size());
    }

    public static void unbind() {
        if (tracker != null) saveNow();
        tracker = null;
        serverKey = null;
        dirty = false;
    }

    public static void bindIfNeeded() {
        if (tracker != null) return;
        bind();
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
        TrackerService current = tracker;
        if (current == null) return;

        lastSaveAt = System.currentTimeMillis();
        dirty = false;

        Path root = storageFor(serverKey);
        List<Save> saves = new ArrayList<>();
        for (String dimensionId : current.dimensions()) {
            WorldIndex index = current.index(dimensionId);
            if (index.isEmpty()) continue;
            saves.add(new Save(root.resolve(fileNameFor(dimensionId)),
                    IndexCodec.Frozen.of(current.palette(), index)));
        }
        if (saves.isEmpty()) return;

        Thread writer = new Thread(() -> {
            for (Save save : saves) {
                try {
                    IndexCodec.write(save.file(), save.frozen());
                } catch (IOException e) {
                    ChestTracker.LOG.warn("Could not save the client index {}: {}",
                            save.file(), e.toString());
                }
            }
        }, "ChestTracker-ClientIndexSave");
        writer.setDaemon(true);
        writer.setPriority(Thread.MIN_PRIORITY);
        writer.start();
    }

    private record Save(Path file, IndexCodec.Frozen frozen) {}

    private static Path storageFor(String key) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(ChestTracker.MOD_ID)
                .resolve("servers")
                .resolve(key);
    }

    private static String fileNameFor(String dimensionId) {
        return dimensionId.replace(':', '.').replace('/', '.') + ".idx";
    }

    private static String currentServerKey() {
        String address = dev.adrian.chesttracker.client.Session.serverAddress();
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
