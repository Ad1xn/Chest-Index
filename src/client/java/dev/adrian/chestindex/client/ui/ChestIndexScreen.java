package dev.adrian.chestindex.client.ui;

import dev.adrian.chestindex.client.ClientTracker;
import dev.adrian.chestindex.client.SearchCategories;
import dev.adrian.chestindex.client.highlight.ContainerHighlight;
import dev.adrian.chestindex.client.ActionBar;
import dev.adrian.chestindex.client.platform.Gfx;
import dev.adrian.chestindex.client.platform.Keys;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.SearchQuery;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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

public final class ChestIndexScreen extends Screen {
    private static final Identifier CONTAINER_TEXTURE =
            Identifier.parse("minecraft:textures/gui/container/generic_54.png");

    private static final int SHEET = 256;
    private static final int GUI_W = 176;
    private static final int TOP_H = 17;
    private static final int ROWS_V = 17;
    private static final int ROWS_H = 108;
    private static final int BOTTOM_V = 215;
    private static final int BOTTOM_H = 7;
    private static final int SLOTS_W = 169;
    private static final int SCROLL_COL = 14;
    private static final int EDGE_W = 7;
    private static final int FILLER_U = 100;
    private static final int SLOT_FILLER_U = 170;

    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int SLOT = 18;
    private static final int SEARCH_H = 16;

    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int GROOVE = 0xFF8B8B8B;
    private static final int GROOVE_DARK = 0xFF373737;
    private static final int TEXT_MAIN = 0xFFFFFFFF;

    private static final int TEXT_MUTED = 0xFFAAAAAA;

    private static final int ICON = 0xFF404040;
    private static final int TEXT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_HOVER = 0x80FFFFFF;
    private static final int ROW_HOVER = 0x40000000;
    private static final int BUTTON_ON = 0xFF6A9A4A;

    private static final int TEXT_DARK_ON_BUTTON = 0xFF202020;

    private static final int ENDER_GLYPH = 0xFF5A2D82;

    private static final int DIMENSION_W = 12;
    private static final int DIMENSION_H = 12;

    private static final int TOOLTIP_BG = 0xF0100010;
    private static final int TOOLTIP_EDGE_TOP = 0x505000FF;
    private static final int TOOLTIP_EDGE_BOTTOM = 0x5028007F;

    private static final int BUTTON_SIZE = 12;
    private static final int TOOLBAR_BUTTONS = 2;
    private static final int MENU_W = 132;
    private static final int MENU_ROW_H = 12;

    private static final long AUTO_REFRESH_MIN_MS = 400;

    private static final long STATUS_INTERVAL_MS = 1000;

    private static final int SCAN_BAR_H = 3;

    private static final int SCAN_BAR_BG = 0xFF373737;
    private static final int SCAN_BAR_FILL = 0xFF6A9A4A;

    private static final long REFUSED_RETRY_MS = 2000;

    private static final int MAX_CONTAINERS = 64;
    private static final int DETAIL_ROW = 11;

    private static final int LIST_PAD = 3;

    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

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
    private String pending = ChestIndexConfig.get().searchText;

    private List<QueryDto.ItemSummary> items = List.of();
    private List<QueryDto.ContainerHit> containers = List.of();
    private String selectedItemId;
    private int scrollRow;

    private int detailScroll;

    private boolean draggingScrollbar;
    private boolean menuOpen;

    private ClientTracker.Availability availability = ClientTracker.Availability.NONE;

    private int newestItemsReply;
    private int newestContainersReply;
    private int newestStatusReply;

    private boolean containersPending;

    private long lastChangeToken;
    private long lastAutoRefresh;

    private boolean itemsStale;

    private Sort sort = Sort.parse(ChestIndexConfig.get().sortMode);
    private boolean includeNested = ChestIndexConfig.get().includeNested;
    private boolean includeMachines = ChestIndexConfig.get().showMachines;
    private boolean includeUtility = ChestIndexConfig.get().showUtility;
    private boolean includeEntities = ChestIndexConfig.get().showEntities;
    private int originIndex = Math.max(0, Math.min(2, ChestIndexConfig.get().originFilter));

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private String hoverLabel;

    private QueryDto.ItemSummary pendingTooltip;

    private List<String> pendingHint;

