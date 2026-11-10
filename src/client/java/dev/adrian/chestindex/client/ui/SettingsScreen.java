package dev.adrian.chestindex.client.ui;

import dev.adrian.chestindex.client.platform.Gfx;
import dev.adrian.chestindex.client.platform.Keys;
import dev.adrian.chestindex.config.ChestIndexConfig;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

public final class SettingsScreen extends Screen {
    private static final int RAIL_BUTTON = 20;
    private static final int RAIL_GAP = 2;

    private static final int RAIL_PAD = 4;

    private static final int ROW_H = 20;

    private static final int CONTENT_W = 258;

    private static final int SCROLLBAR_W = 8;

    private static final int MIN_ROWS = 6;

    private static final int MAX_ROWS = 10;

    private static final int BUTTON_SIZE = 12;

    private static final int PAD = 2;

    private static final int BOX = 12;

    private static final int TRACK_W = 72;
    private static final int TRACK_H = 6;
    private static final int THUMB_W = 6;
    private static final int THUMB_H = 10;

    private static final String SEP = "  -  ";

    private static final int TOOLTIP_W = 180;

    private static final int MAX_RESULTS_CEILING = 2000;
    private static final int RESULTS_STEP = 50;
    private static final int HIGHLIGHT_MAX = 300;
    private static final int GRACE_MAX = 120;

    private static final int BEAM_FROM_MAX = 64;

    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

    private final Screen parent;
    private final ChestIndexConfig config = ChestIndexConfig.get();

    private int panelX;
    private int panelY;
    private int panelW;

    private int visibleRows;

    private List<Section> sections = List.of();

    private int section;

    private int scroll;

    private String filter = "";

    private EditBox filterBox;

    private List<Row> shown;

    private String hoverTitle;

    private record Hover(List<String> lines, int avoidTop, int avoidBottom) {}

    private Hover pendingTooltip;

    private final MarkerPreview preview = new MarkerPreview();

    private int draggingRow = -1;

    private boolean draggingScrollbar;

    public SettingsScreen(Screen parent) {
        super(Component.literal("ChestIndex Settings"));
        this.parent = parent;
    }

    private sealed interface Row {
        String label();

        String hint();

        String help();
    }

    private record Toggle(String label, String hint, String help,
                          BooleanSupplier get, Consumer<Boolean> set) implements Row {}

    private record Choice(String label, String hint, String help,
                          Supplier<String> value, Runnable advance) implements Row {}

    private record Range(String label, String hint, String help,
                         IntSupplier get, IntConsumer set,
                         int min, int max, int step, IntFunction<String> format) implements Row {}

    private record Link(String label, String hint, String help,
                        Supplier<Screen> open) implements Row {}

    private record Section(String name, String icon, String summary, List<Row> rows,
                           boolean preview) {
        Section(String name, String icon, String summary, List<Row> rows) {
            this(name, icon, summary, rows, false);
        }
    }

