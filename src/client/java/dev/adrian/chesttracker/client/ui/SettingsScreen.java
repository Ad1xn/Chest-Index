package dev.adrian.chesttracker.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

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

    /**
 * Settings, in the same window the mod's own screen uses.
 *
 * <p>The old settings screen was twenty-eight vanilla buttons in a scrolling
 * grid, each one labelled {@code "Something: on"}. It worked, and it was
 * unreadable: every row was the same shape and the same width, so finding one
 * setting meant reading all of them, and half the labels were a sentence
 * squeezed into two hundred pixels. Nothing about it said which settings
 * belonged together, and nothing said what any of them was for until you
 * hovered it.
 *
 * <p>So this is not a list of buttons. It is the chest window - vanilla's own
 * {@code generic_54} art, drawn by {@link Panel} at whatever size is needed -
 * with the settings sorted into five sections down the left-hand rail, each
 * marked by the item the game itself uses for that idea: a chest, a hopper, a
 * spyglass, a compass, a redstone torch. A section's settings are rows in a
 * sunken well, label on the left and current value on the right, so the states
 * read down a column without reading a single label.
 *
 * <h2>Why it is worth being a custom screen</h2>
 *
 * <p>Because the mod already has one. Opening the settings out of Mod Menu and
 * landing on a stock options page, then opening the tracker and landing on a
 * chest, is two mods' worth of furniture for one mod. Sharing the chrome also
 * means a resource pack that repaints containers repaints this, which a page
 * of vanilla buttons could never do.
 *
 * <h2>Everything is clickable</h2>
 *
 * <p>The whole row is the control, not a widget sitting on it: click a toggle's
 * row anywhere and it flips, click a choice's row and it advances, drag a
 * number's track. A row that opens another screen says so with a chevron. The
 * title row carries the hovered row's name and a tooltip carries the paragraph
 * that used to be crammed into the label - the one place on this window with
 * room for a sentence is the one place that explains itself.
 *
 * <h2>The filter</h2>
 *
 * <p>Twenty-eight settings in five sections is few enough to browse and too
 * many to hunt through, and somebody who already knows the word they want
 * should not have to guess which section it landed in. Typing in the strip
 * under the title searches labels <em>and</em> explanations across every
 * section at once, which is why "anti-cheat" finds the assist settings that
 * never say it in their labels.
 */
public final class SettingsScreen extends Screen {

    // --- geometry ----------------------------------------------------------

    /** A section button in the rail: square, item-sized plus its bevel. */
    private static final int RAIL_BUTTON = 20;
    private static final int RAIL_GAP = 2;

    /** Gap between the rail and the settings well. */
    private static final int RAIL_PAD = 4;

    private static final int ROW_H = 20;

    /** Width of the settings well, sized to the longest label plus a value. */
    private static final int CONTENT_W = 258;

    private static final int SCROLLBAR_W = 8;

    /**
     * How few rows the well may be cut down to on a short screen.
     *
     * <p>Six, because the rail is five buttons - a well shorter than the rail
     * beside it is not smaller, it is broken.
     */
    private static final int MIN_ROWS = 6;

    /** And how many it may grow to, so a tall screen does not get one long list. */
    private static final int MAX_ROWS = 10;

    private static final int BUTTON_SIZE = 12;

    /** Breathing room between the window's bands and the well between them. */
    private static final int PAD = 2;

    /** The toggle box on a row, matching the toolbar buttons on the other screen. */
    private static final int BOX = 12;

    private static final int TRACK_W = 72;
    private static final int TRACK_H = 6;
    private static final int THUMB_W = 6;
    private static final int THUMB_H = 10;

    /** The separator between parts of a title-row label. */
    private static final String SEP = "  -  ";

    /**
     * How wide a tooltip is allowed to grow before it wraps.
     *
     * <p>Narrower than it first was. At a GUI scale of four a two-hundred-and-
     * forty pixel tooltip is a third of the screen across, which put a single
     * unbroken line over the rows it was explaining.
     */
    private static final int TOOLTIP_W = 180;

    // --- settings the rows read and write ----------------------------------

    private static final int MAX_RESULTS_CEILING = 2000;
    private static final int RESULTS_STEP = 50;
    private static final int HIGHLIGHT_MAX = 300;
    private static final int GRACE_MAX = 120;

