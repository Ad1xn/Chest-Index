package dev.adrian.chesttracker.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.adrian.chesttracker.client.ClientTracker;
import dev.adrian.chesttracker.client.SearchCategories;
import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.ActionBar;
import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.core.util.BlockKey;
import dev.adrian.chesttracker.core.util.SearchQuery;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * A chest-shaped view of everything you own.
 *
 * <p>Drawn with vanilla's own {@code generic_54} container texture rather than
 * an imitation of it, so the window sits alongside a real chest GUI instead of
 * looking like a mod pasted over the game. Nine slots across and six rows down,
 * a search field, a scrollbar, and a row of filter buttons.
 *
 * <p>Nine across matters: one item per row means scrolling forever once a world
 * has a few hundred distinct items, whereas a grid turns that into a glance.
 *
 * <p>Two views share the window - the item grid, and the containers holding a
 * chosen item. Clicking a location closes the screen and hands over to guidance,
 * because the player is about to walk there.
 */
public final class ChestTrackerScreen extends Screen {

    private static final Identifier CONTAINER_TEXTURE =
            Identifier.parse("minecraft:textures/gui/container/generic_54.png");

    // Geometry of generic_54.png: a 176x222 window on a 256x256 sheet.
    private static final int SHEET = 256;
    private static final int GUI_W = 176;
    private static final int TOP_H = 17;          // top border and title row
    private static final int ROWS_V = 17;         // where the six slot rows start
    private static final int ROWS_H = 108;        // six rows of 18px
    private static final int BOTTOM_V = 215;      // bottom border in the sheet
    private static final int BOTTOM_H = 7;
    private static final int SLOTS_W = 169;       // left border plus nine slots
    private static final int SCROLL_COL = 14;     // widened for the scrollbar
    private static final int EDGE_W = 7;          // the window's side border
    private static final int FILLER_U = 100;      // a uniform column of the border bands
    private static final int SLOT_FILLER_U = 170; // flat panel between slots and edge

    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int SLOT = 18;
    private static final int SEARCH_H = 16;

    // Vanilla's container palette, for the parts drawn rather than blitted.
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int GROOVE = 0xFF8B8B8B;
    private static final int GROOVE_DARK = 0xFF373737;
    /**
     * Standard Minecraft text: white, with the drop shadow the font draws by
     * default.
     *
     * <p>Vanilla's container labels are dark grey and shadowless, and copying
     * that looked muddy here - the shadow is on by default in the draw call, so
     * dark grey text was being drawn with a dark outline behind it.
     *
     * <p>Alpha is spelled out. {@code 0xFFFFFF} is fully transparent in ARGB
     * and draws nothing at all; that bug has shipped here once already.
     */
    private static final int TEXT_MAIN = 0xFFFFFFFF;

    /** Standard secondary text, for a filter sitting at its default. */
    private static final int TEXT_MUTED = 0xFFAAAAAA;

    /** Icons drawn as shapes rather than glyphs need to contrast with the panel. */
    private static final int ICON = 0xFF404040;
    private static final int TEXT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_HOVER = 0x80FFFFFF;
    private static final int ROW_HOVER = 0x40000000;
    private static final int BUTTON_ON = 0xFF6A9A4A;

    /** Dark text, for the one place a label sits on a light button. */
    private static final int TEXT_DARK_ON_BUTTON = 0xFF202020;

    /**
     * The ender chest's glyph colour.
     *
     * <p>It shares its letter with the End - both are an E, and there is no
     * room on a twelve-pixel button for more - so the colour is what separates
     * them at a glance. Ender purple because that is the game's own colour for
     * the block, and the row is read far more often than it is hovered.
     */
    private static final int ENDER_GLYPH = 0xFF5A2D82;

    private static final int DIMENSION_W = 12;
    private static final int DIMENSION_H = 12;

    // Vanilla's tooltip palette, taken from its own renderer so a detail panel
    // sits beside a real item tooltip rather than next to one.
    private static final int TOOLTIP_BG = 0xF0100010;
    private static final int TOOLTIP_EDGE_TOP = 0x505000FF;
    private static final int TOOLTIP_EDGE_BOTTOM = 0x5028007F;

    private static final int BUTTON_SIZE = 12;
    private static final int TOOLBAR_BUTTONS = 2;   // menu, close
    private static final int MENU_W = 132;
    private static final int MENU_ROW_H = 12;

    /**
     * Floor between automatic refreshes.
     *
     * <p>The server already limits how often it reports a change, but a
     * singleplayer world reads the counter directly and has no such limit -
     * a hopper line would otherwise re-query every frame.
     */
    private static final long AUTO_REFRESH_MIN_MS = 400;

    /** How often the screen asks what the index holds. */
    private static final long STATUS_INTERVAL_MS = 1000;

    /** Height of the scanning bar drawn under the search field. */
    private static final int SCAN_BAR_H = 3;

    private static final int SCAN_BAR_BG = 0xFF373737;
    private static final int SCAN_BAR_FILL = 0xFF6A9A4A;

    /** How often a refused screen re-asks, in case permission changed. */
    private static final long REFUSED_RETRY_MS = 2000;

    private static final int MAX_CONTAINERS = 64;
    private static final int DETAIL_ROW = 11;

    /** Breathing room between the list well's bevel and its first row. */
    private static final int LIST_PAD = 3;

    /** Registry lookups are not free and slots redraw every frame. */
    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

    /** Translated item names, which are dearer still. See {@link #displayName}. */
    private static final Map<String, String> NAME_CACHE = new HashMap<>();

    private enum Sort {
        COUNT("Most"), NEAREST("Nearest"), NAME("A-Z");

        final String label;

        Sort(String label) {
            this.label = label;
        }

        static Sort parse(String name) {
            for (Sort value : values()) {
                if (value.name().equalsIgnoreCase(name)) return value;
            }
            return COUNT;
        }
    }

    private EditBox search;
    private String pending = ChestTrackerConfig.get().searchText;

    private List<QueryDto.ItemSummary> items = List.of();
    private List<QueryDto.ContainerHit> containers = List.of();
    private String selectedItemId;
    private int scrollRow;

    /**
     * First visible row of the container list, which scrolls separately.
     *
     * <p>Sharing the grid's offset would scroll one view by opening the other,
     * and the two count in different units - rows of nine against single rows.
     */
    private int detailScroll;

    private boolean draggingScrollbar;
    private boolean menuOpen;

    private ClientTracker.Availability availability = ClientTracker.Availability.NONE;

    /**
     * Ids of the newest replies accepted, per view.
     *
     * <p>Every keystroke starts a query and replies need not come back in the
     * order they were asked for. Ids only ever increase, so anything not newer
     * than what is already shown is a straggler and is dropped.
     */
    private int newestItemsReply;
    private int newestContainersReply;
    private int newestStatusReply;

    /**
     * Whether the detail pane is still waiting.
     *
     * <p>An empty list is a real answer - the filters excluded everything, or a
     * remote query timed out - so it cannot be told apart from "not back yet"
     * without saying so. Before this, both showed "Looking..." and one of them
     * never stopped.
     */
    private boolean containersPending;

    /**
     * The index generation this screen last drew, and when it last re-asked.
     *
     * <p>Comparing a token beats being called back: a change that arrives while
     * no screen is open costs nothing, and a closed screen cannot leave a
     * listener behind.
     */
    private long lastChangeToken;
    private long lastAutoRefresh;

    /** A change arrived while the detail pane was open, so the grid is behind. */
    private boolean itemsStale;

    // Every filter picks up where it was left. Re-choosing them on each open
    // is the kind of friction that makes a tool feel unfinished.
    private Sort sort = Sort.parse(ChestTrackerConfig.get().sortMode);
    private boolean includeNested = ChestTrackerConfig.get().includeNested;
    private boolean includeMachines = ChestTrackerConfig.get().showMachines;
    private boolean includeUtility = ChestTrackerConfig.get().showUtility;
    private boolean includeEntities = ChestTrackerConfig.get().showEntities;
    private int originIndex = Math.max(0, Math.min(2, ChestTrackerConfig.get().originFilter));

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private String hoverLabel;

    /**
     * The item whose detail panel is to be drawn this frame, or null.
     *
     * <p>A panel is a tooltip: it belongs over everything else on the window,
     * and the only way to guarantee that is to draw it after everything else.
     */
    private QueryDto.ItemSummary pendingTooltip;

    /**
     * Lines of plain help to draw beside the cursor this frame, or null.
     *
     * <p>The filter rows' explanations used to go to the title row, which is
     * {@code titleWidth()} wide - about a hundred and forty pixels - while
     * "Machines" plus "hoppers, droppers, dispensers, crafters" is closer to
     * two hundred and fifty. {@link #setHoverLabel} drops optional parts until
     * what is left fits, so the explanation was dropped every single time and
     * the row was labelled with the word it already showed. It could never
     * have fitted there, so it is drawn where a game explains a control: in a
     * tooltip, next to the thing being pointed at.
     */
    private List<String> pendingHint;

    /**
     * What the index holds, refreshed while the screen is open.
     *
     * <p>Drives two things a player cannot otherwise know: that a scan is
     * still running - "nothing here" and "nothing here yet" being very
     * different answers - and which other dimensions have anything worth
     * looking at.
     */
    private QueryDto.StatusResponse status = QueryDto.StatusResponse.empty(0);

    private long lastStatusAsk;

    /**
     * Which dimension is being shown; blank means the one the player is in.
     *
     * <p>Restored from the settings, so reopening the screen comes back to the
     * view it was left on. Validated against what the index actually holds when
     * the first status arrives - a remembered Nether is worse than useless once
     * there is nothing in the Nether.
     */
    /**
     * Which dimension the grid is reading, or blank for "wherever I am".
     *
     * <p>Static rather than per-screen so it survives closing and reopening the
     * window, and deliberately <em>not</em> saved to disk. A pin is about the
     * question being asked right now, and a pin that outlives the session it
     * was set in is a trap: pinning the Nether in one world and joining a
     * server a day later gave an empty grid that looked exactly like a broken
     * mod - and the one place a pin is invisible is the moment you have
     * forgotten setting it. Cleared outright when the world changes; see
     * {@link #forgetPinnedDimension()}.
     */
    private static String viewing = "";

    /** Whether the dimension row is showing all of its buttons. */
    private boolean dimensionsExpanded;

    /**
     * What the search box is offering to put in itself: the list of categories
     * when nothing is being typed, and the matching values once one is.
     *
     * <p>A search language nobody is told about is a search language nobody
     * uses. The prefixes are short enough to type but there is no way to guess
     * that they exist, or what values they take - so clicking into the box
     * shows the questions it can answer, and typing one narrows the list to
     * the answers.
     */
    private List<String> suggestions = List.of();

    /** Which suggestion Tab would take; moved with the arrow keys. */
    private int suggestionIndex;

    /**
     * What the box looked like last frame, so the list can follow the cursor.
     *
     * <p>The text responder covers typing, but not clicking into the box or
     * moving the cursor with the arrows - and both change which word is being
     * completed. Watching two integers a frame is cheaper than any event that
     * would tell us.
     */
    private boolean searchWasFocused;
    private int searchCursorWas = -1;

    /** One row of the list under the search box. */
    private static final int SUGGESTION_H = 11;

    /** Clear space between a row's label and its explanation. */
    private static final int HELP_GAP = 6;