    private List<Section> buildSections() {
        return List.of(
                new Section("General", "minecraft:chest",
                        "The mod and where it runs.",
                        List.of(
                                new Toggle("ChestIndex",
                                        "Turns the whole mod on or off.",
                                        "Turns the whole mod on or off. Off means no screen, no button, no key "
                                                + "and no indexing.",
                                        () -> config.enabled, value -> config.enabled = value),
                                new Link("Servers it stays off on",
                                        "Servers where the mod turns itself off.",
                                        "Servers where the mod turns itself off. Comes with a few large public "
                                                + "servers that don't allow client mods. Remove any you like.",
                                        () -> new ServerListScreen(this,
                                                "Servers ChestIndex stays off on",
                                                "The mod is off while you're on one of these.\n"
                                                        + "example.net also covers eu.example.net.",
                                                config.disabledServers)),
                                new Toggle("Run on servers without the mod",
                                        "Keep a local index on vanilla servers.",
                                        "On vanilla servers, index the containers you open and what the server "
                                                + "sends you. Nothing extra is sent to the server.\n\nOff turns the "
                                                + "mod off on those servers.",
                                        () -> config.clientSideIndex, value -> config.clientSideIndex = value),
                                new Toggle("Scan world on join",
                                        "Scan the world files when you join.",
                                        "Scans the world files in the background when you join, so chunks you "
                                                + "haven't visited are found too.",
                                        () -> config.scanOnWorldJoin, value -> config.scanOnWorldJoin = value),
                                new Link("Stored indexes",
                                        "See and delete saved indexes.",
                                        "Every world and server with a saved index, its size, and a delete "
                                                + "button. Deleting one only means a rescan, your world is not touched.",
                                        () -> new IndexScreen(this)),
                                new Choice("LAN/server guests may search",
                                        "Who can search in your LAN world.",
                                        "Who can search when your world is open to LAN, or on a server with the "
                                                + "mod. Does nothing in singleplayer.",
                                        this::accessValue, this::cycleAccess))),

                new Section("Containers", "minecraft:hopper",
                        "Which containers are included.",
                        List.of(
                                new Toggle("Count items inside shulker boxes",
                                        "Count shulker contents as part of the chest.",
                                        "Count the items inside a shulker box as part of the chest it's in.",
                                        () -> config.includeNested, value -> config.includeNested = value),
                                new Toggle("Show machines by default",
                                        "Hoppers, droppers, dispensers, crafters.",
                                        "Hoppers, droppers, dispensers and crafters. They're always indexed, "
                                                + "this only sets whether they're shown by default.",
                                        () -> config.showMachines, value -> config.showMachines = value),
                                new Toggle("Show furnaces & pots by default",
                                        "Furnaces, brewing stands, jukeboxes, pots, shelves.",
                                        "Furnaces, brewing stands, jukeboxes, lecterns, decorated pots and "
                                                + "chiseled bookshelves.",
                                        () -> config.showUtility, value -> config.showUtility = value),
                                new Toggle("Show minecarts & boats by default",
                                        "Chest minecarts, hopper minecarts, chest boats.",
                                        "Chest minecarts, hopper minecarts and chest boats. Only found in "
                                                + "loaded chunks, and not saved.",
                                        () -> config.showEntities, value -> config.showEntities = value),
                                new Link("What counts as a machine",
                                        "Blocks the machines toggle hides.",
                                        "Blocks the machines toggle hides. Add modded machines here.",
                                        () -> new ServerListScreen(this,
                                                "Machines",
                                                "Blocks hidden by the \"Machines\" toggle.\n"
                                                        + "Anything not in a list is always shown.",
                                                config.machineTypes,
                                                ServerListScreen::asRegistryId, "hopper")),
                                new Link("What counts as a functional block",
                                        "Blocks the functional toggle hides.",
                                        "Blocks the functional blocks toggle hides.",
                                        () -> new ServerListScreen(this,
                                                "Functional blocks",
                                                "Blocks hidden by the \"Functional blocks\" toggle.\n"
                                                        + "Anything not in a list is always shown.",
                                                config.utilityTypes,
                                                ServerListScreen::asRegistryId, "jukebox")))),

                new Section("Searching", "minecraft:spyglass",
                        "The search screen.",
                        List.of(
                                new Range("Items shown",
                                        "How many items the grid shows.",
                                        "How many different items the search screen shows. The end of the "
                                                + "slider is unlimited.",
                                        () -> config.maxResults <= 0 ? MAX_RESULTS_CEILING : config.maxResults,
                                        value -> config.maxResults = value >= MAX_RESULTS_CEILING
                                                ? ChestIndexConfig.UNLIMITED_RESULTS : value,
                                        0, MAX_RESULTS_CEILING, RESULTS_STEP,
                                        value -> "Items shown: " + (value >= MAX_RESULTS_CEILING
                                                ? "unlimited" : Integer.toString(value))),
                                new Choice("Item detail",
                                        "Info about the hovered item.",
                                        "Shows how many of the hovered item there are, in how many containers, "
                                                + "how many are in shulker boxes, and how far the nearest is.",
                                        this::detailValue, this::cycleDetail),
                                new Toggle("Ender chest view",
                                        "Show your ender chest as its own view.",
                                        "Adds your ender chest next to the dimension buttons. Only shows up "
                                                + "when it's not empty.",
                                        () -> config.enderChestView, value -> config.enderChestView = value),
                                new Toggle("Search button on containers",
                                        "A search button on container screens.",
                                        "A small search button on chests and other containers. Left-click to "
                                                + "open search, right-drag to move it.",
                                        () -> config.containerSearchButton,
                                        value -> config.containerSearchButton = value),
                                new Toggle("Shulker key searches contents",
                                        "Search a shulker's contents with the key.",
                                        "Using the search key on a shulker box or bundle searches for what's "
                                                + "inside it. Off searches for the box itself.",
                                        () -> config.searchShulkerContents,
                                        value -> config.searchShulkerContents = value),
                                new Toggle("Litematica material list buttons",
                                        "Search buttons in Litematica's material list.",
                                        "Adds a search button to each row of Litematica's material list, and "
                                                + "one next to Export that finds everything still needed. "
                                                + "Needs Litematica.",
                                        () -> config.litematicaButton,
                                        value -> config.litematicaButton = value))),

                new Section("Guidance", "minecraft:compass",
                        "How found containers are shown.",
                        List.of(
                                new Choice("Show found containers as",
                                        "Boxes, text above the hotbar, or both.",
                                        "Boxes around the containers, text above the hotbar, both, or neither.",
                                        this::displayValue, this::cycleDisplay),
                                new Range("Guidance lasts",
                                        "How long markers stay after a search.",
                                        "How long markers stay after a search.",
                                        () -> config.highlightSeconds,
                                        value -> config.highlightSeconds = value,
                                        5, HIGHLIGHT_MAX, 5, value -> "Guidance lasts: " + value + "s"),
                                new Range("Grace when walking away",
                                        "How long markers stay when you walk away.",
                                        "How long markers stay once you walk away from every match.",
                                        () -> config.highlightRecedingGraceSeconds,
                                        value -> config.highlightRecedingGraceSeconds = value,
                                        1, GRACE_MAX, 1, value -> "Grace when walking away: " + value + "s"))),

                new Section("Markers", "minecraft:spectral_arrow",
                        "How markers look.",
                        List.of(
                                new Choice("Marker shape",
                                        "Full cube, outline, or both.",
                                        "Full cube, outline, or both. A double chest gets one marker.",
                                        this::shapeValue, this::cycleShape),
                                new Choice("Markers arrive by",
                                        "How markers appear after a search.",
                                        "The animation markers use when they appear.",
                                        this::entryValue, this::cycleEntry),
                                new Range("Cube solidity",
                                        "How solid the cube marker is.",
                                        "How solid the cube marker is. Doesn't affect the outline.",
                                        () -> config.highlightCubeOpacity,
                                        value -> config.highlightCubeOpacity = value,
                                        5, 100, 5, value -> "Cube solidity: " + value + "%"),
                                new Toggle("Boxes show through walls",
                                        "Draw markers through walls.",
                                        "Draws markers through walls. Turn it off if it breaks with a shader "
                                                + "or another render mod.",
                                        () -> config.highlightThroughWalls,
                                        value -> config.highlightThroughWalls = value),
                                new Toggle("Trail of marks above matches",
                                        "Works past render distance.",
                                        "A column of marks above far away matches. Works past render distance.",
                                        () -> config.guideBeam, value -> config.guideBeam = value),
                                new Range("Trails start at",
                                        "How far a match must be to get a trail.",
                                        "How far away a match has to be to get a trail.",
                                        () -> config.guideBeamFromChunks,
                                        value -> config.guideBeamFromChunks = value,
                                        0, BEAM_FROM_MAX, 1,
                                        value -> "Trails start at: " + value + " chunks"),
                                new Link("Highlight colours",
                                        "Marker colours.",
                                        "One colour for the nearest match, one for the rest.",
                                        () -> new HighlightColourScreen(this)),
                                new Choice("Marked slots",
                                        "How matching slots are marked.",
                                        "How slots with the searched item are marked in an open container.",
                                        this::slotStyleValue, this::cycleSlotStyle)), true),

                new Section("Cheats", "minecraft:command_block",
                        "Cheat-like features. All off by default.",
                        List.of(
                                new Toggle("Enable cheat-like features",
                                        "Turns on everything below.",
                                        "Nothing below works until this is on. The settings below keep their "
                                                + "values while it's off.",
                                        () -> config.cheatFeatures, value -> config.cheatFeatures = value),
                                new Toggle("Index generated chests",
                                        "Unopened loot chests, even in unvisited chunks.",
                                        "Indexes unopened loot chests, even in chunks nobody has loaded. "
                                                + "This is basically x-ray. Loot chests you've opened are always "
                                                + "indexed.",
                                        () -> config.indexGeneratedChests,
                                        value -> config.indexGeneratedChests = value),
                                new Toggle("Read minecarts on servers without the mod",
                                        "They may have moved since you saw them.",
                                        "Minecarts on servers may have moved since you saw them, so this can "
                                                + "point to the wrong place. Always on in your own world.",
                                        () -> config.entityContainersOnVanillaServers,
                                        value -> config.entityContainersOnVanillaServers = value),
                                new Toggle("Open a match in reach",
                                        "Open a match instead of pointing at it.",
                                        "Opens a found container if it's in reach, same as right-clicking it.",
                                        () -> config.openInReach, value -> config.openInReach = value),
                                new Toggle("Turn to face a match",
                                        "Turn your view to the nearest match.",
                                        "Turns your view to the nearest match. Some anti-cheats may flag this.",
                                        () -> config.turnToTarget, value -> config.turnToTarget = value),
                                new Toggle("Assist on every server",
                                        "Allow the two options above on all servers.",
                                        "Lets the two options above work on every server, not just your own "
                                                + "world. Anti-cheats may flag them.",
                                        () -> config.assistOnServers, value -> config.assistOnServers = value),
                                new Link("Servers assist is allowed on",
                                        "Servers where the two options above work.",
                                        "Servers where the two options above work. Your own world always "
                                                + "allows them.",
                                        () -> new ServerListScreen(this,
                                                "Servers assist is allowed on",
                                                "Opening and turning to matches works on these servers.\n"
                                                        + "Your own world always allows it.",
                                                config.assistServers)))));
    }

