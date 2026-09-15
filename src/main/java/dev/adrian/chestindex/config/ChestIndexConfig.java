package dev.adrian.chestindex.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.adrian.chestindex.ChestIndex;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChestIndexConfig {

    private static ChestIndexConfig instance;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;

    public List<String> disabledServers = new ArrayList<>();

    public boolean disabledOn(String address) {
        return matchesHost(disabledServers, address);
    }

    public static boolean matchesHost(List<String> hosts, String address) {
        if (hosts == null || hosts.isEmpty() || address == null || address.isBlank()) return false;
        String host = host(address);
        for (String entry : hosts) {
            if (entry == null || entry.isBlank()) continue;
            String candidate = host(entry);
            if (host.equals(candidate) || host.endsWith("." + candidate)) return true;
        }
        return false;
    }

    public static String host(String address) {
        String text = address.trim().toLowerCase(Locale.ROOT);
        int slash = text.indexOf('/');
        if (slash >= 0) text = text.substring(0, slash);
        if (text.startsWith("[")) {
            int close = text.indexOf(']');
            if (close > 0) text = text.substring(1, close);
        } else {
            int colon = text.lastIndexOf(':');
            if (colon > 0 && text.indexOf(':') == colon) text = text.substring(0, colon);
        }
        while (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }

    public boolean scanOnWorldJoin = true;

    public boolean showMachines = false;

    public boolean showUtility = false;

    public boolean trackEntityContainers = true;

    public boolean showEntities = true;

    public boolean entityContainersOnVanillaServers = false;

    public List<String> machineTypes = new ArrayList<>(List.of(
            "minecraft:hopper", "minecraft:dropper", "minecraft:dispenser",
            "minecraft:crafter"));

    public List<String> utilityTypes = new ArrayList<>(List.of(
            "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
            "minecraft:brewing_stand", "minecraft:campfire", "minecraft:soul_campfire",
            "minecraft:jukebox", "minecraft:lectern", "minecraft:decorated_pot",
            "minecraft:chiseled_bookshelf"));

    public static final int UNLIMITED_RESULTS = 0;

    public int maxResults = UNLIMITED_RESULTS;

    public int resultLimit() {
        return Math.max(0, maxResults);
    }

    public boolean includeNested = true;

    public String sortMode = "COUNT";

    public int originFilter = 0;

    public String searchText = "";

    public boolean enderChestView = true;

    public boolean searchShulkerContents = true;

    public boolean litematicaButton = true;

    public boolean containerSearchButton = true;

    public int searchButtonX = -15;

    public int searchButtonY = 4;

    public Map<String, int[]> searchButtonPositions = new LinkedHashMap<>();

    public int[] searchButtonOffset(String menuKey) {
        if (menuKey != null && searchButtonPositions != null) {
            int[] stored = searchButtonPositions.get(menuKey);
            if (stored != null && stored.length == 2) return stored;
        }
        return new int[] {searchButtonX, searchButtonY};
    }

    public void setSearchButtonOffset(String menuKey, int x, int y) {
        if (menuKey == null || menuKey.isBlank()) {
            searchButtonX = x;
            searchButtonY = y;
            return;
        }
        if (searchButtonPositions == null) searchButtonPositions = new LinkedHashMap<>();
        if (machineTypes == null) machineTypes = new ArrayList<>();
        if (utilityTypes == null) utilityTypes = new ArrayList<>();
        searchButtonPositions.put(menuKey, new int[] {x, y});
    }

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

    public boolean clientSideIndex = true;

    public enum Display {
        BOXES,
        ACTION_BAR,
        BOTH,
        NONE;

        private static final Display[] VALUES = values();

        public static Display parse(String value) {
            if (value == null) return BOXES;
            for (Display display : VALUES) {
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

    public boolean openInReach = true;

    public boolean turnToTarget = true;

    public boolean assistOnServers = false;

    public List<String> assistServers = new ArrayList<>();

    public boolean assistAllowedOn(String address) {
        return assistOnServers || matchesHost(assistServers, address);
    }

    public boolean guideBeam = true;

    public boolean highlightThroughWalls = true;

    public int guideBeamFromChunks = 20;

    public double guideBeamFromBlocks() {
        return Math.max(0, guideBeamFromChunks) * 16.0;
    }

    public enum Detail {
        ALWAYS,
        SHIFT,
        OFF;

        private static final Detail[] VALUES = values();

        public static Detail parse(String value) {
            if (value == null) return SHIFT;
            for (Detail detail : VALUES) {
                if (detail.name().equalsIgnoreCase(value.trim())) return detail;
            }
            return SHIFT;
        }
    }

    public String itemDetail = Detail.SHIFT.name();

    public Detail itemDetail() {
        return Detail.parse(itemDetail);
    }

    public boolean highlightFoundSlots = true;

    public String slotHighlightStyle = SlotStyle.OUTLINE.name();

    public SlotStyle slotHighlightStyle() {
        return SlotStyle.parse(slotHighlightStyle);
    }

    public enum SlotStyle {
        OUTLINE,
        BACKGROUND,
        BOTH;

        private static final SlotStyle[] VALUES = values();

        public static SlotStyle parse(String value) {
            if (value == null) return OUTLINE;
            for (SlotStyle style : VALUES) {
                if (style.name().equalsIgnoreCase(value.trim())) return style;
            }
            return OUTLINE;
        }

        public boolean drawsOutline() {
            return this == OUTLINE || this == BOTH;
        }

        public boolean drawsBackground() {
            return this == BACKGROUND || this == BOTH;
        }

        public String label() {
            return switch (this) {
                case OUTLINE -> "outline";
                case BACKGROUND -> "background";
                case BOTH -> "both";
            };
        }
    }

    public int nearestColour = 0x9B30E0;

    public int otherColour = 0xFFD21E;

    private transient int nearestRgbFor;
    private transient float[] nearestRgb;
    private transient int otherRgbFor;
    private transient float[] otherRgb;

    public float[] nearestRgb() {
        if (nearestRgb == null || nearestRgbFor != nearestColour) {
            nearestRgbFor = nearestColour;
            nearestRgb = rgb(nearestColour);
        }
        return nearestRgb;
    }

    public float[] otherRgb() {
        if (otherRgb == null || otherRgbFor != otherColour) {
            otherRgbFor = otherColour;
            otherRgb = rgb(otherColour);
        }
        return otherRgb;
    }

    private static float[] rgb(int packed) {
        return new float[] {
                ((packed >> 16) & 0xFF) / 255.0f,
                ((packed >> 8) & 0xFF) / 255.0f,
                (packed & 0xFF) / 255.0f,
        };
    }

    public enum Shape {
        OUTLINE,
        CUBE,
        BOTH;

        public static Shape parse(String value) {
            if (value == null) return CUBE;
            for (Shape shape : values()) {
                if (shape.name().equalsIgnoreCase(value.trim())) return shape;
            }
            return CUBE;
        }

        public boolean drawsCube() {
            return this == CUBE || this == BOTH;
        }

        public boolean drawsOutline() {
            return this == OUTLINE || this == BOTH;
        }

        public String label() {
            return switch (this) {
                case OUTLINE -> "outline";
                case CUBE -> "full cube";
                case BOTH -> "cube and outline";
            };
        }
    }

    public String highlightShape = Shape.CUBE.name();

    public Shape highlightShape() {
        return Shape.parse(highlightShape);
    }

    public enum Entry {
        NONE,
        FADE,
        GROW;

        public static Entry parse(String value) {
            if (value == null) return GROW;
            for (Entry entry : values()) {
                if (entry.name().equalsIgnoreCase(value.trim())) return entry;
            }
            return GROW;
        }

        public String label() {
            return switch (this) {
                case NONE -> "appear";
                case FADE -> "fade in";
                case GROW -> "grow in";
            };
        }
    }

    public String highlightEntry = Entry.GROW.name();

    public Entry highlightEntry() {
        return Entry.parse(highlightEntry);
    }

    public int highlightCubeOpacity = 35;

    public float cubeAlpha() {
        return Math.clamp(highlightCubeOpacity, 5, 100) / 100.0f;
    }

    public boolean cheatFeatures = false;

    public boolean indexGeneratedChests = false;

    public boolean indexesGeneratedChests() {
        return cheatFeatures && indexGeneratedChests;
    }

    public boolean opensInReach() {
        return cheatFeatures && openInReach;
    }

    public boolean turnsToTarget() {
        return cheatFeatures && turnToTarget;
    }

    public boolean readsEntitiesOnVanillaServers() {
        return cheatFeatures && entityContainersOnVanillaServers;
    }

    public int highlightSeconds = 45;

    public int highlightRecedingGraceSeconds = 10;

    public static ChestIndexConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve(ChestIndex.MOD_ID + ".json");
        } catch (RuntimeException | LinkageError noLoader) {
            return null;
        }
    }

    private static ChestIndexConfig load() {
        Path path = file();
        if (path == null) return new ChestIndexConfig();
        if (!Files.isRegularFile(path)) {
            ChestIndexConfig fresh = new ChestIndexConfig();
            fresh.save();
            return fresh;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            ChestIndexConfig loaded = GSON.fromJson(reader, ChestIndexConfig.class);
            if (loaded == null) return new ChestIndexConfig();
            loaded.normalise();
            return loaded;
        } catch (IOException | RuntimeException e) {
            ChestIndex.LOG.warn("Could not read {}, using defaults: {}", path, e.toString());
            return new ChestIndexConfig();
        }
    }

    private void normalise() {
        if (disabledServers == null) disabledServers = new ArrayList<>();
        if (assistServers == null) assistServers = new ArrayList<>();
        if (searchButtonPositions == null) searchButtonPositions = new LinkedHashMap<>();
        if (machineTypes == null) machineTypes = new ArrayList<>();
        if (utilityTypes == null) utilityTypes = new ArrayList<>();
        if (searchText == null) searchText = "";
        if (sortMode == null) sortMode = "COUNT";
    }

    public void save() {
        Path path = file();
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            ChestIndex.LOG.warn("Could not write {}: {}", path, e.toString());
        }
    }

    public long highlightDurationMs() {
        return Math.max(1, highlightSeconds) * 1000L;
    }

    public long highlightRecedingGraceMs() {
        return Math.max(1, highlightRecedingGraceSeconds) * 1000L;
    }
}
