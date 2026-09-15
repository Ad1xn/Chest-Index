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
 * client thread and writes it on a single background one, because gzipping a
 * few megabytes in the middle of a frame is a visible stutter. One thread
 * rather than one per save: see {@link #WRITER}.
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
     * One writer, reused, with never more than one save outstanding.
     *
     * <p>A save holds a snapshot of every record in the index until it has
     * finished gzipping it. This used to start a fresh minimum-priority thread
     * every minute with nothing stopping a second from beginning while the
     * first was still going - so on a large index, on a busy machine, saves
     * overlapped: each one kept a full copy of the index alive, they competed
     * for the same CPU, and every overlap made the next save slower still.
     * That compounds rather than settling, which is why it ended in a restart.
     *
     * <p>A single thread cannot race itself, and declining to start a save
     * while one is outstanding bounds what is being held to the one in flight.
     */
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ChestIndex-ClientIndexSave");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    /** Saves handed to the writer and not yet finished. */
    private static final AtomicInteger outstanding = new AtomicInteger();

    /**
     * The generation each dimension was last written at.
     *
     * <p>What turns the autosave from "write the whole index every minute" into
     * "write the dimensions that have changed". A chunk load marks the index
     * dirty whether or not it found anything new, so on ground already walked
     * the timer fired every minute for the life of the session and rewrote
     * every dimension byte for byte identically.
     */
    private static final Map<String, Long> savedGenerations = new ConcurrentHashMap<>();

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

    /** Saves and drops the current index. Called on disconnect. */
    public static void unbind() {
        // Forced: there is no later autosave to fall back on, so a write that
        // happens to be in flight must not cost this session its last minute.
        if (tracker != null) save(true);
        tracker = null;
        serverKey = null;
        dirty = false;
        // Generations belong to the service being dropped. The next server's
        // start again at zero, and a stale entry here would read as "already
        // written" and skip its first save.
        savedGenerations.clear();
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
        savedGenerations.clear();
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
        save(false);
    }

    /**
     * @param force write even if a save is already outstanding. For the
     *              disconnect, which has no later autosave to fall back on -
     *              skipping that one would lose the session.
     */
    private static void save(boolean force) {
        TrackerService current = tracker;
        if (current == null) return;

        lastSaveAt = System.currentTimeMillis();

        // A save in flight is already holding a snapshot of the whole index.
        // Starting another beside it would hold a second one. Left dirty on
        // purpose: nothing was written, so nothing may be recorded as written,
        // and the timer brings us back in a minute.
        if (!force && outstanding.get() > 0) return;

        dirty = false;

        Path root = storageFor(serverKey);
        List<Save> saves = new ArrayList<>();
        for (String dimensionId : current.dimensions()) {
            WorldIndex index = current.index(dimensionId);
            if (index.isEmpty()) continue;
            // Unchanged since it was last written. Freezing and gzipping it
            // again would produce the same bytes at the same path.
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
                        // Recorded only once it is actually on disk. A failed
                        // write that claimed its generation would never be
                        // retried, and the session would be lost silently.
                        savedGenerations.put(save.dimensionId(), save.generation());
                    } catch (IOException e) {
                        // A lost save costs re-observing containers, never
                        // correctness, so it is never worth interrupting play for.
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

    // --- Where it lives -----------------------------------------------------

    private static Path storageFor(String key) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(ChestIndex.MOD_ID)
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
     * <p>Asked of {@link dev.adrian.chestindex.client.Session}, which falls
     * back to the connection itself when the server-list entry is missing. It
     * can be: a direct connect, a server that transferred the client onwards,
     * and anything that joins without going through the list all leave
     * {@code getCurrentServer()} empty - and a null key here meant no index at
     * all for that server, silently, with the previous server's folder sitting
     * there looking like the only one that had ever worked.
     */
    private static String currentServerKey() {
        String address = dev.adrian.chestindex.client.Session.serverAddress();
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
