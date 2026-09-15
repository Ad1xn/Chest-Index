package dev.adrian.chestindex.client.index;

import dev.adrian.chestindex.ChestIndex;
import dev.adrian.chestindex.server.Trackers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Every stored index on this machine, and how to throw one away.
 *
 * <p>An index is a cache of somebody's world, and caches go wrong. A container
 * recorded through a bug stays recorded; a world edited outside the game leaves
 * entries pointing at chests that are not there; a server played once leaves a
 * directory that is never read again. There was already a command for the
 * first of those - {@code /chestindex scanworld override} - but it only
 * reaches the world you are standing in, needs operator rights, and does
 * nothing at all for the client-side indexes kept for remote servers.
 *
 * <h2>Where indexes live</h2>
 *
 * <p>Two places, because there are two kinds:
 *
 * <ul>
 *   <li>a world you host - {@code saves/<world>/data/chestindex/}, written
 *       by the integrated server;</li>
 *   <li>a server you join - {@code config/chestindex/servers/<address>/},
 *       written by this client for servers that keep no index of their own.</li>
 * </ul>
 *
 * <p>Both hold one {@code .idx} per dimension, and a world also holds the log
 * of which region files have been read. Deleting a location means deleting all
 * of that, so the next visit starts from nothing rather than from a half-truth.
 */
public final class IndexStore {

    private IndexStore() {}

    /** What kind of thing an index was built from. */
    public enum Kind {
        /** A world on this machine, hosted by the integrated server. */
        WORLD,
        /** A server this client kept its own index for. */
        SERVER
    }

    /**
     * One stored index.
     *
     * @param name what to call it - the world folder or the server address
     * @param directory where its files are
     * @param bytes total size on disk
     * @param dimensions how many {@code .idx} files it holds
     * @param active whether this is the index currently being written to
     */
    public record Location(String name, Path directory, Kind kind,
                           long bytes, int dimensions, boolean active) {

        /** Size in the units a person reads, rather than in bytes. */
        public String size() {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.0f kB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }

        public String describe() {
            String dims = dimensions + (dimensions == 1 ? " dimension" : " dimensions");
            return active ? dims + " - " + size() + " - in use now" : dims + " - " + size();
        }
    }

