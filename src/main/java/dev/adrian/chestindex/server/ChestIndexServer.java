package dev.adrian.chestindex.server;

import dev.adrian.chestindex.ChestIndex;
import net.fabricmc.api.DedicatedServerModInitializer;

public final class ChestIndexServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        ChestIndex.LOG.info("ChestIndex initialising (dedicated server)");
    }
}