    private String shapeValue() {
        return config.highlightShape().label();
    }

    private void cycleShape() {
        ChestIndexConfig.Shape[] all = ChestIndexConfig.Shape.values();
        config.highlightShape = all[(config.highlightShape().ordinal() + 1) % all.length].name();
    }

    private String entryValue() {
        return config.highlightEntry().label();
    }

    private void cycleEntry() {
        ChestIndexConfig.Entry[] all = ChestIndexConfig.Entry.values();
        config.highlightEntry = all[(config.highlightEntry().ordinal() + 1) % all.length].name();
    }

    private String accessValue() {
        return switch (config.permissionTier()) {
            case ALL -> "everyone";
            case OWNED -> "own containers";
            case OP -> "operators";
        };
    }

    private void cycleAccess() {
        ChestIndexConfig.Access[] tiers = ChestIndexConfig.Access.values();
        config.permissionTier = tiers[(config.permissionTier().ordinal() + 1) % tiers.length].name();
    }

    private String detailValue() {
        return switch (config.itemDetail()) {
            case ALWAYS -> "always";
            case SHIFT -> "holding shift";
            case OFF -> "never";
        };
    }

    private void cycleDetail() {
        ChestIndexConfig.Detail[] modes = ChestIndexConfig.Detail.values();
        config.itemDetail = modes[(config.itemDetail().ordinal() + 1) % modes.length].name();
    }

