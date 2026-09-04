package dev.adrian.chesttracker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.adrian.chesttracker.ChestTracker;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ChestTrackerConfig {

    private static ChestTrackerConfig instance;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean scanOnWorldJoin = true;

    public boolean showMachines = false;

    public int maxResults = 900;

    public static final int UNLIMITED_RESULTS = 0;

    public int resultLimit() {
        return Math.max(0, maxResults);
    }

    public boolean includeNested = true;

    public String sortMode = "COUNT";

    public int originFilter = 0;

    public String searchText = "";

    public boolean containerSearchButton = true;

    public int searchButtonX = -15;

    public int searchButtonY = 4;

    public enum Access {
        ALL,
        OWNED,
        OP;

        public static Access parse(String value) {
            if (value == null) return ALL;
            for (Access access : values()) {
                if (access.name().equalsIgnoreCase(value.trim())) return access;
            }
            return ALL;
        }
    }

    public String permissionTier = Access.ALL.name();

    public Access permissionTier() {
        return Access.parse(permissionTier);
    }

    public int serverHelloTimeoutMs = 3000;

    public enum Display {
        BOXES,
        ACTION_BAR,
        BOTH,
        NONE;

        public static Display parse(String value) {
            if (value == null) return BOXES;
            for (Display display : values()) {
                if (display.name().equalsIgnoreCase(value.trim())) return display;
            }
            return BOXES;
        }

        public boolean drawsBoxes() {
            return this == BOXES || this == BOTH;
        }

        public boolean writesActionBar() {
            return this == ACTION_BAR || this == BOTH;
        }
    }

    public String highlightDisplay = Display.BOXES.name();

    public Display highlightDisplay() {
        return Display.parse(highlightDisplay);
    }

    public int highlightSeconds = 45;

    public int highlightRecedingGraceSeconds = 10;

    public static ChestTrackerConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(ChestTracker.MOD_ID + ".json");
    }

    private static ChestTrackerConfig load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            ChestTrackerConfig fresh = new ChestTrackerConfig();
            fresh.save();
            return fresh;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            ChestTrackerConfig loaded = GSON.fromJson(reader, ChestTrackerConfig.class);
            return loaded == null ? new ChestTrackerConfig() : loaded;
        } catch (IOException | RuntimeException e) {
            ChestTracker.LOG.warn("Could not read {}, using defaults: {}", path, e.toString());
            return new ChestTrackerConfig();
        }
    }

    public void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            ChestTracker.LOG.warn("Could not write {}: {}", path, e.toString());
        }
    }

    public long highlightDurationMs() {
        return Math.max(1, highlightSeconds) * 1000L;
    }

    public long highlightRecedingGraceMs() {
        return Math.max(1, highlightRecedingGraceSeconds) * 1000L;
    }
}