    private QueryDto.StatusResponse status = QueryDto.StatusResponse.empty(0);

    private long lastStatusAsk;

    private static String viewing = "";

    private boolean dimensionsExpanded;

    private List<String> suggestions = List.of();

    private int suggestionIndex;

    private boolean searchWasFocused;
    private int searchCursorWas = -1;

    private static final int SUGGESTION_H = 11;

    private static final int HELP_GAP = 6;

    private static final int MIN_HELP_WIDTH = 24;

    private static final int SUGGESTIONS_VISIBLE = 6;

    private int suggestionScroll;

    private boolean categoriesAsked;

    // The hotkey's own character (e.g. dead-key ^ on macOS) arrives after the
    // screen opens. Drop the first char if no key was pressed in this screen
    // yet, or if it's an accent like ^ ` ´ that a dead key emits late.
    private boolean dropOpeningChar;
    private boolean keyPressedHere;

    public static ChestIndexScreen fromHotkey() {
        ChestIndexScreen screen = new ChestIndexScreen();
        screen.dropOpeningChar = true;
        return screen;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (dropOpeningChar) {
            dropOpeningChar = false;
            if (!keyPressedHere
                    || Character.getType(event.codepoint()) == Character.MODIFIER_SYMBOL) return true;
        }
        return super.charTyped(event);
    }

    public ChestIndexScreen() {
        super(Component.literal("Chest Index"));
    }