    /** Below this there is no room for an explanation worth reading. */
    private static final int MIN_HELP_WIDTH = 24;

    /** How many rows of it are on screen at once; the rest are scrolled to. */
    private static final int SUGGESTIONS_VISIBLE = 6;

    /** First visible row, so a long list - every item tag - can be read through. */
    private int suggestionScroll;

    /**
     * Whether the player has asked for the list of categories.
     *
     * <p>It used to appear the moment the screen opened, because the search box
     * takes focus then - so the first thing anybody saw was a list covering the
     * top row of their own items. The categories are an answer to "what can I
     * type here", which is a question somebody asks by clicking the box; until
     * then the only list shown is the one completing a category they have
     * already started typing.
     */
    private boolean categoriesAsked;

    public ChestTrackerScreen() {
        super(Component.literal("Chest Tracker"));
    }

    @Override
    protected void init() {
        // Minecraft lays a screen out again on every resize - a window drag, F11
        // or a GUI-scale change - on the same instance. Everything below has to
        // know the difference between the screen opening and the screen simply
        // being measured again, or a resize answers a question the player never
        // asked: the grid jumped to the top, whatever they had open closed, and
        // the search box came back fully selected so the next key wiped it.
        boolean reopening = search != null;
        int cursor = reopening ? search.getCursorPosition() : -1;

        panelW = GUI_W + SCROLL_COL;
        panelH = TOP_H + SEARCH_H + ROWS_H + BOTTOM_H;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        // Sized and placed to sit *inside* the groove the window draws for it,
        // rather than beside it. It used to be a 159px field in a 176px groove
        // with its own black border drawn over the top, which read as a text
        // box that had come loose from its slot.
        search = new EditBox(font, panelX + 11, panelY + TOP_H + 2, panelW - 22, 12,
                Component.literal("Search"));
        // The groove is the border. Vanilla's own is a black fill and a grey
        // outline, and two frames around one field is one too many.
        search.setBordered(false);
        search.setMaxLength(64);
        search.setHint(Component.literal("Search"));
        // Set before the responder, so restoring the last search does not count
        // as the player typing it again.
        search.setValue(pending);
        if (reopening) {
            // Put the caret back where it was. A resize is not a new question.
            int at = Math.min(cursor, pending.length());
            search.setCursorPosition(at);
            search.setHighlightPos(at);
        } else {
            // Selected, so the next thing typed replaces the last search rather
            // than being appended to it. Opening the screen almost always means
            // asking a new question, and clearing the box by hand first is a
            // step nobody wants - while the text still being there is what
            // makes repeating the same search one keystroke.
            search.moveCursorToEnd(false);
            search.setHighlightPos(0);
        }
        search.setResponder(value -> {
            pending = value;
            refreshSuggestions();
            refreshItems();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        // Anything measured in pixels was measured with the font this screen
        // had when it was last laid out. init() runs again on a resize and on a
        // resource reload, and a reload can bring a different font with it - so
        // the rows and labels held from before are re-measured rather than
        // trusted, or a pack change leaves the list's right-hand column
        // aligned to a font that is no longer on screen.
        detailRows = null;
        menuRowsChanged();

        availability = ClientTracker.availability();
        lastChangeToken = ClientTracker.changeToken();
        // Ask the server to push changes only while this is on screen.
        ClientTracker.setWatching(true);
        // A resize keeps the scroll position and the open pane; only opening
        // the screen starts at the top of a closed one.
        refreshItems(!reopening);
    }

    @Override
    public void removed() {
        ClientTracker.setWatching(false);
        rememberPreferences();
        super.removed();
    }

    /**
     * Writes the filters and the search box back to the settings.
     *
     * <p>Only when something actually moved, so closing the screen does not
     * rewrite the config file every time it is opened and shut.
     */
    private void rememberPreferences() {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        boolean changed = config.includeNested != includeNested
                || config.showMachines != includeMachines
                || config.showUtility != includeUtility
                || config.showEntities != includeEntities
                || config.originFilter != originIndex
                || !config.sortMode.equals(sort.name())
                || !config.searchText.equals(pending);
        if (!changed) return;

        config.includeNested = includeNested;
        config.showMachines = includeMachines;
        config.showUtility = includeUtility;
        config.showEntities = includeEntities;
        config.originFilter = originIndex;
        config.sortMode = sort.name();
        config.searchText = pending;
        config.save();
    }

    // --- data --------------------------------------------------------------

    private QueryDto.Filters filters() {
        return new QueryDto.Filters(includeNested, includeMachines, includeUtility,
                includeEntities, originIndex);
    }

    /** A refresh the player asked for: back to the grid, scrolled to the top. */
    private void refreshItems() {
        refreshItems(true);
    }

    /**
     * @param resetView true when the player changed the search or the filters,
     *                  false for a background refresh - which must leave the
     *                  scroll position and the open pane alone, or the grid
     *                  jumps under the cursor every time a hopper moves an item
     */
    /**
     * Asks what the index holds, about once a second while open.
     *
     * <p>Polled rather than pushed: it is only interesting while this screen is
     * up, and a push would need a second subscription for something a player
     * reads a handful of times.
     */
    private void pollStatus() {
        long now = System.currentTimeMillis();
        if (now - lastStatusAsk < STATUS_INTERVAL_MS || !mayAttempt()) return;
        lastStatusAsk = now;
        ClientTracker.status().thenAccept(response -> minecraft.execute(() -> {
            // As with the other two replies: a slow earlier answer must not
            // overwrite a newer one. Left unguarded this walked the scan bar
            // backwards - and worse, an old answer that predates a dimension
            // filling up unpins the view the player chose, below.
            if (response.requestId() <= newestStatusReply) return;
            newestStatusReply = response.requestId();
            status = response;
            // A dimension can empty out while being looked at - it is somebody
            // else's world too. Falling back to the player's own keeps the
            // screen showing something rather than an empty grid with a
            // selected button.
            if (!viewing.isEmpty() && shownDimensions().stream()
                    .noneMatch(entry -> entry.dimensionId().equals(viewing))) {
                viewing = "";
                refreshItems();
            }
        }));
    }

    private void refreshItems(boolean resetView) {
        if (!mayAttempt()) return;
        ClientTracker.summarise(pending, filters(), ChestTrackerConfig.get().resultLimit(), viewing)
                .thenAccept(response ->
                minecraft.execute(() -> {
                    // A slow earlier query must not overwrite a newer one's results.
                    if (response.requestId() <= newestItemsReply) return;
                    newestItemsReply = response.requestId();
                    backgroundAnswered();
                    items = sorted(withoutOtherTabs(response.items()));
                    itemsStale = false;
                    if (!resetView) {
                        // The list can shrink, so a kept scroll can end up past
                        // the end of it.
                        scrollRow = Math.min(scrollRow, maxScrollRow());
                        return;
                    }
                    scrollRow = 0;
                    back();
                }));
    }

    /**
     * Re-asks where the selected item is, without clearing what is on screen.
     *
     * <p>Used for background refreshes, so a live update does not blink the
     * pane through "Looking..." on every change.
     */
    private void refreshContainers() {
        String itemId = selectedItemId;
        if (itemId == null || !mayAttempt()) return;
        ClientTracker.containers(List.of(itemId), filters(), MAX_CONTAINERS, viewing, pending)
                .thenAccept(response -> minecraft.execute(() -> {
                    if (!itemId.equals(selectedItemId)) return;
                    if (response.requestId() <= newestContainersReply) return;
                    newestContainersReply = response.requestId();
                    backgroundAnswered();
                    containers = response.hits();
                    containersPending = false;
                    // A background refresh can shorten the list under a kept
                    // scroll position - a container was emptied or broken.
                    detailScroll = Math.min(detailScroll, maxDetailScroll());
                }));
    }

    /**
     * Keeps asking while refused, so a permission change lands on its own.
     *
     * <p>Much slower than a normal refresh: nothing is being shown, and the
     * only thing that can change is an answer the player is waiting on rather
     * than watching.
     */
    private void retryWhileRefused() {
        if (availability != ClientTracker.Availability.NOT_PERMITTED) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoRefresh < REFUSED_RETRY_MS) return;
        lastAutoRefresh = now;
        refreshItems(false);
    }

    /**
     * Re-asks whichever view is open, when the index has moved under it.
     *
     * <p>Throttled, and only when the token actually changed - the token is not
     * consumed while throttled, so a change during the quiet period is picked
     * up as soon as it ends rather than lost.
     */
    private void pollForChanges() {
        long token = ClientTracker.changeToken();
        if (token == lastChangeToken) return;

        long now = System.currentTimeMillis();
        if (now - lastAutoRefresh < backgroundInterval()) return;
        lastChangeToken = token;
        lastAutoRefresh = now;
        backgroundStartedAt = now;

        if (selectedItemId == null) {
            refreshItems(false);
        } else {
            // Refreshing the grid as well would double every update's cost for
            // a pane the player cannot see. It is marked instead, and caught up
            // when they go back to it.
            refreshContainers();
            itemsStale = true;
        }
    }

    /**
     * How long to leave between background refreshes, given what the last one
     * cost.
     *
     * <p>A fixed rate is the wrong shape here. Answering costs a walk of every
     * record and every stack in the dimension whenever the index has moved -
     * and the index moves on every chunk that loads, so walking around with
     * this screen open re-ran that walk two and a half times a second. On a
     * small world it is free and the fixed rate was right; on a large one it
     * was most of a frame, repeatedly.
     *
     * <p>So the interval follows the measured cost: eight times however long
     * the last answer took, floored at the fixed rate and capped so the view
     * never goes properly stale. A cheap world keeps the old cadence exactly;
     * an expensive one settles at a few per cent of a frame either way, which
     * is the number that actually matters.
     */
    private long backgroundInterval() {
        long scaled = lastQueryMs * BACKGROUND_COST_MULTIPLE;
        return Math.max(AUTO_REFRESH_MIN_MS, Math.min(AUTO_REFRESH_MAX_MS, scaled));
    }

    /** How much of the interval an answer is allowed to be; see {@link #backgroundInterval}. */
    private static final int BACKGROUND_COST_MULTIPLE = 8;

    /** However slow answering gets, the screen still catches up this often. */
    private static final long AUTO_REFRESH_MAX_MS = 3000;

    /** When the running background refresh was asked for, or 0. */
    private long backgroundStartedAt;

    /** How long the last background refresh took to come back. */
    private long lastQueryMs;

    /** Notes how long a background refresh took, to pace the next one. */
    private void backgroundAnswered() {
        if (backgroundStartedAt == 0L) return;
        lastQueryMs = Math.max(0L, System.currentTimeMillis() - backgroundStartedAt);
        backgroundStartedAt = 0L;
    }

    /** Whether there is anything to show right now. */
    private boolean canQuery() {
        return availability == ClientTracker.Availability.LOCAL
                || availability == ClientTracker.Availability.SERVER
                || availability == ClientTracker.Availability.CLIENT_ONLY;
    }

    /**
     * Whether it is worth asking at all.
     *
     * <p>Deliberately wider than {@link #canQuery()}: a refused player keeps
     * asking, because the reply is what carries the current answer. Being opped
     * mid-session would otherwise never be noticed - the screen would refuse to
     * ask precisely because of the stale answer it was trying to replace.
     */
    private boolean mayAttempt() {
        return canQuery() || availability == ClientTracker.Availability.NOT_PERMITTED;
    }

    /**
     * Applies the part of the search only this side can answer.
     *
     * <p>Which creative tab an item is filed under is assembled by the client
     * from its own registries, so a server - which may not have built its tabs
     * at all - cannot answer {@code tab:}. Everything else in the search was
     * applied where the index is.
     */
    private List<QueryDto.ItemSummary> withoutOtherTabs(List<QueryDto.ItemSummary> source) {
        SearchQuery query = SearchQuery.parse(pending);
        if (query.valuesOf(SearchQuery.Category.TAB).isEmpty()) return source;

        List<QueryDto.ItemSummary> kept = new ArrayList<>(source.size());
        for (QueryDto.ItemSummary item : source) {
            if (SearchCategories.allows(query, item.itemId())) kept.add(item);
        }
        return kept;
    }

    private List<QueryDto.ItemSummary> sorted(List<QueryDto.ItemSummary> source) {
        List<QueryDto.ItemSummary> copy = new ArrayList<>(source);
        switch (sort) {
            case NEAREST -> copy.sort(Comparator.comparingDouble(QueryDto.ItemSummary::nearestDistSq));
            case NAME -> copy.sort(Comparator.comparing(entry -> displayName(entry.itemId())));
            case COUNT -> { /* summarise already returns most-plentiful first */ }
        }
        return copy;
    }

    /**
     * Closes the screen and outlines every container holding the item.
     *
     * <p>Closes first and highlights when the answer arrives, rather than
     * waiting: the player has said where they want to go, and holding the
     * window open over the world while a server replies reads as a stall.
     */
    private void highlightItem(String itemId) {
        if (!mayAttempt()) return;
        String label = displayName(itemId);

        if (QueryDto.ENDER_CHEST.equals(viewing)) {
            markInEnderChest(itemId, label);
            return;
        }
        // The dimension the results are from, which is not always the one the
        // player is standing in. The highlight compares the two and draws
        // nothing when they differ - which is right, because a box around a
        // Nether chest means nothing while stood in the overworld.
        String dimensionId = viewing.isEmpty()
                ? minecraft.player.level().dimension().identifier().toString()
                : viewing;

        ClientTracker.containers(List.of(itemId), filters(), MAX_CONTAINERS, viewing, pending)
                .thenAccept(response ->
                minecraft.execute(() -> {
                    if (response.hits().isEmpty()) {
                        ActionBar.say(
                                Component.literal("Nothing indexed holds " + label));
                        return;
                    }
                    // Already nearest-first: the server ranks by distance, and
                    // the highlight takes the first as the one to guide to.
                    ContainerHighlight.get().selectHits(response.hits(), dimensionId, label);
                    ContainerHighlight.get().searchingFor(itemId);
                }));
        onClose();
    }

    /**
     * Answers "where in the ender chest is it" the only way that means
     * anything: by marking it once the chest is open.
     *
     * <p>An ender chest has no location - it is the same six rows wherever the
     * player stands - so the ordinary search finds nowhere to walk and used to
     * report "Nothing indexed holds Diamond", contradicting the grid that had
     * just counted them. What the player actually wants to know is which of the
     * shulker boxes in there it is in, and {@code SlotHighlight} already
     * answers that: it marks the item where it is loose, and marks the shulker
     * box in a second colour where it is sealed inside one.
     */
    private void markInEnderChest(String itemId, String label) {
        // The ender chests within reach of the loaded world, so the next step
        // is pointed at rather than described. Empty is a real answer - there
        // is not one nearby - and the message says so instead of pretending.
        java.util.List<Long> chests = dev.adrian.chesttracker.client.EnderChests.nearby();
        String dimensionId = minecraft.player == null ? null
                : minecraft.player.level().dimension().identifier().toString();

        ContainerHighlight.get().markCarried(itemId, label, chests, dimensionId);
        ActionBar.say(Component.literal(chests.isEmpty()
                ? "Open your ender chest - " + label + " will be marked"
                : "Nearest ender chest marked - " + label + " is inside"));
        onClose();
    }

    private void selectItem(String itemId) {
        // A list of places is meaningless for the ender chest, so a right-click
        // there does what a left-click does rather than opening an empty pane.
        if (QueryDto.ENDER_CHEST.equals(viewing)) {
            highlightItem(itemId);
            return;
        }
        selectedItemId = itemId;
        containers = List.of();
        containersPending = true;
        detailScroll = 0;
        refreshContainers();
    }

    private void back() {
        selectedItemId = null;
        containers = List.of();
        containersPending = false;
    }

    // --- geometry ----------------------------------------------------------

    private int gridX() {
        return panelX + 8;
    }

    private int gridY() {
        return panelY + TOP_H + SEARCH_H + 1;
    }

    private int scrollbarX() {
        return panelX + SLOTS_W + 3;
    }

    private int gridRows() {
        return (items.size() + COLS - 1) / COLS;
    }

    private int maxScrollRow() {
        return Math.max(0, gridRows() - ROWS);
    }

    // The container list occupies the rectangle the slot art is blitted into,
    // taken from where that art actually starts rather than from the grid's
    // inner origin, so the list's bevel lands on the window's own border.

    private int listLeft() {
        return panelX + EDGE_W;
    }

    private int listTop() {
        return panelY + TOP_H + SEARCH_H;
    }

    private int listWidth() {
        return COLS * SLOT;
    }

    private int listHeight() {
        return ROWS * SLOT;
    }

    /** Rows that fit inside the well. Anything past this has to be scrolled to. */
    private int listRowsVisible() {
        return (listHeight() - LIST_PAD * 2) / DETAIL_ROW;
    }

    private int maxDetailScroll() {
        return Math.max(0, containers.size() - listRowsVisible());
    }

    private int listRowY(int row) {
        return listTop() + LIST_PAD + row * DETAIL_ROW;
    }

    /**
     * The container under the cursor, or -1.
     *
     * <p>One method for both the highlight and the click, so what lights up is
     * always what gets selected. Deciding it twice is how a click lands on the
     * row above the one being pointed at - and the old click test bounded
     * neither the top of the pane nor its right edge, so the empty strip above
     * the list guided the player to its first row.
     */
    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < listLeft() || mouseX >= listLeft() + listWidth()) return -1;
        int offset = mouseY - listRowY(0);
        if (offset < 0) return -1;
        int row = offset / DETAIL_ROW;
        if (row >= listRowsVisible()) return -1;
        int index = detailScroll + row;
        return index < containers.size() ? index : -1;
    }

    private int slotAt(int mouseX, int mouseY) {
        int col = (mouseX - gridX()) / SLOT;
        int row = (mouseY - gridY()) / SLOT;
        if (mouseX < gridX() || mouseY < gridY() || col < 0 || col >= COLS || row < 0 || row >= ROWS) return -1;
        int index = (scrollRow + row) * COLS + col;
        return index < items.size() ? index : -1;
    }

    // --- drawing -----------------------------------------------------------

    /**
     * Everything drawn on top of the widgets.
     *
     * <p>The window itself is deliberately not drawn here. Widgets render
     * between the background and this, so painting the panel at this point put
     * it over the search field and hid whatever was being typed.
     */
    private void draw(Gfx gfx, int mouseX, int mouseY) {
        followSearchCursor();
        hoverLabel = null;
        pendingTooltip = null;
        pendingHint = null;
        drawButtons(gfx, mouseX, mouseY);

        // The server announces itself shortly after joining, so a screen opened
        // during that window has to notice when the answer arrives.
        ClientTracker.Availability now = ClientTracker.availability();
        if (now != availability) {
            availability = now;
            // A server that has just announced itself may not have been asked
            // to push yet.
            ClientTracker.setWatching(true);
            lastChangeToken = ClientTracker.changeToken();
            refreshItems();
        } else {
            pollForChanges();
            retryWhileRefused();
        }

        if (!canQuery()) {
            drawMessage(gfx, unavailableMessage());
            return;
        }

        pollStatus();
        drawScanBar(gfx);
        drawDimensions(gfx, mouseX, mouseY);

        if (selectedItemId == null) {
            drawGrid(gfx, mouseX, mouseY);
            drawScrollbar(gfx, scrollRow, maxScrollRow(), gridRows(), ROWS);
        } else {
            drawLocations(gfx, mouseX, mouseY);
            drawScrollbar(gfx, detailScroll, maxDetailScroll(),
                    containers.size(), listRowsVisible());
        }

        // Worked out before the title is drawn, even though the menu itself is
        // drawn after it: the dropdown has to sit over the grid, but the title
        // is where its explanation goes.
        labelMenuRow(mouseX, mouseY);

        String title = hoverLabel != null ? hoverLabel
                : selectedItemId != null ? displayName(selectedItemId) : "Chest Tracker";
        // Hover labels have already been fitted by dropping their optional
        // parts; this is the backstop for the plain titles, which are a single
        // name with nothing to drop.
        gfx.text(font, Component.literal(truncate(title, titleWidth())),
                panelX + 8, panelY + 6, TEXT_MAIN);

        // Last, so nothing on the window is drawn over it - see
        // {@link #pendingTooltip}. Before the menu only because an open
        // dropdown is a thing the player is currently pointing at.
        if (pendingTooltip != null) drawItemTooltip(gfx, pendingTooltip, mouseX, mouseY);

        drawMenu(gfx, mouseX, mouseY);
        drawSuggestions(gfx, mouseX, mouseY);

        // After the dropdown, because it explains a row of it.
        if (pendingHint != null) drawTextTooltip(gfx, pendingHint, mouseX, mouseY);
    }

    /**
     * A bar under the search field while the world is being read off disk.
     *
     * <p>Worth the three pixels. Without it an empty grid on a fresh world is
     * indistinguishable from a broken mod, and the honest answer - it is still
     * reading - is the one thing that stops somebody going looking for a bug.
     */
    private void drawScanBar(Gfx gfx) {
        if (!status.scanning()) return;

        // Spans the groove the search field sits in, not the nine slot columns
        // below it. Measured from SLOTS_W it stopped short of the right-hand
        // edge by the width of the scrollbar column the window is widened by,
        // which read as a bar that had finished when it had not.
        int x = panelX + 8;
        int width = panelW - 16;
        int y = panelY + TOP_H + SEARCH_H - SCAN_BAR_H - 1;

        gfx.fill(x, y, x + width, y + SCAN_BAR_H, SCAN_BAR_BG);
        float fraction = status.progress();
        if (fraction > 0) {
            gfx.fill(x, y, x + (int) (width * fraction), y + SCAN_BAR_H, SCAN_BAR_FILL);
        }
        if (hoverLabel == null) {
            setHoverLabel("Indexing the world...", fraction > 0
                    ? String.format("%d%%", Math.round(fraction * 100))
                    : String.format("%,d chunks", status.chunksRead()));
        }
    }

    /**
     * A button per dimension that has anything in it.
     *
     * <p>Only dimensions the index knows about, because a button for an empty
     * Nether is a button that answers nothing. They sit along the bottom edge,
     * which is otherwise border.
     */
    private void drawDimensions(Gfx gfx, int mouseX, int mouseY) {
        List<QueryDto.DimensionSummary> dimensions = shownDimensions();
        // One view is not a choice, and a button that cannot be pressed to any
        // effect is a button worth not drawing.
        if (dimensions.size() < 2) return;

        int y = dimensionRowY();
        int selected = selectedDimensionIndex(dimensions);

        // Expanded while pointed at, collapsed otherwise. The row used to be
        // permanently four buttons wide along the bottom border, which is four
        // things to read every time the screen is opened to answer a question
        // that is usually about the dimension you are standing in. Collapsed it
        // says which view you are looking at; hovered it offers the others.
        boolean over = mouseY >= y && mouseY < y + DIMENSION_H
                && mouseX >= dimensionX(0)
                && mouseX < dimensionX(dimensionsExpanded ? dimensions.size() - 1 : 0) + DIMENSION_W;
        dimensionsExpanded = dimensions.size() > 1 && over;

        if (!dimensionsExpanded) {
            drawDimensionButton(gfx, dimensions.get(selected), dimensionX(0), y, true,
                    over, dimensions.size() > 1);
            return;
        }
        for (int i = 0; i < dimensions.size(); i++) {
            int x = dimensionX(i);
            boolean hovered = mouseX >= x && mouseX < x + DIMENSION_W;
            drawDimensionButton(gfx, dimensions.get(i), x, y, i == selected, hovered, false);
        }
    }

    /** One dimension button, selected or not, hovered or not. */
    private void drawDimensionButton(Gfx gfx, QueryDto.DimensionSummary dimension,
                                     int x, int y, boolean selected, boolean hovered,
                                     boolean collapsed) {
        gfx.fill(x, y, x + DIMENSION_W, y + DIMENSION_H, BEVEL_DARK);
        if (selected) {
            gfx.fill(x, y, x + DIMENSION_W - 1, y + DIMENSION_H - 1, BUTTON_ON);
        } else {
            fillFromTexture(gfx, x, y, DIMENSION_W - 1, DIMENSION_H - 1);
        }
        gfx.fill(x, y, x + DIMENSION_W - 1, y + 1, BEVEL_LIGHT);
        gfx.fill(x, y, x + 1, y + DIMENSION_H - 1, BEVEL_LIGHT);
        boolean ender = QueryDto.ENDER_CHEST.equals(dimension.dimensionId());
        if (hovered) {
            gfx.fill(x + 1, y + 1, x + DIMENSION_W - 1, y + DIMENSION_H - 1, SLOT_HOVER);
            if (collapsed) {
                setHoverLabel(dimensionName(dimension),
                        viewing.isEmpty() ? "follows you" : "pinned",
                        "hover for the others");
            } else {
                labelDimension(dimension);
            }
            describeDimension(dimension, ender);
        }

        String glyph = dimensionGlyph(dimension.dimensionId());
        gfx.text(font, glyph, x + (DIMENSION_W - font.width(glyph)) / 2, y + 2,
                ender ? ENDER_GLYPH : TEXT_DARK_ON_BUTTON);
    }

    /**
     * What a dimension button is, in a sentence, beside the cursor.
     *
     * <p>The title row says which view the button is; it has no room to say
     * what pressing it does. A row of single letters is the least
     * self-explanatory thing on this window - "E" is either the End or your
     * ender chest depending on its colour - so the sentence goes where the
     * menu rows put theirs.
     *
     * <p>Only ever reached with the row expanded: pointing at the collapsed
     * button is what expands it, so it is the buttons underneath that get
     * hovered.
     */
    private void describeDimension(QueryDto.DimensionSummary dimension, boolean ender) {
        String head = ender ? "Your ender chest" : shortName(dimension.dimensionId());
        String detail = ender
                // Not a place, which is the whole reason it needs saying: the
                // other buttons are somewhere you can walk to and this is not.
                ? "The same six slots wherever you open one - carried with you, not left behind."
                : String.format("%,d containers indexed here.", dimension.containers());
        String action = viewing.equals(dimension.dimensionId())
                ? "Pinned here. Click the one you are standing in to follow you again."
                : "Click to pin the view here.";
        pendingHint = List.of(head, detail, action);
    }

    private int dimensionRowY() {
        return panelY + panelH - BOTTOM_H - 1;
    }

    /** Which of the buttons is the view currently being shown. */
    private int selectedDimensionIndex(List<QueryDto.DimensionSummary> dimensions) {
        String wanted = viewing.isEmpty() ? currentDimensionId() : viewing;
        for (int i = 0; i < dimensions.size(); i++) {
            if (dimensions.get(i).dimensionId().equals(wanted)) return i;
        }
        return 0;
    }

    /**
     * The dimension buttons to show, in their fixed order.
     *
     * <p>Held between status replies rather than rebuilt per frame: the row is
     * drawn every frame and the answer only moves when the server sends a new
     * status, about once a second.
     */
    private List<QueryDto.DimensionSummary> shownDimensions() {
        boolean ender = ChestTrackerConfig.get().enderChestView;
        if (shownDimensions != null && shownDimensionsFrom == status && shownDimensionsEnder == ender) {
            return shownDimensions;
        }

        List<QueryDto.DimensionSummary> shown = new ArrayList<>(status.dimensions().size());
        for (QueryDto.DimensionSummary dimension : status.dimensions()) {
            if (ender || !QueryDto.ENDER_CHEST.equals(dimension.dimensionId())) shown.add(dimension);
        }
        // A fixed order, so the row means the same thing every time it is
        // opened. The server returns them in whatever order its index happens
        // to hold, which changes as dimensions fill and empty - and a row of
        // buttons that reshuffles between two openings of the same screen is a
        // row nobody can build muscle memory for.
        shown.sort(DIMENSION_ORDER);

        shownDimensionsFrom = status;
        shownDimensionsEnder = ender;
        shownDimensions = List.copyOf(shown);
        return shownDimensions;
    }

    private static final Comparator<QueryDto.DimensionSummary> DIMENSION_ORDER =
            Comparator.comparingInt(entry -> rankOf(entry.dimensionId()));

    private List<QueryDto.DimensionSummary> shownDimensions;
    private QueryDto.StatusResponse shownDimensionsFrom;
    private boolean shownDimensionsEnder;

    /**
     * Where a view sits in the row: ender chest, overworld, Nether, End, then
     * anything a mod or a datapack added.
     *
     * <p>The ender chest leads because it is the only one that is not a place -
     * it is always with the player, so it is always a sensible thing to ask
     * about, wherever they are standing.
     */
    private static int rankOf(String dimensionId) {
        if (QueryDto.ENDER_CHEST.equals(dimensionId)) return 0;
        return switch (dimensionId) {
            case "minecraft:overworld" -> 1;
            case "minecraft:the_nether" -> 2;
            case "minecraft:the_end" -> 3;
            default -> 4;
        };
    }

    private int dimensionX(int index) {
        return panelX + 8 + index * (DIMENSION_W + 2);
    }

    /** A letter each, because there is no room for a word and no icon to use. */
    private static String dimensionGlyph(String dimensionId) {
        // Shares its letter with the End, and is told apart by colour instead:
        // see ENDER_GLYPH. A lowercase e was the old answer and was worse - it
        // read as a typo rather than as a different thing.
        if (QueryDto.ENDER_CHEST.equals(dimensionId)) return "E";
        String name = shortName(dimensionId);
        return switch (name) {
            case "overworld" -> "O";
            case "the_nether" -> "N";
            case "the_end" -> "E";
            default -> name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        };
    }

    /**
     * What a dimension button says when pointed at.
     *
     * <p>The ender chest needs saying in words. Its glyph is a lowercase e
     * beside the End's capital one, which is a thin distinction to hang on -
     * but it is last in the row, it is the only one that is not a place, and
     * the label is right there under the cursor.
     */
    /**
     * Names the dimension under the cursor, keeping the name over the count.
     *
     * <p>"the_nether" fits; "the_nether - 1,284 containers" does not, and a
     * button whose label is cut to "the_neth..." says less than the glyph
     * already on it.
     */
    private void labelDimension(QueryDto.DimensionSummary dimension) {
        if (QueryDto.ENDER_CHEST.equals(dimension.dimensionId())) {
            setHoverLabel("your ender chest");
            return;
        }
        setHoverLabel(shortName(dimension.dimensionId()),
                String.format("%,d containers", dimension.containers()));
    }

    /** A dimension's name in words, for the collapsed button's label. */
    private static String dimensionName(QueryDto.DimensionSummary dimension) {
        return QueryDto.ENDER_CHEST.equals(dimension.dimensionId())
                ? "your ender chest" : shortName(dimension.dimensionId());
    }

    /**
     * Drops the pinned dimension. Called when the world changes, because a
     * dimension pinned in one world means nothing in the next.
     */
    public static void forgetPinnedDimension() {
        viewing = "";
    }

    /**
     * Drops the icon and name caches, for the same reason and at the same time.
     *
     * <p>The names in particular are translated, and a server may send a
     * resource pack that renames items - so a name learned on the last server
     * is not necessarily what this one calls the same item. They are static and
     * survive the screen being closed, so nothing else would ever clear them.
     */
    public static void forgetItemNames() {
        ICON_CACHE.clear();
        NAME_CACHE.clear();
    }

    private String currentDimensionId() {
        return minecraft.player == null ? ""
                : minecraft.player.level().dimension().identifier().toString();
    }

    /** Switches which dimension the screen is reading, or returns false. */
    private boolean clickDimension(int mouseX, int mouseY) {
        List<QueryDto.DimensionSummary> dimensions = shownDimensions();
        if (dimensions.size() < 2) return false;

        int y = dimensionRowY();
        if (mouseY < y || mouseY >= y + DIMENSION_H) return false;

        // Only what is actually on screen can be clicked. Collapsed, that is
        // the one button showing the current view - and clicking it does
        // nothing, because it is already what is being shown.
        int count = dimensionsExpanded ? dimensions.size() : 1;
        for (int i = 0; i < count; i++) {
            int x = dimensionX(i);
            if (mouseX < x || mouseX >= x + DIMENSION_W) continue;
            if (!dimensionsExpanded) return true;

            // Picking the dimension you are standing in is how you go back to
            // "follow me": a blank view tracks the player through portals,
            // where a named one stays where it was put.
            String picked = dimensions.get(i).dimensionId();
            viewing = picked.equals(currentDimensionId()) ? "" : picked;
            // Left open. The row collapses when the cursor leaves it, and
            // closing it under a cursor that has not moved means the next click
            // lands on whatever the row was covering.
            back();
            refreshItems();
            return true;
        }
        return false;
    }

    /**
     * Why there is nothing to show.
     *
     * <p>Worth distinguishing: a vanilla server, a server that will not answer
     * this player, and a world that simply has not been scanned are three
     * different problems, and one message for all of them sends people looking
     * in the wrong place.
     */
    private String unavailableMessage() {
        return switch (availability) {
            case CONNECTING -> "Asking the server...";
            case NOT_PERMITTED -> "This server does not allow searching.";
            // On a server without the mod there is still an index, it is just
            // this client's own and it starts empty. Saying what fills it is
            // the difference between "broken" and "not yet".
            default -> ChestTrackerConfig.get().clientSideIndex && minecraft != null
                    && !minecraft.hasSingleplayerServer()
                    ? "Nothing seen yet - walk around, and open a chest."
                    : "No index here yet.";
        };
    }

    /**
     * Blits vanilla's chest window, widened by a scrollbar column.
     *
     * <p>The top and bottom borders are uniform horizontal bands, so a second
     * right-aligned copy overlaps the first seamlessly. The slot rows are not,
     * so those are drawn as left-plus-slots, a flat filler, and the right border.
     */
    private void drawWindow(Gfx gfx) {
        int searchY = panelY + TOP_H;
        int gridTop = searchY + SEARCH_H;
        int bottomY = gridTop + ROWS_H;

        drawBand(gfx, 0, TOP_H, panelY);
        drawSearchStrip(gfx, searchY);
        drawSlotBand(gfx, gridTop);
        drawBand(gfx, BOTTOM_V, BOTTOM_H, bottomY);
    }

    /**
     * Draws one horizontal band of the window, widened for the scrollbar column.
     *
     * <p>The band cannot simply be blitted twice, once right-aligned: its ends
     * are corner art, so the second copy paints a corner into the middle of the
     * panel. Instead the left part is drawn, the uniform middle is repeated a
     * pixel at a time to bridge the extra width, and the right edge is drawn
     * last so the border lands where the panel actually ends.
     */
    private void drawBand(Gfx gfx, int srcV, int height, int destY) {
        gfx.blit(CONTAINER_TEXTURE, panelX, destY, 0, srcV, SLOTS_W, height, SHEET, SHEET);
        gfx.blitStretched(CONTAINER_TEXTURE, panelX + SLOTS_W, destY, FILLER_U, srcV,
                SCROLL_COL, height, 1, height, SHEET, SHEET);
        gfx.blit(CONTAINER_TEXTURE, panelX + panelW - EDGE_W, destY,
                GUI_W - EDGE_W, srcV, EDGE_W, height, SHEET, SHEET);
    }

    /**
     * The slot rows. The gap left by widening for the scrollbar is filled from
     * the flat strip between the last slot and the window edge, repeated a pixel
     * at a time - not with a fixed colour. A resource pack repaints this texture,
     * and a hardcoded grey would sit in the middle of someone's brown chest.
     */
    private void drawSlotBand(Gfx gfx, int destY) {
        gfx.blit(CONTAINER_TEXTURE, panelX, destY, 0, ROWS_V, SLOTS_W, ROWS_H, SHEET, SHEET);
        gfx.blitStretched(CONTAINER_TEXTURE, panelX + SLOTS_W, destY, SLOT_FILLER_U, ROWS_V,
                SCROLL_COL, ROWS_H, 1, ROWS_H, SHEET, SHEET);
        gfx.blit(CONTAINER_TEXTURE, panelX + panelW - EDGE_W, destY,
                GUI_W - EDGE_W, ROWS_V, EDGE_W, ROWS_H, SHEET, SHEET);
    }

    /** The search strip, also built from the texture so packs carry through. */
    private void drawSearchStrip(Gfx gfx, int searchY) {
        gfx.blit(CONTAINER_TEXTURE, panelX, searchY, 0, 30, EDGE_W, SEARCH_H, SHEET, SHEET);
        gfx.blitStretched(CONTAINER_TEXTURE, panelX + EDGE_W, searchY, SLOT_FILLER_U, 30,
                panelW - EDGE_W * 2, SEARCH_H, 1, SEARCH_H, SHEET, SHEET);
        gfx.blit(CONTAINER_TEXTURE, panelX + panelW - EDGE_W, searchY,
                GUI_W - EDGE_W, 30, EDGE_W, SEARCH_H, SHEET, SHEET);
        drawGroove(gfx, panelX + 7, searchY + 2, panelW - 14, 12);
    }

    /**
     * Fills a rectangle with the window's own flat panel pixels.
     *
     * <p>Used instead of a fixed colour for the parts vanilla has no art for -
     * the scrollbar thumb and the toolbar buttons - so they follow a resource
     * pack rather than sitting in it as grey rectangles.
     */
    private void fillFromTexture(Gfx gfx, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        gfx.blitStretched(CONTAINER_TEXTURE, x, y, SLOT_FILLER_U, ROWS_V, w, h, 1, h, SHEET, SHEET);
    }

    /**
     * A sunken panel with the window's own pixels inside it.
     *
     * <p>Like {@link #drawGroove} but without the flat grey fill, so a resource
     * pack's chest shows through the middle of the list rather than a vanilla
     * rectangle sitting in it.
     */
    private void drawWell(Gfx gfx, int x, int y, int w, int h) {
        fillFromTexture(gfx, x, y, w, h);
        gfx.fill(x, y, x + w - 1, y + 1, GROOVE_DARK);
        gfx.fill(x, y, x + 1, y + h - 1, GROOVE_DARK);
        gfx.fill(x + w - 1, y, x + w, y + h, BEVEL_LIGHT);
        gfx.fill(x, y + h - 1, x + w, y + h, BEVEL_LIGHT);
    }

    /** The inset used by vanilla for slots and text fields. */
    private void drawGroove(Gfx gfx, int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, GROOVE_DARK);
        gfx.fill(x + 1, y + 1, x + w, y + h, BEVEL_LIGHT);
        gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, GROOVE);
    }

    private void drawGrid(Gfx gfx, int mouseX, int mouseY) {
        int hovered = slotAt(mouseX, mouseY);
        ChestTrackerConfig.Detail detail = ChestTrackerConfig.get().itemDetail();

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (scrollRow + row) * COLS + col;
                if (index >= items.size()) continue;

                int x = gridX() + col * SLOT;
                int y = gridY() + row * SLOT;
                QueryDto.ItemSummary summary = items.get(index);

                gfx.item(iconFor(summary.itemId()), x, y);
                drawCount(gfx, summary.totalCount(), x, y);
                if (index == hovered) gfx.fill(x, y, x + 16, y + 16, SLOT_HOVER);
            }
        }

        if (hovered >= 0) {
            QueryDto.ItemSummary summary = items.get(hovered);
            boolean shift = shiftHeld();
            // Only advertise the modifier when it would actually do something.
            boolean hint = detail == ChestTrackerConfig.Detail.SHIFT && !shift;
            setHoverLabel(
                    displayName(summary.itemId()),
                    String.format("%,d in %d", summary.totalCount(), summary.containerCount()),
                    hint ? "shift for detail" : "right-click to list");

            if (detail == ChestTrackerConfig.Detail.ALWAYS
                    || (detail == ChestTrackerConfig.Detail.SHIFT && shift)) {
                // Held rather than drawn here. The scrollbar and the title row
                // are painted after the grid, so a panel drawn at this point
                // had both of them on top of it - the scrollbar struck a grey
                // bar straight down the middle of the text.
                pendingTooltip = summary;
            }
        } else if (items.isEmpty()) {
            drawMessage(gfx, emptyGridMessage());
        }
    }

    /**
     * What an empty grid means, which is not always the same thing.
     *
     * <p>On a server without the mod the index only holds containers this
     * client has opened, so an empty grid is the normal starting state rather
     * than a fault - and the player needs to be told what fills it, or they
     * will conclude the mod does not work here and be right for the wrong
     * reason.
     */
    private String emptyGridMessage() {
        if (!pending.isBlank()) return "No match";
        // Before anything else: a pinned dimension is the one reason for an
        // empty grid that the player cannot see from the grid itself, and it
        // is the one they are most likely to have forgotten about.
        if (!viewing.isEmpty() && !viewing.equals(currentDimensionId())) {
            return "Nothing here in " + (QueryDto.ENDER_CHEST.equals(viewing)
                    ? "your ender chest" : shortName(viewing))
                    + " - the view is pinned to it, click another to unpin";
        }
        if (availability == ClientTracker.Availability.CLIENT_ONLY) {
            return "Open a chest to remember what is in it";
        }
        return "Nothing indexed yet";
    }

    /**
     * Writes a sentence where the grid would be, in a well of its own.
     *
     * <p>These messages are the ones a player reads when the grid is empty,
     * which is exactly when they cannot check what it says against anything
     * else - so a line running off the right-hand edge of the window loses the
     * half that explains what to do. "Open a chest to remember what is in it"
     * is a hundred and ninety pixels of text in a window a hundred and
     * sixty-nine wide, so it always did.
     *
     * <p>The well is there for the same reason the container list has one: the
     * window always draws its six rows of empty slots, and a sentence written
     * straight across them has nine bevelled squares behind every line, whose
     * white edges strike through the glyphs. Centred rather than set in the
     * top-left corner, because with the grid covered there is no column of
     * items for it to line up with - it is the only thing on the pane.
     */
    private void drawMessage(Gfx gfx, String text) {
        drawWell(gfx, listLeft(), listTop(), listWidth(), listHeight());

        List<String> lines = wrap(text, listWidth() - 8);
        int top = listTop() + (listHeight() - lines.size() * 10) / 2;
        for (int i = 0; i < lines.size(); i++) {
            String row = lines.get(i);
            gfx.text(font, Component.literal(row),
                    listLeft() + (listWidth() - font.width(row)) / 2, top + i * 10, TEXT_MAIN);
        }
    }

    /** Breaks text on spaces to fit a pixel width, without hyphenating. */
    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>(3);
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.width(candidate) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    /**
     * Whether either shift key is down.
     *
     * <p>Asked of the window rather than of {@code Screen}, which no longer
     * offers it on either target. The hotkey poll reads input the same way.
     */
    private boolean shiftHeld() {
        if (minecraft == null || minecraft.getWindow() == null) return false;
        return InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /**
     * What is actually known about the item under the cursor.
     *
     * <p>The title bar can hold one line, and the question that sends someone
     * to this screen - "I have hundreds of these, so why can I never find one"
     * - is usually answered by the second: they are inside shulker boxes.
     *
     * <p>Drawn by hand rather than through vanilla's tooltip renderer, whose
     * entry point differs between the two targets and whose styling would have
     * to be fought to match this window anyway.
     */
    private void drawItemTooltip(Gfx gfx, QueryDto.ItemSummary summary, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>(4);
        lines.add(displayName(summary.itemId()));
        lines.add(String.format("%,d in %d container%s",
                summary.totalCount(), summary.containerCount(),
                summary.containerCount() == 1 ? "" : "s"));
        if (summary.nestedCount() > 0) {
            lines.add(summary.nestedCount() == summary.totalCount()
                    ? "all of them inside shulker boxes"
                    : String.format("%,d inside shulker boxes", summary.nestedCount()));
        }
        if (summary.nearestDistSq() < Double.MAX_VALUE) {
            lines.add(String.format("nearest %.0fm away", Math.sqrt(summary.nearestDistSq())));
        }

        drawTextTooltip(gfx, lines, mouseX, mouseY);
    }

    /**
     * Lines of text in a vanilla tooltip frame, placed beside the cursor.
     *
     * <p>First line in white, the rest in grey: that is how the game separates
     * a name from what is being said about it, and both callers here are that
     * shape.
     */
    private void drawTextTooltip(Gfx gfx, List<String> lines, int mouseX, int mouseY) {
        int textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, font.width(line));
        int textHeight = lines.size() * 10 - 2;

        // Kept on screen rather than inside the window: a panel five lines wide
        // is wider than the window at the right-hand columns, and clamping it to
        // the window there pushed it back over the item being pointed at.
        int textX = mouseX + 12 + textWidth + 4 > width ? mouseX - 16 - textWidth : mouseX + 12;
        int textY = Math.max(8, Math.min(mouseY - 12, height - textHeight - 8));

        drawTooltipFrame(gfx, textX, textY, textWidth, textHeight);

        for (int i = 0; i < lines.size(); i++) {
            gfx.text(font, Component.literal(lines.get(i)),
                    textX, textY + i * 10, i == 0 ? TEXT_MAIN : TEXT_MUTED);
        }
    }

    /**
     * Vanilla's own tooltip frame, drawn to its recipe.
     *
     * <p>The panel used to be a flat box with a solid purple outline, which was
     * this mod's idea of what a tooltip looks like rather than the game's - it
     * sat next to a real item tooltip looking like a different program. Vanilla
     * insets the background three pixels around the text and runs a two-stop
     * gradient down the two side borders, and those numbers are what make it
     * recognisable, so they are copied rather than approximated.
     *
     * @param x the text's left edge, not the frame's
     */
    private void drawTooltipFrame(Gfx gfx, int x, int y, int textWidth, int textHeight) {
        int left = x - 3;
        int right = x + textWidth + 3;
        int top = y - 3;
        int bottom = y + textHeight + 3;

        gfx.fill(left, top - 1, right, top, TOOLTIP_BG);
        gfx.fill(left, bottom, right, bottom + 1, TOOLTIP_BG);
        gfx.fill(left, top, right, bottom, TOOLTIP_BG);
        gfx.fill(left - 1, top, left, bottom, TOOLTIP_BG);
        gfx.fill(right, top, right + 1, bottom, TOOLTIP_BG);

        gfx.fillGradient(left, top + 1, left + 1, bottom - 1, TOOLTIP_EDGE_TOP, TOOLTIP_EDGE_BOTTOM);
        gfx.fillGradient(right - 1, top + 1, right, bottom - 1, TOOLTIP_EDGE_TOP, TOOLTIP_EDGE_BOTTOM);
        gfx.fill(left, top, right, top + 1, TOOLTIP_EDGE_TOP);
        gfx.fill(left, bottom - 1, right, bottom, TOOLTIP_EDGE_BOTTOM);
    }

    /** The room a count has: the item's 16px, not the slot's 18. */
    private static final int COUNT_WIDTH = 16;

    /**
     * Stack counts sit bottom-right of the slot, light on dark, as in vanilla.
     *
     * <p>Shrunk to fit rather than allowed to run over. Vanilla never had this
     * problem because a stack stops at 64, but these are totals across a whole
     * world: six figures is ordinary and eight is not rare on a server. Even
     * "62k" is eighteen pixels of text - the full width of a slot - so drawn at
     * full size against the right edge it started a pixel inside the neighbour,
     * and "999k" ran a third of the way across it.
     *
     * <p>The alternative was to cut the label to three characters, but that
     * means rounding 999,000 to something that is no longer true. A number
     * nobody can read is better than a number that is wrong.
     */
    private void drawCount(Gfx gfx, int count, int slotX, int slotY) {
        if (count <= 1) return; // Vanilla omits a count of one.
        String label = abbreviate(count);

        int width = font.width(label);
        int right = slotX + 17;
        int bottom = slotY + 9 + font.lineHeight;

        if (width <= COUNT_WIDTH) {
            gfx.text(font, label, right - width, slotY + 9, TEXT_LIGHT);
            return;
        }
        gfx.textFromCorner(font, label, right, bottom, COUNT_WIDTH / (float) width, TEXT_LIGHT);
    }

    /**
     * A count in as few characters as it can be said in.
     *
     * <p>Thousands and millions were already here; billions are what an item
     * counter on a long-running server eventually needs, and without that tier
     * a two-billion total came out as "2000m" - five characters, and the widest
     * label the grid could produce.
     */
    private static String abbreviate(int count) {
        if (count < 1000) return Integer.toString(count);
        if (count < 1_000_000) return (count / 1000) + "k";
        if (count < 1_000_000_000) return (count / 1_000_000) + "m";
        return (count / 1_000_000_000) + "b";
    }

    /**
     * The scrollbar for whichever view is open.
     *
     * <p>Both views scroll, so the bar is told what it is scrolling rather than
     * reading the grid's numbers - a thumb sized off the item count while the
     * container list was showing would describe the wrong list.
     */
    private void drawScrollbar(Gfx gfx, int scroll, int maxScroll, int totalRows, int visibleRows) {
        int x = scrollbarX();
        int y = gridY() - 1;
        int height = ROWS * SLOT;
        drawGroove(gfx, x, y, 12, height);

        int thumbHeight = maxScroll == 0 ? height - 2
                : Math.max(15, (height - 2) * visibleRows / Math.max(1, totalRows));
        int travel = height - 2 - thumbHeight;
        int thumbY = y + 1 + (maxScroll == 0 ? 0 : travel * scroll / maxScroll);

        fillFromTexture(gfx, x + 1, thumbY, 10, thumbHeight);
        gfx.fill(x + 1, thumbY, x + 11, thumbY + 1, BEVEL_LIGHT);
        gfx.fill(x + 1, thumbY, x + 2, thumbY + thumbHeight, BEVEL_LIGHT);
        gfx.fill(x + 1, thumbY + thumbHeight - 1, x + 11, thumbY + thumbHeight, BEVEL_DARK);
        gfx.fill(x + 10, thumbY, x + 11, thumbY + thumbHeight, BEVEL_DARK);
    }

    /**
     * One line of the menu: what it controls, and where it currently stands.
     *
     * <p>These used to be four single-letter buttons that cycled on click. The
     * letters said nothing about what they did or what state they were in, so
     * the only way to find out was to click one and watch the results change -
     * and hoppers being hidden by default was undiscoverable that way.
     */
    /**
     * @param help what the row does, shown in the title bar while it is hovered
     */
    private record MenuRow(String label, String value, boolean active, String help) {}

    /**
     * The dropdown's rows, rebuilt only when one of the filters moves.
     *
     * <p>An open menu asks for these three times a frame - once to label the
     * hovered row, once to size the panel and once to draw it - and the answer
     * depends only on fields the player changes by clicking.
     */
    private List<MenuRow> menuRows() {
        if (menuRows == null) menuRows = buildMenuRows();
        return menuRows;
    }

    /** Drops the cached rows after anything they show has changed. */
    private void menuRowsChanged() {
        menuRows = null;
    }

    private List<MenuRow> menuRows;

    private List<MenuRow> buildMenuRows() {
        String origin = switch (originIndex) {
            // "built" rather than "player-placed": it includes containers whose
            // placement was never observed, which on any world older than the
            // mod is most of them.
            case 1 -> "built";
            case 2 -> "generated";
            default -> "any";
        };
        return List.of(
                new MenuRow("Sort by", sort.label, sort != Sort.COUNT,
                        "most, nearest, or A-Z"),
                new MenuRow("Origin", origin, originIndex != 0,
                        "built by players, or generated with the world"),
                new MenuRow("Inside shulkers", includeNested ? "counted" : "ignored", includeNested,
                        "whether a shulker's contents count as being in the chest"),
                // Two groups rather than one, because they are two questions.
                // A hopper's contents are in transit; a jukebox's are furniture.
                new MenuRow("Machines", includeMachines ? "shown" : "hidden", includeMachines,
                        "hoppers, droppers, dispensers, crafters"),
                new MenuRow("Functional blocks", includeUtility ? "shown" : "hidden", includeUtility,
                        "furnaces, brewing stands, jukeboxes, lecterns, pots, bookshelves"),
                // Read live rather than indexed, because they move; see
                // EntityContainers for why they are never stored.
                new MenuRow("Moving containers", includeEntities ? "shown" : "hidden", includeEntities,
                        "chest and hopper minecarts, chest boats - found only where loaded"));
    }

    private int buttonX(int index) {
        return panelX + panelW - 8 - (TOOLBAR_BUTTONS - index) * (BUTTON_SIZE + 1);
    }

    private int menuX() {
        return panelX + panelW - 8 - MENU_W;
    }

    private int menuY() {
        return panelY + 3 + BUTTON_SIZE + 1;
    }

    private int menuH() {
        return menuRows().size() * MENU_ROW_H + 3;
    }

    private void drawButtons(Gfx gfx, int mouseX, int mouseY) {
        for (int i = 0; i < TOOLBAR_BUTTONS; i++) {
            int x = buttonX(i);
            int y = panelY + 3;
            boolean hovered = mouseX >= x && mouseX < x + BUTTON_SIZE
                    && mouseY >= y && mouseY < y + BUTTON_SIZE;
            boolean active = i == 0 && menuOpen;

            gfx.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, BEVEL_DARK);
            if (active) {
                gfx.fill(x, y, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, BUTTON_ON);
            } else {
                fillFromTexture(gfx, x, y, BUTTON_SIZE - 1, BUTTON_SIZE - 1);
            }
            gfx.fill(x, y, x + BUTTON_SIZE - 1, y + 1, BEVEL_LIGHT);
            gfx.fill(x, y, x + 1, y + BUTTON_SIZE - 1, BEVEL_LIGHT);
            if (hovered) {
                gfx.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, SLOT_HOVER);
                hoverLabel = i == 0 ? "Filters and sorting" : "Close";
            }

            if (i == 0) {
                // Drawn rather than typed: the font has no glyph that reads as
                // a menu, and a letter would be back where we started.
                for (int line = 0; line < 3; line++) {
                    gfx.fill(x + 3, y + 3 + line * 3, x + BUTTON_SIZE - 3, y + 4 + line * 3, ICON);
                }
            } else {
                gfx.text(font, "X", x + 3, y + 2, TEXT_MAIN);
            }
        }
    }

    /**
     * Says what the hovered menu row does, in the title bar.
     *
     * <p>A row's label has to fit inside a hundred and thirty pixels, which is
     * not enough to say what "Machines" covers or which way "built" filters.
     * The title row is the one place on this window with room for a sentence,
     * and it is already the place the window explains itself.
     */
    private void labelMenuRow(int mouseX, int mouseY) {
        if (!menuOpen) return;

        int x = menuX();
        int y = menuY();
        if (mouseX < x || mouseX >= x + MENU_W) return;

        List<MenuRow> rows = menuRows();
        for (int i = 0; i < rows.size(); i++) {
            int rowY = y + 2 + i * MENU_ROW_H;
            if (mouseY < rowY - 1 || mouseY >= rowY + MENU_ROW_H - 1) continue;
            MenuRow row = rows.get(i);
            // The title row gets the label alone, which always fits; the
            // sentence goes to the tooltip that has room for it.
            hoverLabel = row.label();
            pendingHint = List.of(row.label() + SEP + row.value(), row.help());
            return;
        }
    }

    /**
     * The dropdown, drawn last so it sits over the grid rather than under it.
     */
    private void drawMenu(Gfx gfx, int mouseX, int mouseY) {
        if (!menuOpen) return;

        int x = menuX();
        int y = menuY();
        int height = menuH();

        gfx.fill(x - 1, y - 1, x + MENU_W + 1, y + height + 1, BEVEL_DARK);
        fillFromTexture(gfx, x, y, MENU_W, height);
        gfx.fill(x, y, x + MENU_W, y + 1, BEVEL_LIGHT);
        gfx.fill(x, y, x + 1, y + height, BEVEL_LIGHT);

        List<MenuRow> rows = menuRows();
        for (int i = 0; i < rows.size(); i++) {
            MenuRow row = rows.get(i);
            int rowY = y + 2 + i * MENU_ROW_H;
            if (mouseX >= x && mouseX < x + MENU_W && mouseY >= rowY - 1 && mouseY < rowY + MENU_ROW_H - 1) {
                gfx.fill(x + 1, rowY - 1, x + MENU_W, rowY + MENU_ROW_H - 1, SLOT_HOVER);
            }
            gfx.text(font, Component.literal(row.label()), x + 4, rowY, TEXT_MAIN);
            // The value is right-aligned so the states line up in a column and
            // can be read down without reading every label.
            String value = row.value();
            // The one place a colour still means something: a filter that has
            // been moved off its default reads at full strength, one left alone
            // is greyed. Both are vanilla greys.
            gfx.text(font, Component.literal(value), x + MENU_W - 4 - font.width(value), rowY,
                    row.active() ? TEXT_MAIN : TEXT_MUTED);
        }
    }

    // --- the search box's own list -----------------------------------------

    /** Reopens or rebuilds the list when the box is clicked into or moved in. */
    private void followSearchCursor() {
        if (search == null) return;
        boolean focused = search.isFocused();
        int cursor = search.getCursorPosition();
        if (focused == searchWasFocused && cursor == searchCursorWas) return;

        searchWasFocused = focused;
        searchCursorWas = cursor;
        refreshSuggestions();
    }

    /**
     * Rebuilds what the box is offering, from where the cursor is.
     *
     * <p>With nothing typed in the word under the cursor it offers the
     * categories themselves; once one has been started it offers the values
     * that category takes. Both are the same list to the eye and to the mouse -
     * click a row, it goes in the box - so there is one thing to learn rather
     * than two.
     */
    private void refreshSuggestions() {
        if (search == null || !search.isFocused()) {
            suggestions = List.of();
            categoriesAsked = false;
            return;
        }
        String word = wordAtCursor();
        List<String> next = word.isEmpty()
                // Only when asked for: see categoriesAsked.
                ? (categoriesAsked ? categoryPrefixes() : List.of())
                : SearchCategories.completionsFor(word);

        if (!next.equals(suggestions)) {
            suggestionIndex = 0;
            suggestionScroll = 0;
        }
        suggestions = next;
    }

    /** Keeps the chosen row on screen when the arrows walk off the end of it. */
    private void scrollToSuggestion() {
        if (suggestionIndex < suggestionScroll) {
            suggestionScroll = suggestionIndex;
        } else if (suggestionIndex >= suggestionScroll + SUGGESTIONS_VISIBLE) {
            suggestionScroll = suggestionIndex - SUGGESTIONS_VISIBLE + 1;
        }
    }

    private int suggestionRowsShown() {
        return Math.min(SUGGESTIONS_VISIBLE, suggestions.size());
    }

    private static List<String> categoryPrefixes() {
        List<String> rows = new ArrayList<>();
        for (SearchQuery.Category category : SearchCategories.available()) rows.add(category.prefix());
        return rows;
    }

    /** The word the cursor is inside, which is the one being completed. */
    private String wordAtCursor() {
        String text = search.getValue();
        int cursor = Math.max(0, Math.min(search.getCursorPosition(), text.length()));
        int start = text.lastIndexOf(' ', cursor - 1) + 1;
        return text.substring(start, cursor);
    }

    /**
     * Puts a suggestion in the box, replacing the word being typed.
     *
     * <p>A finished value gets a space after it, because the next thing typed
     * is a new word. A bare category prefix does not: the next thing typed
     * belongs to it.
     */
    private void applySuggestion(String chosen) {
        String text = search.getValue();
        int cursor = Math.max(0, Math.min(search.getCursorPosition(), text.length()));
        int start = text.lastIndexOf(' ', cursor - 1) + 1;

        boolean bare = SearchQuery.categoryOf(chosen) != null
                && chosen.equals(SearchQuery.categoryOf(chosen).prefix());
        String insert = bare ? chosen : chosen + " ";

        search.setValue(text.substring(0, start) + insert + text.substring(cursor));
        search.setCursorPosition(start + insert.length());
        search.setHighlightPos(start + insert.length());
        refreshSuggestions();
    }

    private int suggestionsX() {
        return panelX + 7;
    }

    private int suggestionsY() {
        return panelY + TOP_H + SEARCH_H;
    }

    private int suggestionsW() {
        return SLOTS_W - 14;
    }

    private boolean overSuggestions(double mouseX, double mouseY) {
        int y = suggestionsY();
        return mouseX >= suggestionsX() && mouseX < suggestionsX() + suggestionsW()
                && mouseY >= y && mouseY < y + suggestionRowsShown() * SUGGESTION_H + 2;
    }

    /** Which row is under the cursor, or -1. */
    private int suggestionAt(double mouseX, double mouseY) {
        if (suggestions.isEmpty()) return -1;
        int x = suggestionsX();
        int y = suggestionsY();
        if (mouseX < x || mouseX >= x + suggestionsW()) return -1;
        // Above the list is not row zero. Integer division truncates towards
        // zero, so a click a few pixels above the top - which is exactly where
        // the search box is - divided out to 0 and inserted the first category:
        // clicking the box typed "@" into it.
        if (mouseY < y + 1) return -1;

        int row = (int) ((mouseY - y - 1) / SUGGESTION_H);
        if (row < 0 || row >= suggestionRowsShown()) return -1;
        return suggestionScroll + row;
    }

    /**
     * The list under the search box.
     *
     * <p>Drawn in the window's own idiom rather than as a vanilla tooltip: it
     * is part of the box, not a remark about it, and it has to be clickable.
     */
    private void drawSuggestions(Gfx gfx, int mouseX, int mouseY) {
        if (suggestions.isEmpty()) return;

        int x = suggestionsX();
        int y = suggestionsY();
        int width = suggestionsW();
        int shown = suggestionRowsShown();
        int height = shown * SUGGESTION_H + 2;
        boolean scrollable = suggestions.size() > shown;

        gfx.fill(x - 1, y - 1, x + width + 1, y + height + 1, BEVEL_DARK);
        fillFromTexture(gfx, x, y, width, height);
        gfx.fill(x, y, x + width, y + 1, BEVEL_LIGHT);
        gfx.fill(x, y, x + 1, y + height, BEVEL_LIGHT);

        // The text stops short of the bar when there is one, rather than
        // running under it.
        int textRight = x + width - (scrollable ? 6 : 0);

        for (int row = 0; row < shown; row++) {
            int i = suggestionScroll + row;
            if (i >= suggestions.size()) break;

            int rowY = y + 1 + row * SUGGESTION_H;
            String text = suggestions.get(i);
            boolean hovered = mouseX >= x && mouseX < x + width
                    && mouseY >= rowY && mouseY < rowY + SUGGESTION_H;
            // The keyboard's place in the list is drawn the same as the
            // mouse's, so Tab and a click are visibly the same choice.
            if (hovered || i == suggestionIndex) {
                gfx.fill(x + 1, rowY, x + width, rowY + SUGGESTION_H, SLOT_HOVER);
            }
            String label = truncate(text, textRight - x - 8);
            gfx.text(font, Component.literal(label), x + 4, rowY + 2, TEXT_MAIN);

            // What the category is for, when the list is the categories. Fitted
            // to what is left after the label rather than simply right-aligned:
            // "tab:" and "the game's own creative tab" are wider than the row
            // together, and drawing both regardless ran the two into each other.
            SearchQuery.Category category = SearchQuery.categoryOf(text);
            if (category != null && text.equals(category.prefix())) {
                int helpLeft = x + 4 + font.width(label) + HELP_GAP;
                int room = textRight - 4 - helpLeft;
                if (room >= MIN_HELP_WIDTH) {
                    String help = truncate(category.help(), room);
                    gfx.text(font, Component.literal(help),
                            textRight - 4 - font.width(help), rowY + 2, TEXT_MUTED);
                }
            }
        }

        // A bar, because "there are four hundred item tags" is not something a
        // list of six rows says on its own.
        if (scrollable) {
            int barX = x + width - 5;
            gfx.fill(barX, y + 1, barX + 4, y + height - 1, GROOVE_DARK);
            int travel = height - 2;
            int thumb = Math.max(6, travel * shown / suggestions.size());
            int thumbY = y + 1 + (travel - thumb)
                    * suggestionScroll / Math.max(1, suggestions.size() - shown);
            fillFromTexture(gfx, barX, thumbY, 4, thumb);
        }
    }

    /**
     * The containers holding the chosen item, one per row.
     *
     * <p>The rows get a well of their own rather than being written across the
     * slot grid the window always draws. Nine bevelled squares behind every
     * line of text is what made this pane unreadable: the grid's white edges
     * struck through the glyphs, and its columns read as table rules that were
     * not there.
     */
    private void drawLocations(Gfx gfx, int mouseX, int mouseY) {
        drawWell(gfx, listLeft(), listTop(), listWidth(), listHeight());

        int textX = listLeft() + 4;

        if (containers.isEmpty()) {
            gfx.text(font, Component.literal(containersPending ? "Looking..." : "Nothing holds that."),
                    textX, listRowY(0), TEXT_MAIN);
            return;
        }

        List<DetailRow> rows = detailRows();
        int hovered = rowAt(mouseX, mouseY);
        int last = Math.min(rows.size(), detailScroll + listRowsVisible());
        for (int index = detailScroll; index < last; index++) {
            DetailRow row = rows.get(index);
            int y = listRowY(index - detailScroll);
            if (index == hovered) {
                gfx.fill(listLeft() + 1, y - 1, listLeft() + listWidth() - 1,
                        y + DETAIL_ROW - 1, ROW_HOVER);
            }

            gfx.text(font, Component.literal(row.distance()),
                    listLeft() + listWidth() - 4 - row.distanceWidth(), y, TEXT_MAIN);
            gfx.text(font, Component.literal(row.description()), textX, y, TEXT_MAIN);
        }
        setHoverLabel("Click to be guided", "right-click to go back");
    }

    /**
     * A container row, laid out once.
     *
     * <p>Two columns rather than one string: at 162 pixels a row of four facts
     * does not fit any world with five-digit coordinates, and trimming one
     * string drops whatever is last. The distance is the field a player is
     * choosing on, so it is anchored to the right and the description gives
     * way first.
     */
    private record DetailRow(String description, String distance, int distanceWidth) {}

    /**
     * The open pane's rows, built when the answer changes rather than per frame.
     *
     * <p>Every part of a row is fixed by the reply that produced it - the
     * distance came off the query and does not change until the next one - so
     * formatting nine of them on every frame was nine {@code String.format}s
     * and nine truncations to draw the same nine lines again.
     */
    private List<DetailRow> detailRows() {
        if (detailRows != null && detailRowsFrom == containers) return detailRows;

        int textWidth = listWidth() - 8;
        List<DetailRow> rows = new ArrayList<>(containers.size());
        for (QueryDto.ContainerHit hit : containers) {
            String distance = distanceOf(hit);
            int distanceWidth = font.width(distance);
            rows.add(new DetailRow(truncate(describe(hit), textWidth - distanceWidth - 4),
                    distance, distanceWidth));
        }

        detailRowsFrom = containers;
        detailRows = rows;
        return rows;
    }

    private List<DetailRow> detailRows;
    private List<QueryDto.ContainerHit> detailRowsFrom;

    /**
     * A row's left column: how much, in what, and how sure we are of it.
     *
     * <p>The marks come before the position rather than after it, so that the
     * one part of the row that can be trimmed is the part a player does not
     * need to read - guidance walks them there, the coordinates are a courtesy.
     */
    private String describe(QueryDto.ContainerHit hit) {
        StringBuilder line = new StringBuilder();
        line.append(hit.matchedCount()).append("x ").append(shortName(hit.typeId()));
        if (hit.nested()) line.append(" *");
        // A container we have only ever seen from the outside must not read as
        // one we have counted - "?" is honest, an unmarked row is not.
        if (!hit.contentsKnown()) line.append(" ?");
        line.append("  ").append(BlockKey.toString(hit.pos()));
        return line.toString();
    }

    /** A row's right column. */
    private static String distanceOf(QueryDto.ContainerHit hit) {
        return String.format("%.0fm", Math.sqrt(hit.distanceSq()));
    }

    // --- item display ------------------------------------------------------

    private static ItemStack iconFor(String itemId) {
        return ICON_CACHE.computeIfAbsent(itemId, id -> {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    /**
     * The item's translated name, falling back to its registry path.
     *
     * <p>A server may index an item this client does not have - a mod present
     * only on the server - so the registry lookup genuinely can miss, and the
     * row still has to say something useful.
     */
    private static String displayName(String itemId) {
        // Cached for the same reason the icons are, only more so: resolving a
        // hover name builds a Component and runs it through the language file,
        // and this is asked once per comparison by the A-Z sort - some nine
        // thousand times for a full grid - as well as once per frame for
        // whatever is under the cursor.
        return NAME_CACHE.computeIfAbsent(itemId, id -> {
            ItemStack stack = iconFor(id);
            return stack.isEmpty() ? shortName(id) : stack.getHoverName().getString();
        });
    }

    /** The separator between parts of a title-row label. */
    private static final String SEP = "  -  ";

    /** How much room the title row has, once the toolbar has taken its share. */
    private int titleWidth() {
        return buttonX(0) - (panelX + 8) - 4;
    }

    /**
     * Sets the title-row label from parts, dropping the least important until
     * what is left fits.
     *
     * <p>The row is about a hundred and forty pixels - some two dozen
     * characters - and every label built for it wanted more than that, so the
     * old flat truncation cut whatever happened to be last. Usually that was
     * the item's own name, which is the one part the player is reading: an
     * ellipsis through "Diamond" to preserve "right-click to list" has it
     * exactly backwards.
     *
     * <p>So {@code head} is what the label is about and is kept whatever
     * happens; the extras are context, tried in order and dropped from the end.
     * A wide window shows the lot, a narrow one shows the name.
     */
    private void setHoverLabel(String head, String... extras) {
        int maxWidth = titleWidth();
        for (int keep = extras.length; keep > 0; keep--) {
            StringBuilder candidate = new StringBuilder(head);
            for (int i = 0; i < keep; i++) candidate.append(SEP).append(extras[i]);
            if (font.width(candidate.toString()) <= maxWidth) {
                hoverLabel = candidate.toString();
                return;
            }
        }
        // Even alone it can overrun - a long modded item name - and only then
        // is cutting it the least bad answer.
        hoverLabel = truncate(head, maxWidth);
    }

    /**
     * Trims text to a pixel width, with an ellipsis when it does not fit.
     *
     * <p>One pass rather than a measurement per character: the old loop built a
     * fresh string and measured the whole of it for every character it kept, so
     * a name that overran by one letter cost thirty measurements of thirty
     * growing strings - and this runs for every row of the container list, and
     * every suggestion, on every frame.
     */
    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static String shortName(String registryId) {
        int colon = registryId.indexOf(':');
        return colon < 0 ? registryId : registryId.substring(colon + 1);
    }

    // --- input -------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        // Clicking the box is the question "what can I type here", and the
        // only thing that opens the list of categories.
        if (event.button() == 0 && search != null
                && mouseX >= search.getX() - 3 && mouseX < search.getX() + search.getWidth() + 3
                && mouseY >= search.getY() && mouseY < search.getY() + 12) {
            categoriesAsked = true;
            // Straight away: the box may already have focus and the cursor may
            // not move, so nothing else this frame would notice the click.
            refreshSuggestions();
        }

        // The suggestion list overlays the grid too, and is the thing most
        // recently pointed at, so it is asked first.
        if (event.button() == 0) {
            int row = suggestionAt(mouseX, mouseY);
            if (row >= 0) {
                suggestionIndex = row;
                applySuggestion(suggestions.get(row));
                return true;
            }
        }

        // The menu overlays the grid, so it gets first refusal on a click.
        if (event.button() == 0 && menuOpen) {
            if (clickMenuRow(mouseX, mouseY)) return true;
            if (!overToolbar(mouseX, mouseY)) {
                // Anywhere else dismisses it, without also acting on whatever
                // was underneath - which would be a click the player never
                // meant to make on the grid.
                menuOpen = false;
                return true;
            }
        }

        if (event.button() == 0 && clickButton(mouseX, mouseY)) return true;
        if (event.button() == 0 && clickDimension(mouseX, mouseY)) return true;
        if (!canQuery()) return super.mouseClicked(event, doubleClick);

        if (event.button() == 1) {
            if (selectedItemId != null) {
                back();
                // Changes that arrived while the pane was open were only applied
                // to the pane; the grid behind it is caught up here.
                if (itemsStale) refreshItems(false);
                return true;
            }
            // Right-click opens the list of places. Left-click is the common
            // case - point me at this - and the list is the answer to a
            // narrower question, so it is the one behind the second button.
            int index = slotAt(mouseX, mouseY);
            if (index >= 0) {
                selectItem(items.get(index).itemId());
                return true;
            }
        }

        if (event.button() == 0) {
            // The bar serves both views, so it is tested before either of them.
            if (mouseX >= scrollbarX() && mouseX < scrollbarX() + 12
                    && mouseY >= gridY() - 1 && mouseY < gridY() - 1 + ROWS * SLOT) {
                draggingScrollbar = true;
                dragScrollbar(mouseY);
                return true;
            }
            if (selectedItemId == null) {
                int index = slotAt(mouseX, mouseY);
                if (index >= 0) {
                    highlightItem(items.get(index).itemId());
                    return true;
                }
            } else {
                int index = rowAt(mouseX, mouseY);
                if (index >= 0) {
                    guideTo(containers.get(index));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean overToolbar(int mouseX, int mouseY) {
        int y = panelY + 3;
        if (mouseY < y || mouseY >= y + BUTTON_SIZE) return false;
        return mouseX >= buttonX(0) && mouseX < buttonX(TOOLBAR_BUTTONS - 1) + BUTTON_SIZE;
    }

    private boolean clickButton(int mouseX, int mouseY) {
        int y = panelY + 3;
        if (mouseY < y || mouseY >= y + BUTTON_SIZE) return false;

        for (int i = 0; i < TOOLBAR_BUTTONS; i++) {
            int x = buttonX(i);
            if (mouseX < x || mouseX >= x + BUTTON_SIZE) continue;
            if (i == 0) {
                menuOpen = !menuOpen;
            } else {
                onClose();
            }
            return true;
        }
        return false;
    }

    /**
     * Applies a click on one menu row.
     *
     * <p>The menu stays open: these settings are usually adjusted together, and
     * reopening it after each one would be four clicks where one is meant.
     */
    private boolean clickMenuRow(int mouseX, int mouseY) {
        int x = menuX();
        int y = menuY();
        if (mouseX < x || mouseX >= x + MENU_W || mouseY < y || mouseY >= y + menuH()) return false;

        int row = (mouseY - (y + 1)) / MENU_ROW_H;
        switch (row) {
            case 0 -> {
                // Sorting is done on what we already have, so it needs no query.
                sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
                items = sorted(items);
                scrollRow = 0;
            }
            case 1 -> {
                originIndex = (originIndex + 1) % 3;
                refreshItems();
            }
            case 2 -> {
                includeNested = !includeNested;
                refreshItems();
            }
            case 3 -> {
                includeMachines = !includeMachines;
                refreshItems();
            }
            case 4 -> {
                includeUtility = !includeUtility;
                refreshItems();
            }
            case 5 -> {
                includeEntities = !includeEntities;
                refreshItems();
            }
            default -> {
                return false;
            }
        }
        // Every arm above changes something a row displays.
        menuRowsChanged();
        return true;
    }

    private void dragScrollbar(int mouseY) {
        int height = ROWS * SLOT;
        boolean grid = selectedItemId == null;
        int maxScroll = grid ? maxScrollRow() : maxDetailScroll();
        if (maxScroll == 0) return;
        double fraction = (mouseY - (gridY() - 1)) / (double) height;
        int row = Math.max(0, Math.min(maxScroll, (int) Math.round(fraction * maxScroll)));
        if (grid) {
            scrollRow = row;
        } else {
            detailScroll = row;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            dragScrollbar((int) event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    private void guideTo(QueryDto.ContainerHit hit) {
        // Through the hit rather than its position: a chest minecart picked out
        // of the list is guided to wherever it has got to, not to the rail it
        // was on when the list was drawn.
        ContainerHighlight.get().selectHits(List.of(hit),
                minecraft.player.level().dimension().identifier().toString(),
                displayName(selectedItemId));
        // So the container, once opened, can mark the slots holding it.
        ContainerHighlight.get().searchingFor(selectedItemId);
        onClose();
    }

    /**
     * Tab completes, the arrows choose, and Escape puts the list away.
     *
     * <p>Taken before the search box sees them, and only while the list is up:
     * Tab in a text field otherwise moves the focus, and Escape otherwise
     * closes the window - which is the right thing for both keys to do when
     * there is no list to act on, so neither is taken then.
     */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!suggestions.isEmpty()) {
            switch (event.key()) {
                case GLFW.GLFW_KEY_TAB -> {
                    applySuggestion(suggestions.get(
                            Math.max(0, Math.min(suggestionIndex, suggestions.size() - 1))));
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    suggestionIndex = (suggestionIndex + 1) % suggestions.size();
                    scrollToSuggestion();
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    suggestionIndex = (suggestionIndex + suggestions.size() - 1) % suggestions.size();
                    scrollToSuggestion();
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    // The list first, the window second. Two presses to leave
                    // is better than losing the search to a stray one.
                    suggestions = List.of();
                    categoriesAsked = false;
                    return true;
                }
                default -> { }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int step = (int) Math.signum(deltaY);

        // The list is over the grid, so it takes the wheel while the pointer is
        // on it - otherwise scrolling a list of four hundred tags would scroll
        // the items behind it instead.
        if (!suggestions.isEmpty() && overSuggestions(mouseX, mouseY)) {
            int maxScroll = Math.max(0, suggestions.size() - suggestionRowsShown());
            suggestionScroll = Math.max(0, Math.min(maxScroll, suggestionScroll - step));
            return true;
        }
        if (selectedItemId == null) {
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - step));
        } else {
            // The list used to swallow the wheel silently, which read as a
            // frozen pane whenever an item was in more places than fit.
            detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll - step));
        }
        return true;
    }

    // Two signatures the Gfx facade cannot hide: 26.x renamed both the render
    // entry point and the background hook, and changed their parameter types as
    // part of the deferred-rendering rework. The window is drawn in the
    // background hook so the search field renders on top of it rather than under.
    //? if >=26.1 {
    /*@Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawWindow(new Gfx(graphics));
    }
    *///?} else {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawWindow(new Gfx(graphics));
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
