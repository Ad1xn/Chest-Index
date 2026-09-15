package dev.adrian.chestindex.server;

import dev.adrian.chestindex.ChestIndex;
import net.fabricmc.api.DedicatedServerModInitializer;

/** Dedicated-server entrypoint. Singleplayer uses the integrated server via the common path. */
public final class ChestIndexServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        ChestIndex.LOG.info("ChestIndex initialising (dedicated server)");
    }
}
