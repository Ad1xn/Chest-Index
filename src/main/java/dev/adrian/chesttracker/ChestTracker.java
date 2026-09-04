package dev.adrian.chesttracker;

import dev.adrian.chesttracker.server.ChestTrackerCommands;
import dev.adrian.chesttracker.server.TrackerService;
import dev.adrian.chesttracker.server.Trackers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.server.scan.LiveScanner;
import dev.adrian.chesttracker.server.scan.RegionScanner;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChestTracker implements ModInitializer {

    public static final String MOD_ID = "chestindex";
    public static final Logger LOG = LoggerFactory.getLogger("ChestTracker");

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TrackerService tracker = new TrackerService(
                    server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MOD_ID));
            tracker.load();
            Trackers.setCurrent(tracker, server);
            LOG.info("ChestTracker ready: {} containers restored", tracker.totalContainers());

            if (ChestTrackerConfig.get().scanOnWorldJoin) {
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
                LOG.info("ChestTracker saved {} containers", tracker.totalContainers());
            }
            Trackers.clear();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RegionScanner scanner = Trackers.regionScanner();
            if (scanner == null) return;
            long tickNanos = (long) (server.getCurrentSmoothedTickTime() * 1_000_000.0f);
            scanner.drain(Trackers::isChunkLoaded, tickNanos);
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            TrackerService tracker = Trackers.current();
            if (tracker == null) return;
            new LiveScanner(tracker).scanChunk(world, chunk, Trackers.dimensionId(world));
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> ChestTrackerCommands.register(dispatcher));
    }
}