    @Override
    protected void init() {
        boolean reopening = search != null;
        int cursor = reopening ? search.getCursorPosition() : -1;

        panelW = GUI_W + SCROLL_COL;
        panelH = TOP_H + SEARCH_H + ROWS_H + BOTTOM_H;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        search = new EditBox(font, panelX + 11, panelY + TOP_H + 2, panelW - 22, 12,
                Component.literal("Search"));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setHint(Component.literal("Search"));
        search.setValue(pending);
        if (reopening) {
            int at = Math.min(cursor, pending.length());
            search.setCursorPosition(at);
            search.setHighlightPos(at);
        } else {
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

        detailRows = null;
        menuRowsChanged();

        availability = ClientTracker.availability();
        lastChangeToken = ClientTracker.changeToken();
        ClientTracker.setWatching(true);
        refreshItems(!reopening);
    }

    @Override
    public void removed() {
        ClientTracker.setWatching(false);
        rememberPreferences();
        super.removed();
    }

    private void rememberPreferences() {
        ChestIndexConfig config = ChestIndexConfig.get();
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

    private QueryDto.Filters filters() {
        return new QueryDto.Filters(includeNested, includeMachines, includeUtility,
                includeEntities, originIndex);
    }

    private void refreshItems() {
        refreshItems(true);
    }

    private void pollStatus() {
        long now = System.currentTimeMillis();
        if (now - lastStatusAsk < STATUS_INTERVAL_MS || !mayAttempt()) return;
        lastStatusAsk = now;
        ClientTracker.status().thenAccept(response -> minecraft.execute(() -> {
            if (response.requestId() <= newestStatusReply) return;
            newestStatusReply = response.requestId();
            status = response;
            if (!viewing.isEmpty() && shownDimensions().stream()
                    .noneMatch(entry -> entry.dimensionId().equals(viewing))) {
                viewing = "";
                refreshItems();
            }
        }));
    }

    private void refreshItems(boolean resetView) {
        if (!mayAttempt()) return;
        ClientTracker.summarise(pending, filters(), ChestIndexConfig.get().resultLimit(), viewing)
                .thenAccept(response ->
                minecraft.execute(() -> {
                    if (response.requestId() <= newestItemsReply) return;
                    newestItemsReply = response.requestId();
                    backgroundAnswered();
                    items = sorted(withoutOtherTabs(response.items()));
                    itemsStale = false;
                    if (!resetView) {
                        scrollRow = Math.min(scrollRow, maxScrollRow());
                        return;
                    }
                    scrollRow = 0;
                    back();
                }));
    }

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
                    detailScroll = Math.min(detailScroll, maxDetailScroll());
                }));
    }

    private void retryWhileRefused() {
        if (availability != ClientTracker.Availability.NOT_PERMITTED) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoRefresh < REFUSED_RETRY_MS) return;
        lastAutoRefresh = now;
        refreshItems(false);
    }

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
            refreshContainers();
            itemsStale = true;
        }
    }

    private long backgroundInterval() {
        long scaled = lastQueryMs * BACKGROUND_COST_MULTIPLE;
        return Math.max(AUTO_REFRESH_MIN_MS, Math.min(AUTO_REFRESH_MAX_MS, scaled));
    }

    private static final int BACKGROUND_COST_MULTIPLE = 8;

    private static final long AUTO_REFRESH_MAX_MS = 3000;

    private long backgroundStartedAt;

    private long lastQueryMs;

    private void backgroundAnswered() {
        if (backgroundStartedAt == 0L) return;
        lastQueryMs = Math.max(0L, System.currentTimeMillis() - backgroundStartedAt);
        backgroundStartedAt = 0L;
    }

    private boolean canQuery() {
        return availability == ClientTracker.Availability.LOCAL
                || availability == ClientTracker.Availability.SERVER
                || availability == ClientTracker.Availability.CLIENT_ONLY;
    }

    private boolean mayAttempt() {
        return canQuery() || availability == ClientTracker.Availability.NOT_PERMITTED;
    }

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
            case COUNT -> copy.sort(Comparator.comparingInt(QueryDto.ItemSummary::totalCount).reversed());
        }
        return copy;
    }

    private void highlightItem(String itemId) {
        if (!mayAttempt()) return;
        String label = displayName(itemId);

        if (QueryDto.ENDER_CHEST.equals(viewing)) {
            markInEnderChest(itemId, label);
            return;
        }
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
                    ContainerHighlight.get().selectHits(response.hits(), dimensionId, label);
                    ContainerHighlight.get().searchingFor(itemId);
                }));
        onClose();
    }

    private void markInEnderChest(String itemId, String label) {
        java.util.List<Long> chests = dev.adrian.chestindex.client.EnderChests.nearby();
        String dimensionId = minecraft.player == null ? null
                : minecraft.player.level().dimension().identifier().toString();

        ContainerHighlight.get().markCarried(itemId, label, chests, dimensionId);
        ActionBar.say(Component.literal(chests.isEmpty()
                ? "Open your ender chest - " + label + " will be marked"
                : "Nearest ender chest marked - " + label + " is inside"));
        onClose();
    }

    private void selectItem(String itemId) {
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

    private int listRowsVisible() {
        return (listHeight() - LIST_PAD * 2) / DETAIL_ROW;
    }

    private int maxDetailScroll() {
        return Math.max(0, containers.size() - listRowsVisible());
    }

    private int listRowY(int row) {
        return listTop() + LIST_PAD + row * DETAIL_ROW;
    }

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

    private void draw(Gfx gfx, int mouseX, int mouseY) {
        followSearchCursor();
        hoverLabel = null;
        pendingTooltip = null;
        pendingHint = null;
        drawButtons(gfx, mouseX, mouseY);

        ClientTracker.Availability now = ClientTracker.availability();
        if (now != availability) {
            availability = now;
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

        labelMenuRow(mouseX, mouseY);

        String title = hoverLabel != null ? hoverLabel
                : selectedItemId != null ? displayName(selectedItemId) : "Chest Index";
        gfx.text(font, Component.literal(truncate(title, titleWidth())),
                panelX + 8, panelY + 6, TEXT_MAIN);

        if (pendingTooltip != null) drawItemTooltip(gfx, pendingTooltip, mouseX, mouseY);

        drawMenu(gfx, mouseX, mouseY);
        drawSuggestions(gfx, mouseX, mouseY);

        if (pendingHint != null) drawTextTooltip(gfx, pendingHint, mouseX, mouseY);
    }

    private void drawScanBar(Gfx gfx) {
        if (!status.scanning()) return;

        int x = panelX + 8;
        int width = panelW - 16;
        int y = panelY + TOP_H + SEARCH_H - SCAN_BAR_H - 1;

        gfx.fill(x, y, x + width, y + SCAN_BAR_H, SCAN_BAR_BG);
        float fraction = status.progress();
        if (fraction > 0) {
            gfx.fill(x, y, x + (int) (width * fraction), y + SCAN_BAR_H, SCAN_BAR_FILL);
        }
        if (hoverLabel == null) {
            String whose = availability == ClientTracker.Availability.SERVER
                    ? "The server is indexing the world..."
                    : "Indexing the world...";
            String detail = status.regionsTotal() > 0
                    ? String.format("%d%%  ·  %,d of %,d regions  ·  %,d chunks",
                            Math.round(fraction * 100), status.regionsRead(),
                            status.regionsTotal(), status.chunksRead())
                    : String.format("%,d chunks", status.chunksRead());
            setHoverLabel(whose, detail);
        }
    }

    private void drawDimensions(Gfx gfx, int mouseX, int mouseY) {
        List<QueryDto.DimensionSummary> dimensions = shownDimensions();
        if (dimensions.size() < 2) return;

        int y = dimensionRowY();
        int selected = selectedDimensionIndex(dimensions);

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

    private void describeDimension(QueryDto.DimensionSummary dimension, boolean ender) {
        String head = ender ? "Your ender chest" : shortName(dimension.dimensionId());
        String detail = ender
                ? "Same items in every ender chest."
                : String.format("%,d containers indexed here.", dimension.containers());
        String action = viewing.equals(dimension.dimensionId())
                ? "Pinned. Click your current dimension to unpin."
                : "Click to pin the view here.";
        pendingHint = List.of(head, detail, action);
    }

    private int dimensionRowY() {
        return panelY + panelH - BOTTOM_H - 1;
    }

    private int selectedDimensionIndex(List<QueryDto.DimensionSummary> dimensions) {
        String wanted = viewing.isEmpty() ? currentDimensionId() : viewing;
        for (int i = 0; i < dimensions.size(); i++) {
            if (dimensions.get(i).dimensionId().equals(wanted)) return i;
        }
        return 0;
    }

    private List<QueryDto.DimensionSummary> shownDimensions() {
        boolean ender = ChestIndexConfig.get().enderChestView;
        if (shownDimensions != null && shownDimensionsFrom == status && shownDimensionsEnder == ender) {
            return shownDimensions;
        }

        List<QueryDto.DimensionSummary> shown = new ArrayList<>(status.dimensions().size());
        for (QueryDto.DimensionSummary dimension : status.dimensions()) {
            if (ender || !QueryDto.ENDER_CHEST.equals(dimension.dimensionId())) shown.add(dimension);
        }
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

    private static String dimensionGlyph(String dimensionId) {
        if (QueryDto.ENDER_CHEST.equals(dimensionId)) return "E";
        String name = shortName(dimensionId);
        return switch (name) {
            case "overworld" -> "O";
            case "the_nether" -> "N";
            case "the_end" -> "E";
            default -> name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        };
    }

    private void labelDimension(QueryDto.DimensionSummary dimension) {
        if (QueryDto.ENDER_CHEST.equals(dimension.dimensionId())) {
            setHoverLabel("your ender chest");
            return;
        }
        setHoverLabel(shortName(dimension.dimensionId()),
                String.format("%,d containers", dimension.containers()));
    }

    private static String dimensionName(QueryDto.DimensionSummary dimension) {
        return QueryDto.ENDER_CHEST.equals(dimension.dimensionId())
                ? "your ender chest" : shortName(dimension.dimensionId());
    }

    public static void forgetPinnedDimension() {
        viewing = "";
    }

    public static void forgetItemNames() {
        ICON_CACHE.clear();
        NAME_CACHE.clear();
    }

    private String currentDimensionId() {
        return minecraft.player == null ? ""
                : minecraft.player.level().dimension().identifier().toString();
    }

    private boolean clickDimension(int mouseX, int mouseY) {
        List<QueryDto.DimensionSummary> dimensions = shownDimensions();
        if (dimensions.size() < 2) return false;

        int y = dimensionRowY();
        if (mouseY < y || mouseY >= y + DIMENSION_H) return false;

        int count = dimensionsExpanded ? dimensions.size() : 1;
        for (int i = 0; i < count; i++) {
            int x = dimensionX(i);
            if (mouseX < x || mouseX >= x + DIMENSION_W) continue;
            if (!dimensionsExpanded) return true;

            String picked = dimensions.get(i).dimensionId();
            viewing = picked.equals(currentDimensionId()) ? "" : picked;
            back();
            refreshItems();
            return true;
        }
        return false;
    }

    private String unavailableMessage() {
        return switch (availability) {
            case CONNECTING -> "Asking the server...";
            case NOT_PERMITTED -> "This server does not allow searching.";
            default -> ChestIndexConfig.get().clientSideIndex && minecraft != null
                    && !minecraft.hasSingleplayerServer()
                    ? "Nothing indexed yet. Open some chests."
                    : "No index here yet.";
        };
    }

    private void drawWindow(Gfx gfx) {
        int searchY = panelY + TOP_H;
        int gridTop = searchY + SEARCH_H;
        int bottomY = gridTop + ROWS_H;

        drawBand(gfx, 0, TOP_H, panelY);
        drawSearchStrip(gfx, searchY);
        drawSlotBand(gfx, gridTop);
        drawBand(gfx, BOTTOM_V, BOTTOM_H, bottomY);
    }

    private void drawBand(Gfx gfx, int srcV, int height, int destY) {
        gfx.blit(CONTAINER_TEXTURE, panelX, destY, 0, srcV, SLOTS_W, height, SHEET, SHEET);
        gfx.blitStretched(CONTAINER_TEXTURE, panelX + SLOTS_W, destY, FILLER_U, srcV,
                SCROLL_COL, height, 1, height, SHEET, SHEET);
        gfx.blit(CONTAINER_TEXTURE, panelX + panelW - EDGE_W, destY,
                GUI_W - EDGE_W, srcV, EDGE_W, height, SHEET, SHEET);
    }

    private void drawSlotBand(Gfx gfx, int destY) {
        gfx.blit(CONTAINER_TEXTURE, panelX, destY, 0, ROWS_V, SLOTS_W, ROWS_H, SHEET, SHEET);
        gfx.blitStretched(CONTAINER_TEXTURE, panelX + SLOTS_W, destY, SLOT_FILLER_U, ROWS_V,
                SCROLL_COL, ROWS_H, 1, ROWS_H, SHEET, SHEET);
        gfx.blit(CONTAINER_TEXTURE, panelX + panelW - EDGE_W, destY,
                GUI_W - EDGE_W, ROWS_V, EDGE_W, ROWS_H, SHEET, SHEET);
    }

    private void drawSearchStrip(Gfx gfx, int searchY) {
        gfx.blit(CONTAINER_TEXTURE, panelX, searchY, 0, 30, EDGE_W, SEARCH_H, SHEET, SHEET);
        gfx.blitStretched(CONTAINER_TEXTURE, panelX + EDGE_W, searchY, SLOT_FILLER_U, 30,
                panelW - EDGE_W * 2, SEARCH_H, 1, SEARCH_H, SHEET, SHEET);
        gfx.blit(CONTAINER_TEXTURE, panelX + panelW - EDGE_W, searchY,
                GUI_W - EDGE_W, 30, EDGE_W, SEARCH_H, SHEET, SHEET);
        drawGroove(gfx, panelX + 7, searchY + 2, panelW - 14, 12);
    }

    private void fillFromTexture(Gfx gfx, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        gfx.blitStretched(CONTAINER_TEXTURE, x, y, SLOT_FILLER_U, ROWS_V, w, h, 1, h, SHEET, SHEET);
    }

    private void drawWell(Gfx gfx, int x, int y, int w, int h) {
        fillFromTexture(gfx, x, y, w, h);
        gfx.fill(x, y, x + w - 1, y + 1, GROOVE_DARK);
        gfx.fill(x, y, x + 1, y + h - 1, GROOVE_DARK);
        gfx.fill(x + w - 1, y, x + w, y + h, BEVEL_LIGHT);
        gfx.fill(x, y + h - 1, x + w, y + h, BEVEL_LIGHT);
    }

    private void drawGroove(Gfx gfx, int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, GROOVE_DARK);
        gfx.fill(x + 1, y + 1, x + w, y + h, BEVEL_LIGHT);
        gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, GROOVE);
    }

    private void drawGrid(Gfx gfx, int mouseX, int mouseY) {
        int hovered = slotAt(mouseX, mouseY);
        ChestIndexConfig.Detail detail = ChestIndexConfig.get().itemDetail();

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
            boolean hint = detail == ChestIndexConfig.Detail.SHIFT && !shift;
            setHoverLabel(
                    displayName(summary.itemId()),
                    String.format("%,d in %d", summary.totalCount(), summary.containerCount()),
                    hint ? "shift for detail" : "right-click to list");

            if (detail == ChestIndexConfig.Detail.ALWAYS
                    || (detail == ChestIndexConfig.Detail.SHIFT && shift)) {
                pendingTooltip = summary;
            }
        } else if (items.isEmpty()) {
            drawMessage(gfx, emptyGridMessage());
        }
    }

    private String emptyGridMessage() {
        if (!pending.isBlank()) return "No match";
        if (!viewing.isEmpty() && !viewing.equals(currentDimensionId())) {
            return "Nothing here in " + (QueryDto.ENDER_CHEST.equals(viewing)
                    ? "your ender chest" : shortName(viewing))
                    + " (pinned, click another to unpin)";
        }
        if (availability == ClientTracker.Availability.CLIENT_ONLY) {
            return "Open a chest to remember what is in it";
        }
        return "Nothing indexed yet";
    }

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

    private boolean shiftHeld() {
        return Keys.shiftHeld();
    }

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

    private void drawTextTooltip(Gfx gfx, List<String> lines, int mouseX, int mouseY) {
        int textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, font.width(line));
        int textHeight = lines.size() * 10 - 2;

        int textX = mouseX + 12 + textWidth + 4 > width ? mouseX - 16 - textWidth : mouseX + 12;
        int textY = Math.max(8, Math.min(mouseY - 12, height - textHeight - 8));

        drawTooltipFrame(gfx, textX, textY, textWidth, textHeight);

        for (int i = 0; i < lines.size(); i++) {
            gfx.text(font, Component.literal(lines.get(i)),
                    textX, textY + i * 10, i == 0 ? TEXT_MAIN : TEXT_MUTED);
        }
    }

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

    private static final int COUNT_WIDTH = 16;

    private void drawCount(Gfx gfx, int count, int slotX, int slotY) {
        if (count <= 1) return;
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

    private static String abbreviate(int count) {
        if (count < 1000) return Integer.toString(count);
        if (count < 1_000_000) return (count / 1000) + "k";
        if (count < 1_000_000_000) return (count / 1_000_000) + "m";
        return (count / 1_000_000_000) + "b";
    }

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

    private record MenuRow(String label, String value, boolean active, String help) {}

    private List<MenuRow> menuRows() {
        if (menuRows == null) menuRows = buildMenuRows();
        return menuRows;
    }

    private void menuRowsChanged() {
        menuRows = null;
    }

    private List<MenuRow> menuRows;

    private List<MenuRow> buildMenuRows() {
        String origin = switch (originIndex) {
            case 1 -> "built";
            case 2 -> "generated";
            default -> "any";
        };
        return List.of(
                new MenuRow("Sort by", sort.label, sort != Sort.COUNT,
                        "most, nearest, or A-Z"),
                new MenuRow("Origin", origin, originIndex != 0,
                        "player-built or world-generated"),
                new MenuRow("Inside shulkers", includeNested ? "counted" : "ignored", includeNested,
                        "count shulker contents in the chest"),
                new MenuRow("Machines", includeMachines ? "shown" : "hidden", includeMachines,
                        "hoppers, droppers, dispensers, crafters"),
                new MenuRow("Functional blocks", includeUtility ? "shown" : "hidden", includeUtility,
                        "furnaces, brewing stands, jukeboxes, lecterns, pots, bookshelves"),
                new MenuRow("Moving containers", includeEntities ? "shown" : "hidden", includeEntities,
                        "minecarts and chest boats, loaded chunks only"));
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
                for (int line = 0; line < 3; line++) {
                    gfx.fill(x + 3, y + 3 + line * 3, x + BUTTON_SIZE - 3, y + 4 + line * 3, ICON);
                }
            } else {
                gfx.text(font, "X", x + 3, y + 2, TEXT_MAIN);
            }
        }
    }

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
            hoverLabel = row.label();
            pendingHint = List.of(row.label() + SEP + row.value(), row.help());
            return;
        }
    }

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
            String value = row.value();
            gfx.text(font, Component.literal(value), x + MENU_W - 4 - font.width(value), rowY,
                    row.active() ? TEXT_MAIN : TEXT_MUTED);
        }
    }

    private void followSearchCursor() {
        if (search == null) return;
        boolean focused = search.isFocused();
        int cursor = search.getCursorPosition();
        if (focused == searchWasFocused && cursor == searchCursorWas) return;

        searchWasFocused = focused;
        searchCursorWas = cursor;
        refreshSuggestions();
    }

    private void refreshSuggestions() {
        if (search == null || !search.isFocused()) {
            suggestions = List.of();
            categoriesAsked = false;
            return;
        }
        String word = wordAtCursor();
        List<String> next = word.isEmpty()
                ? (categoriesAsked ? categoryPrefixes() : List.of())
                : SearchCategories.completionsFor(word);

        if (!next.equals(suggestions)) {
            suggestionIndex = 0;
            suggestionScroll = 0;
        }
        suggestions = next;
    }

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

    private String wordAtCursor() {
        String text = search.getValue();
        int cursor = Math.max(0, Math.min(search.getCursorPosition(), text.length()));
        int start = text.lastIndexOf(' ', cursor - 1) + 1;
        return text.substring(start, cursor);
    }

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

    private int suggestionAt(double mouseX, double mouseY) {
        if (suggestions.isEmpty()) return -1;
        int x = suggestionsX();
        int y = suggestionsY();
        if (mouseX < x || mouseX >= x + suggestionsW()) return -1;
        if (mouseY < y + 1) return -1;

        int row = (int) ((mouseY - y - 1) / SUGGESTION_H);
        if (row < 0 || row >= suggestionRowsShown()) return -1;
        return suggestionScroll + row;
    }

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

        int textRight = x + width - (scrollable ? 6 : 0);

        for (int row = 0; row < shown; row++) {
            int i = suggestionScroll + row;
            if (i >= suggestions.size()) break;

            int rowY = y + 1 + row * SUGGESTION_H;
            String text = suggestions.get(i);
            boolean hovered = mouseX >= x && mouseX < x + width
                    && mouseY >= rowY && mouseY < rowY + SUGGESTION_H;
            if (hovered || i == suggestionIndex) {
                gfx.fill(x + 1, rowY, x + width, rowY + SUGGESTION_H, SLOT_HOVER);
            }
            String label = truncate(text, textRight - x - 8);
            gfx.text(font, Component.literal(label), x + 4, rowY + 2, TEXT_MAIN);

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

            int distanceX = listLeft() + listWidth() - 4 - row.distanceWidth();
            gfx.text(font, Component.literal(row.distance()), distanceX, y, TEXT_MAIN);
            if (row.generated()) {
                gfx.text(font, Component.literal(GENERATED_TAG),
                        distanceX - GENERATED_GAP - font.width(GENERATED_TAG), y, TEXT_MUTED);
            }
            gfx.text(font, Component.literal(row.description()), textX, y, TEXT_MAIN);
        }
        setHoverLabel("Click to be guided", "right-click to go back");
    }

    private static final String GENERATED_TAG = "generated";
    private static final int GENERATED_GAP = 6;

    private record DetailRow(String description, String distance, int distanceWidth, boolean generated) {}

    private List<DetailRow> detailRows() {
        if (detailRows != null && detailRowsFrom == containers) return detailRows;

        int textWidth = listWidth() - 8;
        List<DetailRow> rows = new ArrayList<>(containers.size());
        for (QueryDto.ContainerHit hit : containers) {
            String distance = distanceOf(hit);
            int distanceWidth = font.width(distance);
            int tagWidth = hit.natural() ? font.width(GENERATED_TAG) + GENERATED_GAP : 0;
            rows.add(new DetailRow(truncate(describe(hit), textWidth - distanceWidth - tagWidth - 4),
                    distance, distanceWidth, hit.natural()));
        }

        detailRowsFrom = containers;
        detailRows = rows;
        return rows;
    }

    private List<DetailRow> detailRows;
    private List<QueryDto.ContainerHit> detailRowsFrom;

    private String describe(QueryDto.ContainerHit hit) {
        StringBuilder line = new StringBuilder();
        line.append(hit.matchedCount()).append("x ").append(shortName(hit.typeId()));
        if (hit.nested()) line.append(" *");
        if (!hit.contentsKnown()) line.append(" ?");
        line.append("  ").append(BlockKey.toString(hit.pos()));
        return line.toString();
    }

    private static String distanceOf(QueryDto.ContainerHit hit) {
        return String.format("%.0fm", Math.sqrt(hit.distanceSq()));
    }

    private static ItemStack iconFor(String itemId) {
        return ICON_CACHE.computeIfAbsent(itemId, id -> {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    private static String displayName(String itemId) {
        return NAME_CACHE.computeIfAbsent(itemId, id -> {
            ItemStack stack = iconFor(id);
            return stack.isEmpty() ? shortName(id) : stack.getHoverName().getString();
        });
    }

    private static final String SEP = "  -  ";

    private int titleWidth() {
        return buttonX(0) - (panelX + 8) - 4;
    }

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
        hoverLabel = truncate(head, maxWidth);
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static String shortName(String registryId) {
        int colon = registryId.indexOf(':');
        return colon < 0 ? registryId : registryId.substring(colon + 1);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        if (event.button() == Keys.MOUSE_LEFT && menuOpen) {
            if (clickMenuRow(mouseX, mouseY)) return true;
            if (!overToolbar(mouseX, mouseY)) {
                menuOpen = false;
                return true;
            }
        }

        if (event.button() == Keys.MOUSE_LEFT && search != null
                && mouseX >= search.getX() - 3 && mouseX < search.getX() + search.getWidth() + 3
                && mouseY >= search.getY() && mouseY < search.getY() + 12) {
            categoriesAsked = true;
            refreshSuggestions();
        }

        if (event.button() == Keys.MOUSE_LEFT) {
            int row = suggestionAt(mouseX, mouseY);
            if (row >= 0) {
                suggestionIndex = row;
                applySuggestion(suggestions.get(row));
                return true;
            }
        }

        if (event.button() == Keys.MOUSE_LEFT && clickButton(mouseX, mouseY)) return true;
        if (event.button() == Keys.MOUSE_LEFT && clickDimension(mouseX, mouseY)) return true;
        if (!canQuery()) return super.mouseClicked(event, doubleClick);

        if (event.button() == Keys.MOUSE_RIGHT) {
            if (selectedItemId != null) {
                back();
                if (itemsStale) refreshItems(false);
                return true;
            }
            int index = slotAt(mouseX, mouseY);
            if (index >= 0) {
                selectItem(items.get(index).itemId());
                return true;
            }
        }

        if (event.button() == Keys.MOUSE_LEFT) {
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

    private boolean clickMenuRow(int mouseX, int mouseY) {
        int x = menuX();
        int y = menuY();
        if (mouseX < x || mouseX >= x + MENU_W || mouseY < y || mouseY >= y + menuH()) return false;

        int row = (mouseY - (y + 1)) / MENU_ROW_H;
        switch (row) {
            case 0 -> {
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
        ContainerHighlight.get().selectHits(List.of(hit),
                minecraft.player.level().dimension().identifier().toString(),
                displayName(selectedItemId));
        ContainerHighlight.get().searchingFor(selectedItemId);
        onClose();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        keyPressedHere = true;
        if (!suggestions.isEmpty()) {
            switch (event.key()) {
                case Keys.TAB -> {
                    applySuggestion(suggestions.get(
                            Math.max(0, Math.min(suggestionIndex, suggestions.size() - 1))));
                    return true;
                }
                case Keys.DOWN -> {
                    suggestionIndex = (suggestionIndex + 1) % suggestions.size();
                    scrollToSuggestion();
                    return true;
                }
                case Keys.UP -> {
                    suggestionIndex = (suggestionIndex + suggestions.size() - 1) % suggestions.size();
                    scrollToSuggestion();
                    return true;
                }
                case Keys.ESCAPE -> {
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

        if (!suggestions.isEmpty() && overSuggestions(mouseX, mouseY)) {
            int maxScroll = Math.max(0, suggestions.size() - suggestionRowsShown());
            suggestionScroll = Math.max(0, Math.min(maxScroll, suggestionScroll - step));
            return true;
        }
        if (selectedItemId == null) {
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - step));
        } else {
            detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll - step));
        }
        return true;
    }

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
