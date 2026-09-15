package dev.adrian.chestindex.config;

import dev.adrian.chestindex.ChestIndex;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Migration {

    private Migration() {}

    private static final String OLD_ID = "chest-tracker";

    private static boolean done;

    private static boolean newer(Path a, Path b) {
        try {
            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b)) > 0;
        } catch (IOException unreadable) {
            return false;
        }
    }

    public static void run() {
        if (done) return;
        done = true;

        Path config = FabricLoader.getInstance().getConfigDir();
        move(config.resolve(OLD_ID + ".json"), config.resolve(ChestIndex.MOD_ID + ".json"),
                "settings");
        move(config.resolve(OLD_ID), config.resolve(ChestIndex.MOD_ID),
                "stored indexes");
    }

    public static void world(Path directory) {
        Path parent = directory.getParent();
        if (parent == null) return;
        move(parent.resolve(OLD_ID), directory, "world index");
    }

    private static void move(Path from, Path to, String what) {
        if (!Files.exists(from)) return;

        if (Files.exists(to)) {
            if (newer(to, from)) {
                ChestIndex.LOG.info("Keeping the newer {} already at {}; the older one at {} "
                        + "is no longer read.", what, to, from);
                return;
            }
            Path aside = to.resolveSibling(to.getFileName() + ".superseded");
            try {
                Files.move(to, aside, StandardCopyOption.REPLACE_EXISTING);
                ChestIndex.LOG.info("Set aside a stale {} at {}", what, aside);
            } catch (IOException e) {
                ChestIndex.LOG.warn("Could not set aside {}: {}", to, e.toString());
                return;
            }
        }

        try {
            Files.createDirectories(to.getParent());
            try {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException acrossFilesystems) {
                Files.move(from, to);
            }
            ChestIndex.LOG.info("Moved {} from {} to {}", what, OLD_ID, ChestIndex.MOD_ID);
        } catch (IOException e) {
            ChestIndex.LOG.warn("Could not move {} from {} to {}: {}", what, from, to, e.toString());
        }
    }
}