    private String displayValue() {
        return switch (config.highlightDisplay()) {
            case BOXES -> "boxes in the world";
            case ACTION_BAR -> "text above the hotbar";
            case BOTH -> "boxes and text";
            case NONE -> "nothing";
        };
    }

    private void cycleDisplay() {
        ChestIndexConfig.Display[] modes = ChestIndexConfig.Display.values();
        config.highlightDisplay = modes[(config.highlightDisplay().ordinal() + 1) % modes.length].name();
    }

    private String slotStyleValue() {
        return config.slotHighlightStyle().label();
    }

    private void cycleSlotStyle() {
        ChestIndexConfig.SlotStyle[] styles = ChestIndexConfig.SlotStyle.values();
        config.slotHighlightStyle =
                styles[(config.slotHighlightStyle().ordinal() + 1) % styles.length].name();
    }

    @Override
    protected void init() {
        sections = buildSections();
        section = Math.max(0, Math.min(section, sections.size() - 1));

        int room = height - 32 - CHROME_H;
        int fits = Math.max(railRows(), Math.min(MAX_ROWS, room / ROW_H));

        int tallest = 0;
        for (Section candidate : sections) tallest = Math.max(tallest, candidate.rows().size());
        visibleRows = Math.max(railRows(), Math.min(fits, tallest));

        panelW = Panel.EDGE_W + RAIL_BUTTON + RAIL_PAD + CONTENT_W + Panel.EDGE_W;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH()) / 2;

        filterBox = new EditBox(font, panelX + 11, panelY + Panel.TOP_H + 4,
                panelW - 22, 10, Component.literal("Filter settings"));
        filterBox.setBordered(false);
        filterBox.setMaxLength(48);
        filterBox.setValue(filter);
        filterBox.setResponder(value -> {
            filter = value;
            scroll = 0;
            shown = null;
        });
        addRenderableWidget(filterBox);

