package dev.adrian.chesttracker;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChestTracker implements ModInitializer {
    public static final String MOD_ID = "chesttracker";
    public static final Logger LOG = LoggerFactory.getLogger("ChestTracker");

    @Override
    public void onInitialize() {
        LOG.info("ChestTracker initialising (common)");
    }
}
