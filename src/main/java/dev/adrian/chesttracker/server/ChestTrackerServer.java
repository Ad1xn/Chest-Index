package dev.adrian.chesttracker.server;

import dev.adrian.chesttracker.ChestTracker;
import net.fabricmc.api.DedicatedServerModInitializer;

public final class ChestTrackerServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        ChestTracker.LOG.info("ChestTracker initialising (dedicated server)");
    }
}
