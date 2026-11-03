package dev.adrian.chestindex.client.index;

import dev.adrian.chestindex.ChestIndex;
import dev.adrian.chestindex.client.net.ServerLink;
import dev.adrian.chestindex.server.Trackers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class IndexStore {
    private IndexStore() {}

    public enum Kind {
        WORLD,
        SERVER,
        HOSTED
    }

    public record Location(String name, Path directory, Kind kind,
                           long bytes, int dimensions, boolean active) {
        public String size() {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.0f kB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }

        public String describe() {
            if (kind == Kind.HOSTED) return "Kept by the server - in use now";
            String dims = dimensions + (dimensions == 1 ? " dimension" : " dimensions");
            return active ? dims + " - " + size() + " - in use now" : dims + " - " + size();
        }
    }

    private static Path serversRoot() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(ChestIndex.MOD_ID)
                .resolve("servers");
    }

    public static List<Location> all() {
        List<Location> found = new ArrayList<>();

        Path activeWorld = activeWorldRoot();
        Path activeServer = ClientIndex.storageRoot();

        Path saves = FabricLoader.getInstance().getGameDir().resolve("saves");
        if (Files.isDirectory(saves)) {
            try (Stream<Path> worlds = Files.list(saves)) {
                for (Path world : worlds.filter(Files::isDirectory).toList()) {
                    Path directory = world.resolve("data").resolve(ChestIndex.MOD_ID);
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

        Location hosted = hosted();
        if (hosted != null) found.add(0, hosted);
        return found;
    }

    private static Location hosted() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.hasSingleplayerServer() || client.getConnection() == null) return null;
        if (ServerLink.state() != ServerLink.State.PRESENT) return null;
        ServerData server = client.getCurrentServer();
        return new Location(server == null ? "This server" : server.name, null, Kind.HOSTED, 0, 0, true);
    }

    public static boolean mayRecheckHosted() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) return false;
        var chestindex = client.getConnection().getCommands().getRoot().getChild("chestindex");
        return chestindex != null && chestindex.getChild("recheck") != null;
    }

    private static Location read(String name, Path directory, Kind kind, boolean active) {
        if (!Files.isDirectory(directory)) return null;

        long bytes = 0;
        int dimensions = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                try {
                    bytes += Files.size(file);
                } catch (IOException ignored) {
                }
                if (file.getFileName().toString().endsWith(".idx")) dimensions++;
            }
        } catch (IOException unreadable) {
            return null;
        }
        if (dimensions == 0) return null;
        return new Location(name, directory, kind, bytes, dimensions, active);
    }

    private static Path activeWorldRoot() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getSingleplayerServer() == null) return null;
        var service = Trackers.current();
        return service == null ? null : service.storageRoot();
    }

    private static boolean sameFile(Path a, Path b) {
        if (a == null || b == null) return false;
        try {
            return Files.isSameFile(a, b);
        } catch (IOException notBoth) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

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

        try (Stream<Path> left = Files.list(location.directory())) {
            if (left.findAny().isEmpty()) Files.delete(location.directory());
        } catch (IOException leaveIt) {
        }
        return null;
    }

    public static boolean inWorld() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null;
    }
}
