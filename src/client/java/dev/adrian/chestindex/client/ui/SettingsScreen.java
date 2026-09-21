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
                        "The mod as a whole, and where it is allowed to run.",
                        List.of(
                                new Toggle("ChestIndex",
                                        "The whole mod, on or off.",
                                        "The whole mod. Off means no search screen, no button on containers, "
                                                + "no key, nothing indexed and nothing drawn in the world - not "
                                                + "merely hidden.",
                                        () -> config.enabled, value -> config.enabled = value),
                                new Link("Servers it stays off on",
                                        "Addresses the mod switches itself off on.",
                                        "Addresses the mod turns itself off on. Starts with four large public "
                                                + "servers whose rules on client mods are strict; remove any of "
                                                + "them if you would rather decide for yourself.",
                                        () -> new ServerListScreen(this,
                                                "Servers ChestIndex stays off on",
                                                "Joining one of these switches the mod off for as long as you are\n"
                                                        + "connected. Matched by host, so example.net covers eu.example.net.",
                                                config.disabledServers)),
                                new Toggle("Run on servers without the mod",
                                        "Whether a vanilla server gets a client-side index.",
                                        "On a vanilla server the only index there can be is one this client "
                                                + "keeps for itself, built from what the server already sends you "
                                                + "and the containers you open. Nothing is sent, nothing is opened "
                                                + "for you.\n\nOff switches the mod off entirely on those servers - "
                                                + "no key, no screen, nothing indexed - because without that index "
                                                + "there is nothing here for it to search. Your own world and "
                                                + "servers running the mod are unaffected.",
                                        () -> config.clientSideIndex, value -> config.clientSideIndex = value),
                                new Toggle("Scan world on join",
                                        "Reads the world off disk to find unvisited chunks.",
                                        "Reads the world off disk in the background when you join, so "
                                                + "containers in chunks you have never visited are found too. "
                                                + "Costs some throughput for a few seconds on a large world.",
                                        () -> config.scanOnWorldJoin, value -> config.scanOnWorldJoin = value),
                                new Link("Stored indexes",
                                        "What has been indexed, and how to throw one away.",
                                        "Every world and server this machine holds an index for, "
                                                + "how large each has got, and a button to delete "
                                                + "one. An index is a cache of somebody's world, so "
                                                + "deleting one costs a rescan and never the world "
                                                + "itself - worth doing when a world has been edited "
                                                + "outside the game, or for a server you played once "
                                                + "and will not return to.",
                                        () -> new IndexScreen(this)),
                                new Choice("LAN/server guests may search",
                                        "Who may search once your world is opened up.",
                                        "Who may search when your world is opened to LAN, or on a server "
                                                + "running the mod. The host is never gated in their own world, "
                                                + "so this does nothing in plain singleplayer - it starts "
                                                + "mattering the moment that world is opened up, which is exactly "
                                                + "when nobody thinks to look in a settings screen.",
                                        this::accessValue, this::cycleAccess))),

                new Section("Containers", "minecraft:hopper",
                        "Which containers count, and what counts as being inside one.",
                        List.of(
                                new Toggle("Count items inside shulker boxes",
                                        "Whether a shulker's contents count as in the chest.",
                                        "Whether a shulker box in a chest contributes its contents to that "
                                                + "chest, or only counts as a shulker box.",
                                        () -> config.includeNested, value -> config.includeNested = value),
                                new Toggle("Show machines by default",
                                        "Hoppers, droppers, dispensers, crafters.",
                                        "Hoppers, droppers, dispensers and crafters - containers that move "
                                                + "items about on their own. Always indexed; this is only whether "
                                                + "the search screen starts with them shown.",
                                        () -> config.showMachines, value -> config.showMachines = value),
                                new Toggle("Show furnaces & pots by default",
                                        "Furnaces, brewing stands, jukeboxes, pots, shelves.",
                                        "Furnaces, brewing stands, jukeboxes, lecterns, decorated pots and "
                                                + "chiseled bookshelves - things that hold items but are not "
                                                + "storage. A separate group from the machines, because a pot "
                                                + "holds exactly what you put in it while a hopper's contents "
                                                + "are in transit.",
                                        () -> config.showUtility, value -> config.showUtility = value),
                                new Toggle("Show minecarts & boats by default",
                                        "Chest minecarts, hopper minecarts, chest boats.",
                                        "Chest minecarts, hopper minecarts and chest boats. Read live at the "
                                                + "moment you search rather than indexed, because they move - so "
                                                + "they are found only where the world is loaded, and never "
                                                + "remembered between sessions.",
                                        () -> config.showEntities, value -> config.showEntities = value),
                                new Link("What counts as a machine",
                                        "The container types the machines toggle hides.",
                                        "Container types filtered by the machines toggle. Add a modded machine "
                                                + "here to have it filtered with the vanilla ones.",
                                        () -> new ServerListScreen(this,
                                                "Machines",
                                                "Container types filtered by the \"Machines\" toggle. Anything in neither\n"
                                                        + "group is ordinary storage and is always shown.",
                                                config.machineTypes,
                                                ServerListScreen::asRegistryId, "hopper")),
                                new Link("What counts as a functional block",
                                        "The container types the functional toggle hides.",
                                        "Container types filtered by the functional-blocks toggle: furnaces, "
                                                + "brewing stands, jukeboxes, lecterns, pots and shelves.",
                                        () -> new ServerListScreen(this,
                                                "Functional blocks",
                                                "Container types filtered by the \"Functional blocks\" toggle. Anything in\n"
                                                        + "neither group is ordinary storage and is always shown.",
                                                config.utilityTypes,
                                                ServerListScreen::asRegistryId, "jukebox")))),

                new Section("Searching", "minecraft:spyglass",
                        "The search screen, and the ways of reaching it.",
                        List.of(
                                new Range("Items shown",
                                        "How many distinct items the grid lists at once.",
                                        "How many distinct items the search screen will list at once. Past the "
                                                + "top of the range it reads unlimited rather than a number: "
                                                + "dragging it to the end means stop hiding things, not exactly "
                                                + "two thousand.",
                                        () -> config.maxResults <= 0 ? MAX_RESULTS_CEILING : config.maxResults,
                                        value -> config.maxResults = value >= MAX_RESULTS_CEILING
                                                ? ChestIndexConfig.UNLIMITED_RESULTS : value,
                                        0, MAX_RESULTS_CEILING, RESULTS_STEP,
                                        value -> "Items shown: " + (value >= MAX_RESULTS_CEILING
                                                ? "unlimited" : Integer.toString(value))),
                                new Choice("Item detail",
                                        "The panel describing the item under the cursor.",
                                        "Describes the item under the cursor in the search grid: how many "
                                                + "there are, in how many containers, how many are sealed inside "
                                                + "shulker boxes, and how far the nearest is. The title row "
                                                + "always carries the short version.",
                                        this::detailValue, this::cycleDetail),
                                new Toggle("Ender chest view",
                                        "Your ender chest as a view of its own.",
                                        "Offers your ender chest as a view of its own, beside the dimension "
                                                + "buttons. It lists only what is in there - never mixed with a "
                                                + "dimension - and appears only when it is not empty.",
                                        () -> config.enderChestView, value -> config.enderChestView = value),
                                new Toggle("Search button on containers",
                                        "A magnifier on chest and container windows.",
                                        "A small magnifier on chests and other container windows. Left-click "
                                                + "opens the search screen; right-drag moves it. Each kind of "
                                                + "window remembers its own place, so a hopper's button need not "
                                                + "sit where a chest's does.",
                                        () -> config.containerSearchButton,
                                        value -> config.containerSearchButton = value),
                                new Toggle("Shulker key searches contents",
                                        "The search key looks inside a held shulker box.",
                                        "Pointing the search key at a shulker box or bundle looks for "
                                                + "everything inside it rather than for more shulker boxes - "
                                                + "which is almost always the question. Off, it searches for the "
                                                + "box itself, empty ones included.",
                                        () -> config.searchShulkerContents,
                                        value -> config.searchShulkerContents = value),
                                new Toggle("Litematica material list buttons",
                                        "Search buttons on Litematica's material list.",
                                        "Adds search buttons to Litematica's material list: one on each row, "
                                                + "left of that row's Ignore, which finds that material; and one "
                                                + "beside Export, which finds everything the schematic still "
                                                + "needs. Only appears when Litematica is installed.",
                                        () -> config.litematicaButton,
                                        value -> config.litematicaButton = value))),

                new Section("Guidance", "minecraft:compass",
                        "How a container you have searched for is pointed out.",
                        List.of(
                                new Choice("Show found containers as",
                                        "Boxes in the world, text above the hotbar, or both.",
                                        "Boxes drawn around the containers in the world, a line of text above "
                                                + "the hotbar, both, or neither.",
                                        this::displayValue, this::cycleDisplay),
                                new Range("Guidance lasts",
                                        "How long the markers stay up after a search.",
                                        "How long the markers stay up after a search, if you have not walked "
                                                + "to one of them first.",
                                        () -> config.highlightSeconds,
                                        value -> config.highlightSeconds = value,
                                        5, HIGHLIGHT_MAX, 5, value -> "Guidance lasts: " + value + "s"),
                                new Range("Grace when walking away",
                                        "How long they survive once you head away.",
                                        "How long the markers survive once you are heading away from every "
                                                + "match, rather than being dropped the moment the distance "
                                                + "starts growing.",
                                        () -> config.highlightRecedingGraceSeconds,
                                        value -> config.highlightRecedingGraceSeconds = value,
                                        1, GRACE_MAX, 1, value -> "Grace when walking away: " + value + "s"))),

                new Section("Markers", "minecraft:spectral_arrow",
                        "What the markers look like. The picture changes as you do.",
                        List.of(
                                new Choice("Marker shape",
                                        "A full cube, just its outline, or both.",
                                        "What a marker in the world is drawn as. The full cube is found by "
                                                + "looking rather than by scanning, which is the job at any "
                                                + "distance; the outline is the more precise of the two and "
                                                + "leaves the container visible inside its own marker. A "
                                                + "double chest is one marker across both halves either way.",
                                        this::shapeValue, this::cycleShape),
                                new Choice("Markers arrive by",
                                        "How a marker appears when a search lands.",
                                        "How the markers appear when a search lands. A search closes the "
                                                + "screen and drops you back into the world, and markers that "
                                                + "are simply there the moment it closes read as part of the "
                                                + "scenery. Something that arrives is something the mod is "
                                                + "saying. Over in under half a second whichever you pick.",
                                        this::entryValue, this::cycleEntry),
                                new Range("Cube solidity",
                                        "How solid a filled marker is.",
                                        "How solid the full-cube marker is drawn. It has to be obvious from "
                                                + "across a base and still leave the container recognisable "
                                                + "close up, and those pull against each other - this is "
                                                + "where you settle it. No effect on the outline.",
                                        () -> config.highlightCubeOpacity,
                                        value -> config.highlightCubeOpacity = value,
                                        5, 100, 5, value -> "Cube solidity: " + value + "%"),
                                new Toggle("Boxes show through walls",
                                        "A marker you can only see in the open says little.",
                                        "Draws the boxes through whatever is in front of them, which is the "
                                                + "point of pointing at a container - it is behind something. "
                                                + "Turn it off if a shader pack or another rendering mod "
                                                + "disagrees with it; the boxes then hide behind walls as any "
                                                + "other line in the game does. The trail is always drawn "
                                                + "against the world rather than through it.",
                                        () -> config.highlightThroughWalls,
                                        value -> config.highlightThroughWalls = value),
                                new Toggle("Trail of marks above matches",
                                        "Works past render distance, where boxes cannot.",
                                        "Stands a column of marks on every distant match, running up to the "
                                                + "build limit. This is the part that still works past render "
                                                + "distance, where there is no terrain drawn to place a box "
                                                + "against. Unlike the boxes it is drawn behind the world "
                                                + "rather than through it, so it reads as standing somewhere "
                                                + "in the landscape rather than floating in front of it.",
                                        () -> config.guideBeam, value -> config.guideBeam = value),
                                new Range("Trails start at",
                                        "How far off a match must be to get a trail.",
                                        "How far away a match has to be before a trail is stood on it. A "
                                                + "container across the room does not need a column over it - "
                                                + "the box already says which one it is - and a base full of "
                                                + "them is a fence you cannot see the chests through. The "
                                                + "default is past most render distances, which is where the "
                                                + "box stops being enough.",
                                        () -> config.guideBeamFromChunks,
                                        value -> config.guideBeamFromChunks = value,
                                        0, BEAM_FROM_MAX, 1,
                                        value -> "Trails start at: " + value + " chunks"),
                                new Link("Highlight colours",
                                        "The two colours the world markers are drawn in.",
                                        "The colours the in-world markers are drawn in. The nearest match "
                                                + "holds the first one; every other match rests at the second "
                                                + "and swells through the first, which is what picks the "
                                                + "nearest out without labelling it.",
                                        () -> new HighlightColourScreen(this)),
                                new Choice("Marked slots",
                                        "How a matching slot is marked in an open container.",
                                        "How a slot holding what you searched for is marked, in whatever "
                                                + "container is open. The outline leaves the item and its stack "
                                                + "count fully visible; the background is easier to pick out "
                                                + "against a busy container texture, and is drawn as a tint so "
                                                + "the item still reads through it.",
                                        this::slotStyleValue, this::cycleSlotStyle)), true),

                new Section("Cheats", "minecraft:command_block",
                        "Things that cross the line. All off until you turn them on.",
                        List.of(
                                new Toggle("Enable cheat-like features",
                                        "The master switch over everything below.",
                                        "Nothing in this section does anything until this is on. Each setting "
                                                + "below keeps its own value while it is off, so you can switch "
                                                + "the lot off for one server and back on at home without "
                                                + "setting them all up again. Off by default, and deliberately: "
                                                + "whether these are a convenience or cheating depends on which "
                                                + "world you are in and what you agreed to there, which is not "
                                                + "something the mod can tell from the outside.",
                                        () -> config.cheatFeatures, value -> config.cheatFeatures = value),
                                new Toggle("Index generated chests",
                                        "Loot nobody has opened, including unvisited chunks.",
                                        "Puts unopened structure loot into the index, including in chunks "
                                                + "nobody has ever loaded, because the scanner reads the "
                                                + "world's files directly. This is x-ray in the plainest "
                                                + "sense. Off, that loot is neither recorded nor returned and "
                                                + "the index stays a map of what players put somewhere. A "
                                                + "generated chest someone has already opened is always kept "
                                                + "either way - remembering what you have seen is the whole "
                                                + "mod. Turning it off hides them at once; turning it on "
                                                + "fills them in on the next scan.",
                                        () -> config.indexGeneratedChests,
                                        value -> config.indexGeneratedChests = value),
                                new Toggle("Read minecarts on servers without the mod",
                                        "On a server they have moved since being seen.",
                                        "On a server these are moved by other players and by rails this "
                                                + "client is not simulating, so where one was found is not "
                                                + "where it is - and guidance that confidently walks you to the "
                                                + "wrong place is worse than not answering. In your own world "
                                                + "they are always read.",
                                        () -> config.entityContainersOnVanillaServers,
                                        value -> config.entityContainersOnVanillaServers = value),
                                new Toggle("Open a match in reach",
                                        "Opens a match instead of pointing at it.",
                                        "If a container you just searched for is within normal reach, open it "
                                                + "instead of pointing at it. Sends the same interaction as "
                                                + "right-clicking, so a server checks the distance as usual.",
                                        () -> config.openInReach, value -> config.openInReach = value),
                                new Toggle("Turn to face a match",
                                        "Turns your view towards the nearest match.",
                                        "Turns your view towards the nearest match when a search lands. Worth "
                                                + "knowing on a server you do not run: a client that moves the "
                                                + "view is the shape of thing some anti-cheats watch for.",
                                        () -> config.turnToTarget, value -> config.turnToTarget = value),
                                new Toggle("Assist on every server",
                                        "Off by default: both look like cheats to anti-cheat.",
                                        "Whether the two assist settings act on servers as well as in your "
                                                + "own world. Off by default: turning to face a match and "
                                                + "opening one in reach are the only things this mod does that a "
                                                + "server can see, and both look exactly like the cheats "
                                                + "anti-cheat is written to catch. Prefer naming the servers you "
                                                + "trust to switching this on everywhere.",
                                        () -> config.assistOnServers, value -> config.assistOnServers = value),
                                new Link("Servers assist is allowed on",
                                        "The named servers the two assist features act on.",
                                        "A named server you trust, rather than every server you ever join. "
                                                + "Turning to face a match and opening one in reach act on these "
                                                + "and nowhere else; your own world always allows both.",
                                        () -> new ServerListScreen(this,
                                                "Servers assist is allowed on",
                                                "Turning to face a match and opening one in reach act on these servers\n"
                                                        + "and nowhere else. Your own world always allows both.",
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