    /** The client-side indexes' root: {@code config/chestindex/servers}. */
    private static Path serversRoot() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(ChestIndex.MOD_ID)
                .resolve("servers");
    }

    /**
     * Every index this machine holds, worlds first and largest first within
     * each kind.
     *
     * <p>Read off disk each time it is asked for. This is a settings screen
     * being opened, not a hot path, and the alternative - a cached list - would
     * go stale the moment one was deleted.
     */
    public static List<Location> all() {
        List<Location> found = new ArrayList<>();

        Path activeWorld = activeWorldRoot();
        Path activeServer = ClientIndex.storageRoot();

        Path saves = FabricLoader.getInstance().getGameDir().resolve("saves");
        if (Files.isDirectory(saves)) {
            try (Stream<Path> worlds = Files.list(saves)) {
                for (Path world : worlds.filter(Files::isDirectory).toList()) {
                    Path directory = world.resolve("data").resolve(ChestIndex.MOD_ID);
                    // The one place every save is looked at. A world the player
                    // opens again is carried across the rename when its server
                    // starts, but one they never open again would keep an index
                    // under the old id that nothing reads and nothing removes -
                    // so this screen, which is already walking the saves, is
                    // where the rest of them get moved. The open world has been
                    // moved already and is skipped.
                    dev.adrian.chestindex.config.Migration.world(directory);
                    Location location = read(world.getFileName().toString(), directory,
                            Kind.WORLD, sameFile(directory, activeWorld));
                    if (location != null) found.add(location);
                }
            } catch (IOException unreadable) {
                ChestIndex.LOG.warn("Could not list saves: {}", unreadable.toString());
            }
        }

        Path servers = serversRoot();
        if (Files.isDirectory(servers)) {
            try (Stream<Path> keys = Files.list(servers)) {
                for (Path directory : keys.filter(Files::isDirectory).toList()) {
                    Location location = read(directory.getFileName().toString(), directory,
                            Kind.SERVER, sameFile(directory, activeServer));
                    if (location != null) found.add(location);
                }
            } catch (IOException unreadable) {
                ChestIndex.LOG.warn("Could not list stored server indexes: {}", unreadable.toString());
            }
        }

        found.sort(Comparator.comparing(Location::kind).thenComparing(
                Comparator.comparingLong(Location::bytes).reversed()));
        return found;
    }

    /**
     * One location, or null if there is no index there.
     *
     * <p>Null rather than a zero-sized entry, because a world that has never
     * been indexed has nothing to delete and listing it would only be a row
     * whose button does nothing.
     */
    private static Location read(String name, Path directory, Kind kind, boolean active) {
        if (!Files.isDirectory(directory)) return null;

        long bytes = 0;
        int dimensions = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                try {
                    bytes += Files.size(file);
                } catch (IOException ignored) {
                    // A file that vanished between listing and sizing is simply
                    // not counted; the total is a guide, not an audit.
                }
                if (file.getFileName().toString().endsWith(".idx")) dimensions++;
            }
        } catch (IOException unreadable) {
            return null;
        }
        if (dimensions == 0) return null;
        return new Location(name, directory, kind, bytes, dimensions, active);
    }

    /**
     * The world the integrated server is running, or null if none is.
     *
     * <p>Gated on there being a singleplayer server at all, so that at the
     * title screen this never loads {@link Trackers} - a class the client has
     * no other reason to touch, and whose loading is the one thing here that
     * can fail for reasons that have nothing to do with indexes.
     */
    private static Path activeWorldRoot() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getSingleplayerServer() == null) return null;
        var service = Trackers.current();
        return service == null ? null : service.storageRoot();
    }

    /** Compared by real path, so a symlinked saves folder still matches. */
    private static boolean sameFile(Path a, Path b) {
        if (a == null || b == null) return false;
        try {
            return Files.isSameFile(a, b);
        } catch (IOException notBoth) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    /**
     * Deletes one stored index, in memory as well as on disk.
     *
     * <p>The in-memory half matters and is easy to forget: deleting the files
     * of the world you are standing in achieves nothing on its own, because the
     * running service still holds every container and writes them all back at
     * the next save. So the service is emptied first, and only then the files.
     *
     * @return what went wrong, or null if it worked
     */
    public static String delete(Location location) {
        if (location.active()) {
            if (location.kind() == Kind.WORLD) {
                var service = Trackers.current();
                if (service != null) service.clearIndexes();
            } else {
                ClientIndex.clearNow();
            }
        }

        List<String> failed = new ArrayList<>();
        try (Stream<Path> files = Files.list(location.directory())) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                // The scan log too: without it the next scan would decide every
                // region file was already read and rebuild nothing.
                if (!name.endsWith(".idx") && !name.equals("scanned-regions.txt")) continue;
                try {
                    Files.delete(file);
                } catch (IOException locked) {
                    failed.add(name);
                }
            }
        } catch (IOException unreadable) {
            return "Could not read " + location.directory();
        }

        if (!failed.isEmpty()) {
            return "Could not delete " + String.join(", ", failed);
        }

        // Only if it is now empty - a world's data directory may hold other
        // mods' files, and this one has no business removing those.
        try (Stream<Path> left = Files.list(location.directory())) {
            if (left.findAny().isEmpty()) Files.delete(location.directory());
        } catch (IOException leaveIt) {
            // An empty directory left behind costs nothing.
        }
        return null;
    }

    /** Whether a world is open at all, which decides what a warning has to say. */
    public static boolean inWorld() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null;
    }
}