        shown = null;
        scroll = Math.min(scroll, maxScroll());
    }

    private int railX() {
        return panelX + Panel.EDGE_W;
    }

    private int railY(int index) {
        return contentTop() + index * (RAIL_BUTTON + RAIL_GAP);
    }

    private int contentX() {
        return panelX + Panel.EDGE_W + RAIL_BUTTON + RAIL_PAD;
    }

    private int contentTop() {
        return panelY + Panel.TOP_H + Panel.SEARCH_H + PAD;
    }

    private int contentW() {
        return panelX + panelW - Panel.EDGE_W - contentX();
    }

    private int contentH() {
        return visibleRows * ROW_H + 2;
    }

    private static final int CHROME_H =
            Panel.TOP_H + Panel.SEARCH_H + PAD + 2 + PAD + Panel.BOTTOM_H;

    private int railRows() {
        int railH = sections.size() * RAIL_BUTTON + (sections.size() - 1) * RAIL_GAP;
        return (railH + ROW_H - 1) / ROW_H;
    }

    private int panelH() {
        return CHROME_H + visibleRows * ROW_H;
    }

    private boolean overflows() {
        return rows().size() > rowsVisible();
    }

    private int rowsX() {
        return contentX() + 1;
    }

    private int rowsW() {
        return contentW() - 2 - (overflows() ? SCROLLBAR_W + 1 : 0);
    }

    private boolean showsPreview() {
        return filter.isEmpty() && section >= 0 && section < sections.size()
                && sections.get(section).preview();
    }

    private int previewH() {
        return showsPreview() ? MarkerPreview.HEIGHT : 0;
    }

    private int rowsTop() {
        return contentTop() + 1 + previewH();
    }

    private int rowsVisible() {
        return Math.max(1, visibleRows - previewH() / ROW_H);
    }

    private int rowY(int index) {
        return rowsTop() + index * ROW_H;
    }

    private int scrollbarX() {
        return contentX() + contentW() - 1 - SCROLLBAR_W;
    }

    private int scrollbarTop() {
        return rowsTop();
    }

    private int scrollbarH() {
        return rowsVisible() * ROW_H;
    }

    private int maxScroll() {
        return Math.max(0, rows().size() - rowsVisible());
    }

    private int closeX() {
        return panelX + panelW - 8 - BUTTON_SIZE;
    }

    private int titleWidth() {
        return closeX() - (panelX + 8) - 4;
    }

    private List<Row> rows() {
        if (shown == null) shown = buildShown();
        return shown;
    }

    private List<Row> buildShown() {
        if (filter.isBlank()) return sections.get(section).rows();

        String needle = filter.trim().toLowerCase(Locale.ROOT);
        List<Row> matches = new ArrayList<>();
        for (Section candidate : sections) {
            for (Row row : candidate.rows()) {
                if (row.label().toLowerCase(Locale.ROOT).contains(needle)
                        || row.hint().toLowerCase(Locale.ROOT).contains(needle)
                        || row.help().toLowerCase(Locale.ROOT).contains(needle)) {
                    matches.add(row);
                }
            }
        }
        return matches;
    }

    private int totalSettings() {
        int total = 0;
        for (Section candidate : sections) total += candidate.rows().size();
        return total;
    }

    private void draw(Gfx gfx, int mouseX, int mouseY) {
        hoverTitle = null;
        pendingTooltip = null;

        drawPlaceholder(gfx);
        drawCloseButton(gfx, mouseX, mouseY);
        drawRail(gfx, mouseX, mouseY);
        Panel.well(gfx, contentX(), contentTop(), contentW(), contentH());
        drawRows(gfx, mouseX, mouseY);

        if (overflows()) {
            Panel.scrollbar(gfx, scrollbarX(), scrollbarTop(), SCROLLBAR_W, scrollbarH(),
                    scroll, maxScroll(), rows().size(), rowsVisible());
        }

        if (draggingRow >= 0 || draggingScrollbar) pendingTooltip = null;

        String title = hoverTitle != null ? hoverTitle : defaultTitle();
        gfx.text(font, Component.literal(truncate(title, titleWidth())),
                panelX + 8, panelY + 6, Panel.TEXT_MAIN);

        drawFooter(gfx);

        if (pendingTooltip != null) drawTooltip(gfx, pendingTooltip, mouseX);
    }

    private String defaultTitle() {
        if (!filter.isBlank()) {
            int matches = rows().size();
            return matches == 0
                    ? "Nothing matches"
                    : matches + " of " + totalSettings() + " settings";
        }
        String name = sections.get(section).name();
        String full = "Settings" + SEP + name;
        return font.width(full) <= titleWidth() ? full : name;
    }

    private void drawPlaceholder(Gfx gfx) {
        if (!filter.isEmpty() || filterBox == null || filterBox.isFocused()) return;
        gfx.text(font, Component.literal("Filter settings"),
                filterBox.getX(), filterBox.getY(), Panel.TEXT_MUTED);
    }

    private void drawCloseButton(Gfx gfx, int mouseX, int mouseY) {
        int x = closeX();
        int y = panelY + 3;
        boolean hovered = mouseX >= x && mouseX < x + BUTTON_SIZE
                && mouseY >= y && mouseY < y + BUTTON_SIZE;

        Panel.raised(gfx, x, y, BUTTON_SIZE, BUTTON_SIZE, false);
        if (hovered) {
            gfx.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, Panel.HOVER);
            hoverTitle = "Save and close";
        }
        gfx.text(font, "X", x + 3, y + 2, Panel.TEXT_MAIN);
    }

    private void drawRail(Gfx gfx, int mouseX, int mouseY) {
        boolean filtering = !filter.isBlank();

        for (int i = 0; i < sections.size(); i++) {
            Section candidate = sections.get(i);
            int x = railX();
            int y = railY(i);
            boolean hovered = mouseX >= x && mouseX < x + RAIL_BUTTON
                    && mouseY >= y && mouseY < y + RAIL_BUTTON;
            boolean open = !filtering && i == section;

            Panel.raised(gfx, x, y, RAIL_BUTTON, RAIL_BUTTON, open);

            ItemStack icon = iconFor(candidate.icon());
            if (icon.isEmpty()) {
                Panel.sectionIcon(gfx, i, x, y);
            } else {
                gfx.item(icon, x + 2, y + 2);
            }

            if (hovered) {
                gfx.fill(x + 1, y + 1, x + RAIL_BUTTON - 1, y + RAIL_BUTTON - 1, Panel.HOVER);
                hoverTitle = candidate.name();
                pendingTooltip = new Hover(List.of(candidate.name(), candidate.summary()),
                        y, y + RAIL_BUTTON);
            } else if (filtering) {
                gfx.fill(x + 1, y + 1, x + RAIL_BUTTON - 1, y + RAIL_BUTTON - 1, 0x50000000);
            }
        }
    }

    private void drawRows(Gfx gfx, int mouseX, int mouseY) {
        if (showsPreview()) drawPreview(gfx, mouseX, mouseY);

        List<Row> visible = rows();
        if (visible.isEmpty()) {
            drawEmpty(gfx);
            return;
        }

        int left = rowsX();
        int right = left + rowsW();
        int hovered = rowAt(mouseX, mouseY);

        for (int i = 0; i < rowsVisible(); i++) {
            int index = scroll + i;
            if (index >= visible.size()) break;

            Row row = visible.get(index);
            int y = rowY(i);

            if (i > 0) gfx.fill(left + 2, y, right - 2, y + 1, 0x18000000);

            if (index == hovered) {
                gfx.fill(left, y + 1, right, y + ROW_H, 0x30FFFFFF);
                hoverTitle = row.label();
                pendingTooltip = new Hover(tooltipFor(row), y, y + ROW_H);
            }

            drawRow(gfx, row, left, right, y, index == hovered);
        }
    }

    private void drawPreview(Gfx gfx, int mouseX, int mouseY) {
        int top = contentTop() + 1;
        preview.draw(gfx, font, rowsX(), top, rowsW(), MarkerPreview.HEIGHT);

        gfx.fill(rowsX() + 2, top + MarkerPreview.HEIGHT - 1,
                rowsX() + rowsW() - 2, top + MarkerPreview.HEIGHT, 0x18000000);

        if (preview.contains(mouseX, mouseY)) {
            hoverTitle = preview.hint();
            pendingTooltip = new Hover(List.of(preview.hint()), top,
                    top + MarkerPreview.HEIGHT);
        }
    }

    private void drawRow(Gfx gfx, Row row, int left, int right, int y, boolean hovered) {
        int textY = y + (ROW_H - 8) / 2;

        switch (row) {
            case Toggle toggle -> {
                boolean on = toggle.get().getAsBoolean();
                int boxX = right - 4 - BOX;
                gfx.text(font, Component.literal(truncate(toggle.label(), boxX - left - 8)),
                        left + 4, textY, Panel.TEXT_MAIN);
                Panel.raised(gfx, boxX, y + 4, BOX, BOX, on);
                if (on) Panel.tick(gfx, boxX + 3, y + 6, Panel.TEXT_MAIN);
            }
            case Choice choice -> {
                String value = choice.value().get();
                int valueW = font.width(value);
                int valueX = right - 12 - valueW;
                gfx.text(font, Component.literal(truncate(choice.label(), valueX - left - 8)),
                        left + 4, textY, Panel.TEXT_MAIN);
                gfx.text(font, Component.literal(value), valueX, textY, Panel.TEXT_MAIN);
                Panel.chevron(gfx, right - 8, textY + 1, hovered ? Panel.TEXT_MAIN : Panel.ICON);
            }
            case Range range -> {
                int trackX = right - 4 - TRACK_W;
                gfx.text(font,
                        Component.literal(truncate(range.format().apply(range.get().getAsInt()),
                                trackX - left - 8)),
                        left + 4, textY, Panel.TEXT_MAIN);
                drawTrack(gfx, range, trackX, y);
            }
            case Link link -> {
                gfx.text(font, Component.literal(truncate(link.label() + "...", right - left - 16)),
                        left + 4, textY, Panel.TEXT_MAIN);
                Panel.chevron(gfx, right - 8, textY + 1, hovered ? Panel.TEXT_MAIN : Panel.ICON);
            }
        }
    }

    private void drawTrack(Gfx gfx, Range range, int trackX, int y) {
        int trackY = y + (ROW_H - TRACK_H) / 2;
        Panel.groove(gfx, trackX, trackY, TRACK_W, TRACK_H);

        double fraction = fractionOf(range);
        int travel = TRACK_W - THUMB_W;
        int filled = (int) Math.round(travel * fraction);
        if (filled > 0) {
            gfx.fill(trackX + 1, trackY + 1, trackX + 1 + filled, trackY + TRACK_H - 1, Panel.ON);
        }

        int thumbX = trackX + filled;
        Panel.raised(gfx, thumbX, y + (ROW_H - THUMB_H) / 2, THUMB_W, THUMB_H, false);
    }

    private static double fractionOf(Range range) {
        int value = range.get().getAsInt();
        return Math.min(1.0, Math.max(0.0,
                (value - range.min()) / (double) (range.max() - range.min())));
    }

    private void drawEmpty(Gfx gfx) {
        String message = "No setting mentions \"" + truncate(filter.trim(), 120) + "\".";
        gfx.text(font, Component.literal(message),
                contentX() + (contentW() - font.width(message)) / 2,
                contentTop() + contentH() / 2 - 4, Panel.TEXT_MUTED);
    }

    private void drawFooter(Gfx gfx) {
        String footer = pendingTooltip != null && !shiftHeld()
                ? "Hold shift for the full explanation."
                : filter.isBlank()
                ? sections.get(section).summary()
                : "Filtering every section. Escape clears it.";
        gfx.text(font, Component.literal(footer),
                panelX + (panelW - font.width(footer)) / 2, panelY + panelH() + 6,
                Panel.TEXT_MUTED);
    }

    private List<String> tooltipFor(Row row) {
        List<String> lines = new ArrayList<>();
        lines.add(row.label());
        if (!shiftHeld()) {
            lines.add(row.hint());
            return lines;
        }
        String[] paragraphs = row.help().split("\n\n");
        for (int i = 0; i < paragraphs.length; i++) {
            if (i > 0) lines.add("");
            lines.addAll(wrap(paragraphs[i].replace('\n', ' '), TOOLTIP_W));
        }
        return lines;
    }

    private boolean shiftHeld() {
        return Keys.shiftHeld();
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" +")) {
            if (word.isEmpty()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) lines.add(line.toString());
            line.setLength(0);
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private void drawTooltip(Gfx gfx, Hover hover, int mouseX) {
        Panel.tooltip(gfx, font, hover.lines(), mouseX, width, height,
                hover.avoidTop(), hover.avoidBottom());
    }

    private String truncate(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static ItemStack iconFor(String itemId) {
        ItemStack cached = ICON_CACHE.get(itemId);
        if (cached != null) return cached;

        Identifier identifier = Identifier.tryParse(itemId);
        Item item = identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
        if (item == null) return ItemStack.EMPTY;

        try {
            ItemStack icon = new ItemStack(item);
            ICON_CACHE.put(itemId, icon);
            return icon;
        } catch (RuntimeException notBoundYet) {
            return ItemStack.EMPTY;
        }
    }

    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < rowsX() || mouseX >= rowsX() + rowsW()) return -1;
        int top = rowsTop();
        if (mouseY < top || mouseY >= top + rowsVisible() * ROW_H) return -1;
        int index = scroll + (mouseY - top) / ROW_H;
        return index < rows().size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        if (clickClose(mouseX, mouseY)) return true;
        if (clickRail(mouseX, mouseY)) return true;

        if (overflows() && mouseX >= scrollbarX() && mouseX < scrollbarX() + SCROLLBAR_W
                && mouseY >= scrollbarTop() && mouseY < scrollbarTop() + scrollbarH()) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return true;
        }

        if (clickRow(mouseX, mouseY)) return true;

        return super.mouseClicked(event, doubleClick);
    }

    private boolean clickClose(int mouseX, int mouseY) {
        int x = closeX();
        int y = panelY + 3;
        if (mouseX < x || mouseX >= x + BUTTON_SIZE || mouseY < y || mouseY >= y + BUTTON_SIZE) {
            return false;
        }
        VanillaButton.playClick();
        onClose();
        return true;
    }

    private boolean clickRail(int mouseX, int mouseY) {
        if (mouseX < railX() || mouseX >= railX() + RAIL_BUTTON) return false;

        for (int i = 0; i < sections.size(); i++) {
            int y = railY(i);
            if (mouseY < y || mouseY >= y + RAIL_BUTTON) continue;
            section = i;
            scroll = 0;
            if (!filter.isBlank()) {
                filter = "";
                filterBox.setValue("");
            }
            shown = null;
            VanillaButton.playClick();
            return true;
        }
        return false;
    }

    private boolean clickRow(int mouseX, int mouseY) {
        if (showsPreview() && preview.click(mouseX, mouseY)) {
            VanillaButton.playClick();
            return true;
        }

        int index = rowAt(mouseX, mouseY);
        if (index < 0) return false;

        Row row = rows().get(index);

        switch (row) {
            case Toggle toggle -> {
                toggle.set().accept(!toggle.get().getAsBoolean());
                VanillaButton.playClick();
            }
            case Choice choice -> {
                choice.advance().run();
                VanillaButton.playClick();
            }
            case Range range -> {
                int trackX = rowsX() + rowsW() - 4 - TRACK_W;
                if (mouseX < trackX - 2 || mouseX >= trackX + TRACK_W + 2) return true;
                draggingRow = index;
                applyRange(range, mouseX, trackX);
            }
            case Link link -> {
                VanillaButton.playClick();
                config.save();
                minecraft.setScreenAndShow(link.open().get());
            }
        }
        return true;
    }

    private void applyRange(Range range, int mouseX, int trackX) {
        int travel = TRACK_W - THUMB_W;
        double fraction = Math.min(1.0, Math.max(0.0,
                (mouseX - trackX - THUMB_W / 2.0) / travel));
        int raw = (int) Math.round(range.min() + fraction * (range.max() - range.min()));
        int snapped = Math.round(raw / (float) range.step()) * range.step();
        range.set().accept(Math.min(range.max(), Math.max(range.min(), snapped)));
    }

    private void dragScrollbar(int mouseY) {
        int max = maxScroll();
        if (max == 0) return;
        double fraction = (mouseY - scrollbarTop()) / (double) Math.max(1, scrollbarH());
        scroll = Math.max(0, Math.min(max, (int) Math.round(fraction * max)));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            dragScrollbar((int) event.y());
            return true;
        }
        if (draggingRow >= 0 && draggingRow < rows().size()
                && rows().get(draggingRow) instanceof Range range) {
            applyRange(range, (int) event.x(), rowsX() + rowsW() - 4 - TRACK_W);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        draggingRow = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int max = maxScroll();
        if (max == 0) return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(deltaY)));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == Keys.ESCAPE && !filter.isBlank()) {
            filter = "";
            filterBox.setValue("");
            shown = null;
            scroll = 0;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        config.save();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    //? if >=26.1 {
    /*@Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Gfx gfx = new Gfx(graphics);
        Panel.window(gfx, panelX, panelY, panelW, panelH());
        Panel.searchStrip(gfx, panelX, panelY + Panel.TOP_H, panelW);
    }
    *///?} else {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Gfx gfx = new Gfx(graphics);
        Panel.window(gfx, panelX, panelY, panelW, panelH());
        Panel.searchStrip(gfx, panelX, panelY + Panel.TOP_H, panelW);
    }
    //?}

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        draw(new Gfx(graphics), mouseX, mouseY);
    }
    *///?} else {
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        draw(new Gfx(graphics), mouseX, mouseY);
    }
    //?}
}