    /** Registry lookups are not free and the rail redraws every frame. */
    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

    private final Screen parent;
    private final ChestTrackerConfig config = ChestTrackerConfig.get();

    private int panelX;
    private int panelY;
    private int panelW;

    /**
     * How many rows the well is tall, fixed for the life of the screen.
     *
     * <p>Fixed, and sized to the largest section, because the two alternatives
     * are both worse. Sizing it to the screen leaves a five-row section sitting
     * in a well with room for eight. Sizing it to the <em>open</em> section -
     * which this briefly did - resizes the window under the hand of whoever is
     * clicking down the rail, and on a four-row section made the well shorter
     * than the rail beside it, so the last button hung out of the bottom of the
     * window over the footer.
     */
    private int visibleRows;

    private List<Section> sections = List.of();

    /** Which section is open. Survives a resize and a trip to a sub-screen. */
    private int section;

    private int scroll;

    /** What the filter strip holds. Empty means show the open section. */
    private String filter = "";

    private EditBox filterBox;

    /** The rows on screen: one section's, or everything the filter matches. */
    private List<Row> shown;

    /** Set while drawing, read when the title row is drawn. */
    private String hoverTitle;

    /**
     * The tooltip to draw, and the band of the screen it must stay clear of.
     *
     * <p>The band is the hovered row. A tooltip placed at the cursor sits on
     * top of it, and on a slider row that means sitting on top of the number
     * being dragged - so the tooltip goes above the row, or below it, but never
     * over it.
     */
    private record Hover(List<String> lines, int avoidTop, int avoidBottom) {}

    /** Set while drawing, drawn last so nothing covers it. */
    private Hover pendingTooltip;

    /** Which row's track the mouse is holding, or -1. */
    private int draggingRow = -1;

    private boolean draggingScrollbar;

    public SettingsScreen(Screen parent) {
        super(Component.literal("ChestTracker Settings"));
        this.parent = parent;
    }

    // --- the settings themselves -------------------------------------------

    /**
     * One row of a section.
     *
     * <p>Four shapes, because there are four kinds of answer a setting can
     * have, and a screen that drew them all as the same button is what this
     * replaces. Each carries how to read its current value and how to change
     * it, so the drawing and the clicking share one description of every
     * setting rather than agreeing by hand.
     */
    private sealed interface Row {
        String label();

        /**
         * One line, shown on hover.
         *
         * <p>Separate from {@link #help()} because a hover tooltip that carries
         * a whole paragraph is a panel, and a panel next to the cursor covers
         * the row it is describing - which on a row whose label holds the
         * number you are dragging means covering the number.
         */
        String hint();

        /** The paragraph, shown while shift is held. Also what the filter searches. */
        String help();
    }

    private record Toggle(String label, String hint, String help,
                          BooleanSupplier get, Consumer<Boolean> set) implements Row {}

    /** A setting with three or more named answers, advanced by clicking. */
    private record Choice(String label, String hint, String help,
                          Supplier<String> value, Runnable advance) implements Row {}

    private record Range(String label, String hint, String help,
                         IntSupplier get, IntConsumer set,
                         int min, int max, int step, IntFunction<String> format) implements Row {}

    /** A row that opens a screen of its own - a list to edit, or the colours. */
    private record Link(String label, String hint, String help,
                        Supplier<Screen> open) implements Row {}

    /**
     * @param icon the item the game itself uses for this idea, drawn in the rail
     * @param summary one line, shown under the window while the section is open
     */
    private record Section(String name, String icon, String summary, List<Row> rows) {}

