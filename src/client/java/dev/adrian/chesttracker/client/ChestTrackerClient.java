package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.ChestTracker;
import net.fabricmc.api.ClientModInitializer;

public final class ChestTrackerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ChestTracker.LOG.info("ChestTracker initialising (client)");
    }
}
