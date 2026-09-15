package dev.adrian.chestindex;

import dev.adrian.chestindex.net.ChestIndexNetwork;
import dev.adrian.chestindex.server.ChestIndexCommands;
import dev.adrian.chestindex.server.TrackerService;
import dev.adrian.chestindex.server.Trackers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.server.scan.LiveScanner;
import dev.adrian.chestindex.server.scan.RegionScanner;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. Runs on every environment, including dedicated servers, so
 * nothing reachable from here may touch client-only classes.
 */
public final class ChestIndex implements ModInitializer {

    // The internal id must differ from the original Chest Tracker's, or Fabric
    // resolves the collision by id and this mod silently never loads. The
    // display name stays "ChestIndex"; only the id and its derived paths move.
    public static final String MOD_ID = "chestindex";
    public static final Logger LOG = LoggerFactory.getLogger("ChestIndex");

    @Override
    public void onInitialize() {
        // Before anything else, and before the first config read in particular:
        // reading the config writes a fresh file of defaults when none is
        // there, which would take the destination this is trying to move the
        // player's own settings into.
        dev.adrian.chestindex.config.Migration.run();

        // Payload types must be registered identically on both sides, so this
        // runs from the common entrypoint rather than the server one. The
        // handlers below it are server-side and inert on a client.
        ChestIndexNetwork.registerTypes();
        ChestIndexNetwork.registerServerHandlers();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Singleplayer runs an integrated server, so this is also the path
            // that gives the client full access to its own world.
            java.nio.file.Path index =
                    server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MOD_ID);
            // Per world, not once at startup: every save has its own index, and
            // which ones this player will ever open is not knowable from here.
            dev.adrian.chestindex.config.Migration.world(index);

            TrackerService tracker = new TrackerService(index);
            tracker.load();
            Trackers.setCurrent(tracker, server);
            LOG.info("ChestIndex ready: {} containers restored", tracker.totalContainers());

            if (ChestIndexConfig.get().scanOnWorldJoin) {
                // Background, never at join: a full region scan of a large world
                // would freeze the game for as long as it takes. This yields
                // under load and simply finishes when it finishes.
                RegionScanner scanner = Trackers.regionScanner();
                if (scanner != null) {
                    scanner.start(server.getWorldPath(LevelResource.ROOT), server.overworld().getGameTime());
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TrackerService tracker = Trackers.current();
            if (tracker != null) {
                tracker.save();
                LOG.info("ChestIndex saved {} containers", tracker.totalContainers());
            }
            Trackers.clear();
            ChestIndexNetwork.forgetAll();
        });

        // Applying scan results happens here, on the server thread, under a
        // per-tick budget. The scanner thread only ever reads and parses.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // How much of this tick is already gone. Both drains below stand
            // down on a tick that is already late rather than adding to it.
            long tickNanos = (long) (server.getCurrentSmoothedTickTime() * 1_000_000.0f);

            // Containers whose contents changed since last tick. Without this the
            // index only learns contents when a chunk unloads, so filling a chest
            // you just placed would never show up.
            Trackers.drainDirty(tickNanos);

            // Tell anyone with the screen open that what they are looking at
            // has moved. Rate-limited inside; the drain above is what makes the
            // change worth reporting.
            ChestIndexNetwork.flushChanges(server);

            RegionScanner scanner = Trackers.regionScanner();
            if (scanner == null) return;
            scanner.drain(Trackers::isChunkLoaded, Trackers::liveScanChunk, tickNanos);
        });

        // A chunk about to unload is frozen from here on, so this is the one
        // moment its contents are worth capturing: exactly once, and final.
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            TrackerService tracker = Trackers.current();
            if (tracker == null) return;
            new LiveScanner(tracker).scanChunk(world, chunk, Trackers.dimensionId(world));
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> ChestIndexCommands.register(dispatcher));
    }
}
