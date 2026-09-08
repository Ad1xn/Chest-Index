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

/**
 * The index this client keeps for itself, for servers that have no index of
 * their own.
 *
 * <p>Everything here is built out of what the server has <b>already sent</b>
 * this client in the ordinary course of playing: the block entities in chunks
 * it gave us, and the contents of a container the player themselves opened.
 * Nothing is requested, nothing is probed, and not one packet is sent that
 * would not have been sent with the mod uninstalled. That is the whole design
 * constraint - on a server that did not ask for this mod, the mod has to be
 * invisible from the far end.
 *
 * <p>It reuses {@link TrackerService} rather than reimplementing it. Despite
 * living in the {@code server} package that class is only "the thing that owns
 * a world's indexes": a palette, a {@link WorldIndex} per dimension, and the
 * file format. None of it needs a server, and having one storage implementation
 * means a bug fixed in either place is fixed in both.
 *
 * <h2>Threading</h2>
 * Bound, read and written on the client thread only - {@link WorldIndex} is not
 * thread-safe and this one has no server thread to hide behind. Saving is the
 * exception: it takes an immutable {@link IndexCodec.Frozen} snapshot on the
 * client thread and writes it on a background one, because gzipping a few
 * megabytes in the middle of a frame is a visible stutter.
 */
public final class ClientIndex {

    private ClientIndex() {}

    /** The index for the server currently connected to, or null when unbound. */
    private static TrackerService tracker;

    /** Which server that is, as a directory name. */
    private static String serverKey;

    /** Set by any observation; cleared by a save. Nothing to write if untouched. */
    private static boolean dirty;

    private static long lastSaveAt;

    /**
     * How often an in-progress session is written out.
     *
     * <p>A disconnect saves as well, so this only covers the crash and the
     * kill: losing at most a minute of chests you opened, rather than the lot.
     */
    private static final long SAVE_INTERVAL_MS = 60_000;

    // --- Lifecycle ----------------------------------------------------------

    /** Whether there is a client-side index to talk to right now. */
    public static boolean isBound() {
        return tracker != null;
    }

    public static TrackerService tracker() {
        return tracker;
    }

    public static String serverKey() {
        return serverKey;
    }

    /**
     * Binds the index for the server just joined, loading whatever was saved
     * for it last time.
     *
     * <p>Keyed by address, so each server keeps its own world. Two servers'
     * chests in one file would be worse than useless: the coordinates collide
     * and the mod would confidently send the player to a chest on a different
     * server.
     */
    public static void bind() {
        unbind();

        String key = currentServerKey();
        if (key == null) {
            // Worth a line. Without one, "the mod remembers nothing on this
            // server" and "the mod cannot tell which server this is" look
            // identical from the outside, and only one of them is a bug here.
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

    /** Saves and drops the current index. Called on disconnect. */
    public static void unbind() {
        if (tracker != null) saveNow();
        tracker = null;
        serverKey = null;
        dirty = false;
    }

    /**
     * Binds the index if it should be bound and is not.
     *
     * <p>Belt and braces for the join. Everything the key is worked out from -
     * the server list entry, the connection - is in place by the time the join
     * event fires, but "everything" here is client state this mod does not
     * own, and the cost of it not being ready was a whole session recorded
     * nowhere. Asked again the first time something is actually observed, when
     * it certainly is ready.
     */
    public static void bindIfNeeded() {
        if (tracker != null) return;
        bind();
    }

    /** Where this client's index for the bound server lives, or null. */
    public static Path storageRoot() {
        return serverKey == null ? null : storageFor(serverKey);
    }

    /**
     * Throws away everything held for the bound server.
     *
     * <p>The in-memory half of deleting a stored index. Without it, clearing
     * the files under a live connection achieves nothing: the next autosave
     * writes the same containers straight back out again.
     */
    public static void clearNow() {
        TrackerService current = tracker;
        if (current != null) current.clearIndexes();
        dirty = false;
        lastSaveAt = System.currentTimeMillis();
    }

    /** Marks the index as having something worth writing. */
    public static void touch() {
        dirty = true;
    }

    /**
     * Writes the index out if it is time. Called from the client tick.
     *
     * <p>Cheap when there is nothing to do, which is almost always: an
     * untouched index writes nothing, and a touched one writes at most once a
     * minute.
     */
    public static void tick() {
        if (tracker == null || !dirty) return;
        long now = System.currentTimeMillis();
        if (now - lastSaveAt < SAVE_INTERVAL_MS) return;
        saveNow();
    }

    /**
     * Snapshots every dimension on this thread, then writes them on another.
     *
     * <p>The snapshot is the part that has to happen here: it copies references
     * to immutable records, which is fast, and after that the writer cannot see
     * the index change under it.
     */
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
                    // A lost save costs re-observing containers, never
                    // correctness, so it is never worth interrupting play for.
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

    // --- Where it lives -----------------------------------------------------

    private static Path storageFor(String key) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(ChestTracker.MOD_ID)
                .resolve("servers")
                .resolve(key);
    }

    /**
     * Matches {@code TrackerService}'s own naming, because that class does the
     * loading and looks for exactly these files.
     */
    private static String fileNameFor(String dimensionId) {
        return dimensionId.replace(':', '.').replace('/', '.') + ".idx";
    }

    /**
     * A directory name for the server we are on, or null if we are not on one.
     *
     * <p>The address rather than the name in the server list: the name is
     * whatever the player typed and two entries can share it, while the address
     * is what actually decides which world the chests are in.
     *
     * <p>Asked of {@link dev.adrian.chesttracker.client.Session}, which falls
     * back to the connection itself when the server-list entry is missing. It
     * can be: a direct connect, a server that transferred the client onwards,
     * and anything that joins without going through the list all leave
     * {@code getCurrentServer()} empty - and a null key here meant no index at
     * all for that server, silently, with the previous server's folder sitting
     * there looking like the only one that had ever worked.
     */
    private static String currentServerKey() {
        String address = dev.adrian.chesttracker.client.Session.serverAddress();
        return address.isEmpty() ? null : sanitise(address);
    }

    /**
     * Turns an address into something every filesystem will accept.
     *
     * <p>Lower-cased first: {@code MC.Example.NET} and {@code mc.example.net}
     * are one server, and on a case-insensitive filesystem they would otherwise
     * be one directory reached by two names - which works until the day the
     * world is on a case-sensitive one and the index silently splits in two.
     */
    static String sanitise(String address) {
        StringBuilder out = new StringBuilder(address.length());
        for (char c : address.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' ? c : '_');
        }
        // A name made entirely of separators would resolve to something
        // surprising, and "." and ".." resolve to somewhere actively wrong.
        String cleaned = out.toString().replaceAll("^[._-]+", "");
        return cleaned.isBlank() ? "server" : cleaned;
    }
}