    /**
     * Builds every section.
     *
     * <p>Rebuilt on each {@code init} rather than held, because the rows that
     * open a sub-screen have to hand it this screen as its parent, and after a
     * resize this screen is at a different place.
     */
    private List<Section> buildSections() {
        return List.of(
                new Section("General", "minecraft:chest",
                        "The mod as a whole, and where it is allowed to run.",
                        List.of(
                                new Toggle("ChestTracker",
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
                                                "Servers ChestTracker stays off on",
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
                                new Toggle("Read minecarts on servers without the mod",
                                        "Off: on a server they have moved since being seen.",
                                        "Off by default, and deliberately. On a server these are moved by "
                                                + "other players and by rails this client is not simulating, so "
                                                + "where one was found is not where it is - and guidance that "
                                                + "confidently walks you to the wrong place is worse than not "
                                                + "answering. In your own world they are always read.",
                                        () -> config.entityContainersOnVanillaServers,
                                        value -> config.entityContainersOnVanillaServers = value),
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
                                                ? ChestTrackerConfig.UNLIMITED_RESULTS : value,
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
                                        1, GRACE_MAX, 1, value -> "Grace when walking away: " + value + "s"),
                                new Toggle("Trail of marks above matches",
                                        "Works past render distance, where boxes cannot.",
                                        "Stands a column of fading marks on every match. This is the part "
                                                + "that still works past render distance, where there is no "
                                                + "terrain drawn to place a box against.",
                                        () -> config.guideBeam, value -> config.guideBeam = value),
                                new Link("Highlight colours",
                                        "The two colours the world markers are drawn in.",
                                        "The colours the in-world markers are drawn in - one for the nearest "
                                                + "match, one for the rest.",
                                        () -> new HighlightColourScreen(this)),
                                new Choice("Marked slots",
                                        "How a matching slot is marked in an open container.",
                                        "How a slot holding what you searched for is marked, in whatever "
                                                + "container is open. The outline leaves the item and its stack "
                                                + "count fully visible; the background is easier to pick out "
                                                + "against a busy container texture, and is drawn as a tint so "
                                                + "the item still reads through it.",
                                        this::slotStyleValue, this::cycleSlotStyle))),

                new Section("Assist", "minecraft:redstone_torch",
                        "The two things this mod can do on your behalf, and where.",
                        List.of(
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

    // --- the values the choice rows show -----------------------------------

    private String accessValue() {
        return switch (config.permissionTier()) {
            case ALL -> "everyone";
            case OWNED -> "own containers";
            case OP -> "operators";
        };
    }

    private void cycleAccess() {
        ChestTrackerConfig.Access[] tiers = ChestTrackerConfig.Access.values();
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
        ChestTrackerConfig.Detail[] modes = ChestTrackerConfig.Detail.values();
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
        ChestTrackerConfig.Display[] modes = ChestTrackerConfig.Display.values();
        config.highlightDisplay = modes[(config.highlightDisplay().ordinal() + 1) % modes.length].name();
    }

    private String slotStyleValue() {
        return config.slotHighlightStyle().label();
    }

    private void cycleSlotStyle() {
        ChestTrackerConfig.SlotStyle[] styles = ChestTrackerConfig.SlotStyle.values();
        config.slotHighlightStyle =
                styles[(config.slotHighlightStyle().ordinal() + 1) % styles.length].name();
    }

    // --- layout ------------------------------------------------------------

    @Override
    protected void init() {
        sections = buildSections();
        section = Math.max(0, Math.min(section, sections.size() - 1));

        // Thirty-two leaves the footer line its own room under the window, and
        // a margin above and below the lot.
        int room = height - 32 - CHROME_H;
        int fits = Math.max(railRows(), Math.min(MAX_ROWS, room / ROW_H));

        int tallest = 0;
        for (Section candidate : sections) tallest = Math.max(tallest, candidate.rows().size());
        // Never shorter than the rail, whatever the sections hold: a well the
        // rail hangs out of is not a small window, it is a broken one.
        visibleRows = Math.max(railRows(), Math.min(fits, tallest));

        panelW = Panel.EDGE_W + RAIL_BUTTON + RAIL_PAD + CONTENT_W + Panel.EDGE_W;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH()) / 2;

        // Sized and placed to sit inside the groove the strip draws for it,
        // rather than beside it - the same way the tracker screen's box does.
        filterBox = new EditBox(font, panelX + 11, panelY + Panel.TOP_H + 2,
                panelW - 22, 12, Component.literal("Filter settings"));
        filterBox.setBordered(false);
        filterBox.setMaxLength(48);
        filterBox.setHint(Component.literal("Filter settings"));
        filterBox.setValue(filter);
        filterBox.setResponder(value -> {
            filter = value;
            scroll = 0;
            shown = null;
        });
        addRenderableWidget(filterBox);
        // Deliberately not focused. A focused box hides its own hint, and that
        // hint is the only thing on the strip that says what it is for - a bare
        // cursor blinking in a groove teaches nobody that the settings can be
        // searched. One click to type is the cheaper half of that trade.

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

    /** The window's fixed parts, whatever the open section is. */
    private static final int CHROME_H =
            Panel.TOP_H + Panel.SEARCH_H + PAD + 2 + PAD + Panel.BOTTOM_H;

    /** Rows the well needs to be at least as tall as the rail standing beside it. */
    private int railRows() {
        int railH = sections.size() * RAIL_BUTTON + (sections.size() - 1) * RAIL_GAP;
        return (railH + ROW_H - 1) / ROW_H;
    }

    private int panelH() {
        return CHROME_H + visibleRows * ROW_H;
    }

    /** Whether there are more rows than the well can show at once. */
    private boolean overflows() {
        return rows().size() > visibleRows;
    }

    private int rowsX() {
        return contentX() + 1;
    }

    private int rowsW() {
        return contentW() - 2 - (overflows() ? SCROLLBAR_W + 1 : 0);
    }

    private int rowY(int index) {
        return contentTop() + 1 + index * ROW_H;
    }

    private int scrollbarX() {
        return contentX() + contentW() - 1 - SCROLLBAR_W;
    }

    private int maxScroll() {
        return Math.max(0, rows().size() - visibleRows);
    }

    private int closeX() {
        return panelX + panelW - 8 - BUTTON_SIZE;
    }

    /** How much room the title row has, once the close button has taken its share. */
    private int titleWidth() {
        return closeX() - (panelX + 8) - 4;
    }

    /**
     * The rows on screen.
     *
     * <p>Cached because this is asked several times a frame - to size the
     * scrollbar, to find the row under the cursor, and to draw - and the answer
     * changes only when the filter is typed in or the section is clicked. The
     * <em>values</em> in them are read live, so a cached list is still current.
     */
    private List<Row> rows() {
        if (shown == null) shown = buildShown();
        return shown;
    }

    /**
     * One section's rows, or - while the filter has something in it - every
     * row in every section that matches it.
     *
     * <p>Matched against the explanation as well as the label, which is the
     * whole point: the assist settings are the ones somebody worried about
     * anti-cheat wants, and neither of their labels contains the word.
     */
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

    /** How many rows there are altogether, for the footer's count. */
    private int totalSettings() {
        int total = 0;
        for (Section candidate : sections) total += candidate.rows().size();
        return total;
    }

    // --- drawing -----------------------------------------------------------

    private void draw(Gfx gfx, int mouseX, int mouseY) {
        hoverTitle = null;
        pendingTooltip = null;

        drawCloseButton(gfx, mouseX, mouseY);
        drawRail(gfx, mouseX, mouseY);
        Panel.well(gfx, contentX(), contentTop(), contentW(), contentH());
        drawRows(gfx, mouseX, mouseY);

        if (overflows()) {
            Panel.scrollbar(gfx, scrollbarX(), contentTop() + 1, SCROLLBAR_W, contentH() - 2,
                    scroll, maxScroll(), rows().size(), visibleRows);
        }

        // A tooltip while a track is being dragged is a panel jumping about
        // under the hand of somebody watching a number change.
        if (draggingRow >= 0 || draggingScrollbar) pendingTooltip = null;

        String title = hoverTitle != null ? hoverTitle : defaultTitle();
        gfx.text(font, Component.literal(truncate(title, titleWidth())),
                panelX + 8, panelY + 6, Panel.TEXT_MAIN);

        drawFooter(gfx);

        // Last, so nothing on the window is drawn over it.
        if (pendingTooltip != null) drawTooltip(gfx, pendingTooltip, mouseX);
    }

    /**
     * The title when nothing is hovered.
     *
     * <p>The section's name, prefixed while there is room for it. Filtering
     * replaces it with a count, because the section is not what is on screen
     * any more and saying it would be a lie.
     */
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

    /**
     * The section rail.
     *
     * <p>An item rather than a letter or a glyph, because the game has already
     * decided what a hopper looks like and nobody needs to learn a legend. The
     * open section's button is drawn in the on-green - the same green the
     * tracker screen uses for anything switched on - so the rail says where you
     * are without a label.
     *
     * <p>While the filter has something in it the rail is drawn muted: the rows
     * on screen come from every section at once, so no button is the open one
     * and pretending otherwise would be wrong. Clicking one still works, and
     * clears the filter.
     */
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
                // A version or a resource pack without that item still needs
                // something in the button, and the section's initial is the
                // one thing that is always available.
                String initial = candidate.name().substring(0, 1);
                gfx.text(font, initial, x + (RAIL_BUTTON - font.width(initial)) / 2, y + 6,
                        Panel.TEXT_MAIN);
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
        List<Row> visible = rows();
        if (visible.isEmpty()) {
            drawEmpty(gfx);
            return;
        }

        int left = rowsX();
        int right = left + rowsW();
        int hovered = rowAt(mouseX, mouseY);

        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= visible.size()) break;

            Row row = visible.get(index);
            int y = rowY(i);

            // A separating line rather than a box per row: twenty-eight boxes
            // is the list this replaces, and the well already frames them all.
            if (i > 0) gfx.fill(left + 2, y, right - 2, y + 1, 0x18000000);

            if (index == hovered) {
                gfx.fill(left, y + 1, right, y + ROW_H, 0x30FFFFFF);
                hoverTitle = row.label();
                pendingTooltip = new Hover(tooltipFor(row), y, y + ROW_H);
            }

            drawRow(gfx, row, left, right, y, index == hovered);
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
                // The number lives in the label rather than beside the track:
                // the track is already saying roughly where in the range it is,
                // and the exact figure is what the label is for.
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

    /**
     * What is drawn where the rows would be when there are none.
     *
     * <p>Only reachable by filtering, and it names the thing that was typed
     * rather than saying "no results": the answer somebody needs is that this
     * word is not in any setting, not that the screen is empty.
     */
    private void drawEmpty(Gfx gfx) {
        String message = "No setting mentions \"" + truncate(filter.trim(), 120) + "\".";
        gfx.text(font, Component.literal(message),
                contentX() + (contentW() - font.width(message)) / 2,
                contentTop() + contentH() / 2 - 4, Panel.TEXT_MUTED);
    }

    /**
     * A line under the window saying where you are.
     *
     * <p>The section summaries live here rather than on the rail, because a
     * five-word explanation of a section does not fit on a twenty-pixel button
     * and the alternative - only ever seeing it in a tooltip - means it is
     * invisible until you happen to hover.
     */
    private void drawFooter(Gfx gfx) {
        String footer = pendingTooltip != null && !shiftHeld()
                // Said here rather than in the tooltip, which is the thing the
                // shift is meant to keep small.
                ? "Hold shift for the full explanation."
                : filter.isBlank()
                ? sections.get(section).summary()
                : "Filtering every section. Escape clears it.";
        gfx.text(font, Component.literal(footer),
                panelX + (panelW - font.width(footer)) / 2, panelY + panelH() + 6,
                Panel.TEXT_MUTED);
    }

    /**
     * What a row's tooltip says: two lines, or the whole paragraph on shift.
     *
     * <p>Two lines by default because that is what a hover wants to be. The
     * paragraphs are worth having - several of these settings have a reason
     * behind them that the label cannot carry - but showing one on every hover
     * put a five-line panel over the rows on the way to reading anything, and
     * shift is how this mod already asks for the long answer elsewhere.
     *
     * <p>Paragraph breaks - a double newline - survive as blank lines, because
     * two of these settings genuinely have a second paragraph saying what
     * turning them off does.
     */
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
        if (minecraft == null || minecraft.getWindow() == null) return false;
        return InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /** Word-wraps to a pixel width, breaking a single over-long word if it must. */
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

    /**
     * Lines of text in a vanilla tooltip frame, placed beside the cursor.
     *
     * <p>First line in white, the rest in grey: that is how the game separates
     * a name from what is being said about it.
     */
    private void drawTooltip(Gfx gfx, Hover hover, int mouseX) {
        List<String> lines = hover.lines();
        int textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, font.width(line));
        int textHeight = lines.size() * 10 - 2;

        int textX = mouseX + 12 + textWidth + 4 > width ? mouseX - 16 - textWidth : mouseX + 12;
        textX = Math.max(8, textX);

        // Above the row if it fits there, below it otherwise. Six pixels of
        // clearance puts the frame's own three-pixel inset clear of the row
        // rather than flush against it.
        int textY = hover.avoidTop() - 6 - textHeight;
        if (textY < 8) textY = hover.avoidBottom() + 6;
        textY = Math.max(8, Math.min(textY, height - textHeight - 8));

        Panel.tooltipFrame(gfx, textX, textY, textWidth, textHeight);
        for (int i = 0; i < lines.size(); i++) {
            gfx.text(font, Component.literal(lines.get(i)),
                    textX, textY + i * 10, i == 0 ? Panel.TEXT_MAIN : Panel.TEXT_MUTED);
        }
    }

    /**
     * Trims text to a pixel width, with an ellipsis when it does not fit.
     *
     * <p>One pass rather than a measurement per character - this runs for every
     * row on every frame.
     */
    private String truncate(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    /**
     * The stack to draw for a section, or empty if there is not one to draw.
     *
     * <p>Empty has two quite different causes, and they are cached differently.
     * An id no registry knows is absent for good, so it is remembered. An item
     * whose components are not bound yet is a "not yet": 26.x binds them when a
     * world's registries load, and this screen opens from Mod Menu on the title
     * screen, where nothing is loaded and building the stack throws. Caching
     * that would leave the rail without icons for the rest of the session, long
     * after the items became available - so it is not remembered, and the next
     * frame in a world resolves it properly.
     *
     * <p>Caught rather than asked in advance because there is no way to ask that
     * both targets understand: 26.2 has {@code Holder.Reference#areComponentsBound},
     * 1.21.11 has no equivalent and no such problem. Callers already handle an
     * empty stack by drawing the section's initial instead.
     */
    private static ItemStack iconFor(String itemId) {
        ItemStack cached = ICON_CACHE.get(itemId);
        if (cached != null) return cached;

        Identifier identifier = Identifier.tryParse(itemId);
        Item item = identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
        if (item == null) {
            ICON_CACHE.put(itemId, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }

        try {
            ItemStack icon = new ItemStack(item);
            ICON_CACHE.put(itemId, icon);
            return icon;
        } catch (RuntimeException notBoundYet) {
            return ItemStack.EMPTY;
        }
    }

    // --- input -------------------------------------------------------------

    /** Which row the cursor is over, in the shown list's own numbering, or -1. */
    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < rowsX() || mouseX >= rowsX() + rowsW()) return -1;
        int top = contentTop() + 1;
        if (mouseY < top || mouseY >= top + visibleRows * ROW_H) return -1;
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
                && mouseY >= contentTop() && mouseY < contentTop() + contentH()) {
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

    /**
     * Opens a section.
     *
     * <p>Clearing the filter as well, because the rail is showing muted while
     * one is typed and a click on it that left the filtered rows on screen
     * would look like the button had not worked.
     */
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

    /**
     * Applies a click on a settings row.
     *
     * <p>The whole row is the control: a twelve-pixel box is a small thing to
     * hit and there is nothing else on the row that a click could have meant.
     * A number is the exception - its row is only live over the track, because
     * a click at the label end would have to mean some value and every choice
     * of which would be a guess.
     */
    private boolean clickRow(int mouseX, int mouseY) {
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
                // A couple of pixels of slack either side, so the thumb at
                // either end of the track is not half outside its own row.
                if (mouseX < trackX - 2 || mouseX >= trackX + TRACK_W + 2) return true;
                draggingRow = index;
                applyRange(range, mouseX, trackX);
            }
            case Link link -> {
                VanillaButton.playClick();
                // Saved on the way out rather than on the way back: the
                // sub-screens edit the config's own lists in place, and one of
                // them may be the last thing that happens before the game is
                // closed from the pause menu.
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
        double fraction = (mouseY - (contentTop() + 1)) / (double) (contentH() - 2);
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
        // The filter first, the window second. One press to get back to the
        // section you were in is worth more than one press to leave, and the
        // second press still leaves.
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && !filter.isBlank()) {
            filter = "";
            filterBox.setValue("");
            shown = null;
            scroll = 0;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        // Written on close rather than on every click, so a session of fiddling
        // produces one file write.
        config.save();
        minecraft.setScreenAndShow(parent);
    }

    // Two signatures the Gfx facade cannot hide: 26.x renamed both the render
    // entry point and the background hook. The window is drawn in the
    // background hook so the filter field renders on top of it rather than
    // under, and everything else afterwards so tooltips sit over the lot.
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
