package dev.adrian.chestindex.config;

import dev.adrian.chestindex.ChestIndex;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Carries what players own across the rename from {@code chest-tracker}.
 *
 * <p>Three things live under the mod id, and all three would be silently
 * abandoned by a rename: the settings file, the per-server indexes kept beside
 * it, and the index inside each world save. The last one is the expensive one -
 * it is a whole world's scan, which on a large save is minutes of work the
 * player already waited through once.
 *
 * <p>So each is <em>moved</em> rather than copied. A copy would keep the
 * settings and the scan but leave a second set of files behind that nothing
 * ever reads again, which is the same mess in a different place.
 *
 * <p>Runs on both sides. A dedicated server owns a world index and no config
 * directory worth speaking of; a client owns all three. Nothing here assumes
 * which it is - every step is skipped when its source is absent, which is also
 * what makes this safe to leave in place and cheap to run on every launch.
 *
 * <p>Deliberately not permanent. It exists so that 1.2 is not a reset, and can
 * go once the versions that wrote {@code chest-tracker} are no longer in use.
 */
public final class Migration {

    private Migration() {}

    /** What the mod id used to be, and what the files on disk are still called. */
    private static final String OLD_ID = "chest-tracker";

    private static boolean done;

    /**
     * Moves the settings file and the stored indexes beside it.
     *
     * <p>Called before anything reads the config, because a config read that
     * happens first writes a fresh file of defaults and the move then finds its
     * destination already taken.
     */
    public static void run() {
        if (done) return;
        done = true;

        Path config = FabricLoader.getInstance().getConfigDir();
        move(config.resolve(OLD_ID + ".json"), config.resolve(ChestIndex.MOD_ID + ".json"),
                "settings");
        move(config.resolve(OLD_ID), config.resolve(ChestIndex.MOD_ID),
                "stored indexes");
    }

    /**
     * Moves one world's index, given where it now belongs.
     *
     * <p>Takes the new path and works back to the old one, rather than being
     * told both: the caller already resolves {@code data/<id>} and should not
     * have to know that this class exists in order to spell it a second way.
     *
     * @param directory the index directory under the new id
     */
    public static void world(Path directory) {
        Path parent = directory.getParent();
        if (parent == null) return;
        move(parent.resolve(OLD_ID), directory, "world index");
    }

    /**
     * Moves {@code from} to {@code to}, if that is both needed and safe.
     *
     * <p>Three cases, and only one of them moves anything:
     * <ul>
     *   <li>no source - the normal case on every launch after the first, and on
     *       a fresh install. Nothing to say.
     *   <li>a destination already there - somebody has run a build under this id
     *       before, so the newer files are the ones to keep and the old ones are
     *       not this code's to delete. Says where they are, once, and leaves
     *       them; guessing wrong here costs a world scan.
     *   <li>source, no destination - move it.
     * </ul>
     */
    private static void move(Path from, Path to, String what) {
        if (!Files.exists(from)) return;

        if (Files.exists(to)) {
            ChestIndex.LOG.warn("Both {} and {} exist; keeping the newer one. "
                    + "The old {} at {} is no longer read and can be deleted.",
                    from.getFileName(), to.getFileName(), what, from);
            return;
        }

        try {
            Files.createDirectories(to.getParent());
            try {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException acrossFilesystems) {
                // A config directory symlinked onto another volume, which is
                // rarer than it is exotic. A plain move still does the job; it
                // is only the all-or-nothing guarantee that is lost.
                Files.move(from, to);
            }
            ChestIndex.LOG.info("Moved {} from {} to {}", what, OLD_ID, ChestIndex.MOD_ID);
        } catch (IOException e) {
            // Never fatal. Failing to move an index costs a re-scan; failing to
            // start costs the player the mod.
            ChestIndex.LOG.warn("Could not move {} from {} to {}: {}", what, from, to, e.toString());
        }
    }
}
