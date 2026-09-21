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

public final class ChestIndex implements ModInitializer {
    public static final String MOD_ID = "chestindex";
    public static final Logger LOG = LoggerFactory.getLogger("ChestIndex");

    @Override
    public void onInitialize() {
        dev.adrian.chestindex.config.Migration.run();

        ChestIndexNetwork.registerTypes();
        ChestIndexNetwork.registerServerHandlers();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            java.nio.file.Path index =
                    server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MOD_ID);
            dev.adrian.chestindex.config.Migration.world(index);

            TrackerService tracker = new TrackerService(index);
            tracker.load();
            Trackers.setCurrent(tracker, server);
            LOG.info("ChestIndex ready: {} containers restored", tracker.totalContainers());

            if (ChestIndexConfig.get().scanOnWorldJoin) {
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

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tickNanos = (long) (server.getCurrentSmoothedTickTime() * 1_000_000.0f);

            Trackers.drainDirty(tickNanos);

            ChestIndexNetwork.flushChanges(server);

            RegionScanner scanner = Trackers.regionScanner();
            if (scanner == null) return;
            scanner.drain(Trackers::isChunkLoaded, Trackers::liveScanChunk, tickNanos);
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            TrackerService tracker = Trackers.current();
            if (tracker == null) return;
            new LiveScanner(tracker).scanChunk(world, chunk, Trackers.dimensionId(world));
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> ChestIndexCommands.register(dispatcher));
    }
}
