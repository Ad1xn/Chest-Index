package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.ClientTracker;
import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.core.util.BlockKey;
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

public final class ChestTrackerScreen extends Screen {

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

    private static final int BUTTON_SIZE = 12;
    private static final int TOOLBAR_BUTTONS = 2;
    private static final int MENU_W = 132;
    private static final int MENU_ROW_H = 12;

    private static final long AUTO_REFRESH_MIN_MS = 400;

    private static final long REFUSED_RETRY_MS = 2000;

    private static final int MAX_CONTAINERS = 64;
    private static final int DETAIL_ROW = 11;

    private static final int LIST_PAD = 3;

    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

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

    private int detailScroll;

    private boolean draggingScrollbar;
    private boolean menuOpen;

    private ClientTracker.Availability availability = ClientTracker.Availability.NONE;

    private int newestItemsReply;
    private int newestContainersReply;

    private boolean containersPending;

    private long lastChangeToken;
    private long lastAutoRefresh;

    private boolean itemsStale;

    private Sort sort = Sort.parse(ChestTrackerConfig.get().sortMode);
    private boolean includeNested = ChestTrackerConfig.get().includeNested;
    private boolean includeMachines = ChestTrackerConfig.get().showMachines;
    private int originIndex = Math.max(0, Math.min(2, ChestTrackerConfig.get().originFilter));

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private String hoverLabel;

    public ChestTrackerScreen() {
        super(Component.literal("Chest Tracker"));
    }

    @Override
    protected void init() {
        panelW = GUI_W + SCROLL_COL;
        panelH = TOP_H + SEARCH_H + ROWS_H + BOTTOM_H;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        search = new EditBox(font, panelX + 8, panelY + TOP_H + 3, SLOTS_W - 10, 12,
                Component.literal("Search"));
        search.setMaxLength(64);
        search.setHint(Component.literal("Search"));
        search.setValue(pending);
        search.setResponder(value -> {
            pending = value;
            refreshItems();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        availability = ClientTracker.availability();
        lastChangeToken = ClientTracker.changeToken();
        ClientTracker.setWatching(true);
        refreshItems();
    }

    @Override
    public void removed() {
        ClientTracker.setWatching(false);
        rememberPreferences();
        super.removed();
    }

    private void rememberPreferences() {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        boolean changed = config.includeNested != includeNested
                || config.showMachines != includeMachines
                || config.originFilter != originIndex
                || !config.sortMode.equals(sort.name())
                || !config.searchText.equals(pending);
        if (!changed) return;

        config.includeNested = includeNested;
        config.showMachines = includeMachines;
        config.originFilter = originIndex;
        config.sortMode = sort.name();
        config.searchText = pending;
        config.save();
    }

    private QueryDto.Filters filters() {
        return new QueryDto.Filters(includeNested, includeMachines, originIndex);
    }

    private void refreshItems() {
        refreshItems(true);
    }

    private void refreshItems(boolean resetView) {
        if (!mayAttempt()) return;
        ClientTracker.summarise(pending, filters(), ChestTrackerConfig.get().resultLimit()).thenAccept(response ->
                minecraft.execute(() -> {
                    if (response.requestId() <= newestItemsReply) return;
                    newestItemsReply = response.requestId();
                    items = sorted(response.items());
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
        ClientTracker.containers(itemId, filters(), MAX_CONTAINERS).thenAccept(response ->
                minecraft.execute(() -> {
                    if (!itemId.equals(selectedItemId)) return;
                    if (response.requestId() <= newestContainersReply) return;
                    newestContainersReply = response.requestId();
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
        if (now - lastAutoRefresh < AUTO_REFRESH_MIN_MS) return;
        lastChangeToken = token;
        lastAutoRefresh = now;

        if (selectedItemId == null) {
            refreshItems(false);
        } else {
            refreshContainers();
            itemsStale = true;
        }
    }

    private boolean canQuery() {
        return availability == ClientTracker.Availability.LOCAL
                || availability == ClientTracker.Availability.SERVER;
    }

    private boolean mayAttempt() {
        return canQuery() || availability == ClientTracker.Availability.NOT_PERMITTED;
    }

    private List<QueryDto.ItemSummary> sorted(List<QueryDto.ItemSummary> source) {
        List<QueryDto.ItemSummary> copy = new ArrayList<>(source);
        switch (sort) {
            case NEAREST -> copy.sort(Comparator.comparingDouble(QueryDto.ItemSummary::nearestDistSq));
            case NAME -> copy.sort(Comparator.comparing(entry -> displayName(entry.itemId())));
            case COUNT -> {  }
        }
        return copy;
    }

    private void selectItem(String itemId) {
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
        hoverLabel = null;
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
            gfx.text(font, Component.literal(unavailableMessage()), gridX(), gridY() + 4, TEXT_MAIN);
            return;
        }

        if (selectedItemId == null) {
            drawGrid(gfx, mouseX, mouseY);
            drawScrollbar(gfx, scrollRow, maxScrollRow(), gridRows(), ROWS);
        } else {
            drawLocations(gfx, mouseX, mouseY);
            drawScrollbar(gfx, detailScroll, maxDetailScroll(),
                    containers.size(), listRowsVisible());
        }

        String title = hoverLabel != null ? hoverLabel
                : selectedItemId != null ? displayName(selectedItemId) : "Chest Tracker";
        int available = buttonX(0) - (panelX + 8) - 4;
        gfx.text(font, Component.literal(truncate(title, available)), panelX + 8, panelY + 6, TEXT_MAIN);

        drawMenu(gfx, mouseX, mouseY);
    }

    private String unavailableMessage() {
        return switch (availability) {
            case CONNECTING -> "Asking the server...";
            case NOT_PERMITTED -> "This server does not allow searching.";
            default -> "No index here yet.";
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
        for (int i = 0; i < SCROLL_COL; i++) {
            gfx.blit(CONTAINER_TEXTURE, panelX + SLOTS_W + i, destY, FILLER_U, srcV, 1, height, SHEET, SHEET);
        }
        gfx.blit(CONTAINER_TEXTURE, panelX + panelW - EDGE_W, destY,
                GUI_W - EDGE_W, srcV, EDGE_W, height, SHEET, SHEET);
    }

    private void drawSlotBand(Gfx gfx, int destY) {
        gfx.blit(CONTAINER_TEXTURE, panelX, destY, 0, ROWS_V, SLOTS_W, ROWS_H, SHEET, SHEET);
        for (int i = 0; i < SCROLL_COL; i++) {
            gfx.blit(CONTAINER_TEXTURE, panelX + SLOTS_W + i, destY,
                    SLOT_FILLER_U, ROWS_V, 1, ROWS_H, SHEET, SHEET);
        }
        gfx.blit(CONTAINER_TEXTURE, panelX + panelW - EDGE_W, destY,
                GUI_W - EDGE_W, ROWS_V, EDGE_W, ROWS_H, SHEET, SHEET);
    }

    private void drawSearchStrip(Gfx gfx, int searchY) {
        gfx.blit(CONTAINER_TEXTURE, panelX, searchY, 0, 30, EDGE_W, SEARCH_H, SHEET, SHEET);
        for (int x = panelX + EDGE_W; x < panelX + panelW - EDGE_W; x++) {
            gfx.blit(CONTAINER_TEXTURE, x, searchY, SLOT_FILLER_U, 30, 1, SEARCH_H, SHEET, SHEET);
        }
        gfx.blit(CONTAINER_TEXTURE, panelX + panelW - EDGE_W, searchY,
                GUI_W - EDGE_W, 30, EDGE_W, SEARCH_H, SHEET, SHEET);
        drawGroove(gfx, panelX + 7, searchY + 2, panelW - 14, 12);
    }

    private void fillFromTexture(Gfx gfx, int x, int y, int w, int h) {
        for (int i = 0; i < w; i++) {
            gfx.blit(CONTAINER_TEXTURE, x + i, y, SLOT_FILLER_U, ROWS_V, 1, h, SHEET, SHEET);
        }
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
            hoverLabel = String.format("%s  %,d in %d",
                    displayName(summary.itemId()), summary.totalCount(), summary.containerCount());
        } else if (items.isEmpty()) {
            gfx.text(font, Component.literal(pending.isBlank() ? "Nothing indexed yet" : "No match"),
                    gridX(), gridY() + 4, TEXT_MAIN);
        }
    }

    private void drawCount(Gfx gfx, int count, int slotX, int slotY) {
        if (count <= 1) return;
        String label = abbreviate(count);
        gfx.text(font, label, slotX + 17 - font.width(label), slotY + 9, TEXT_LIGHT);
    }

    private static String abbreviate(int count) {
        if (count < 1000) return Integer.toString(count);
        if (count < 1_000_000) return (count / 1000) + "k";
        return (count / 1_000_000) + "m";
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

    private record MenuRow(String label, String value, boolean active) {}

    private List<MenuRow> menuRows() {
        String origin = switch (originIndex) {
            case 1 -> "player-placed" ;
            case 2 -> "natural";
            default -> "any";
        };
        return List.of(
                new MenuRow("Sort by", sort.label, sort != Sort.COUNT),
                new MenuRow("Origin", origin, originIndex != 0),
                new MenuRow("Inside shulkers", includeNested ? "counted" : "ignored", includeNested),
                new MenuRow("Machines", includeMachines ? "shown" : "hidden", includeMachines));
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

    private void drawLocations(Gfx gfx, int mouseX, int mouseY) {
        drawWell(gfx, listLeft(), listTop(), listWidth(), listHeight());

        int textX = listLeft() + 4;
        int textWidth = listWidth() - 8;

        if (containers.isEmpty()) {
            gfx.text(font, Component.literal(containersPending ? "Looking..." : "Nothing holds that."),
                    textX, listRowY(0), TEXT_MAIN);
            return;
        }

        int hovered = rowAt(mouseX, mouseY);
        int last = Math.min(containers.size(), detailScroll + listRowsVisible());
        for (int index = detailScroll; index < last; index++) {
            QueryDto.ContainerHit hit = containers.get(index);
            int y = listRowY(index - detailScroll);
            if (index == hovered) {
                gfx.fill(listLeft() + 1, y - 1, listLeft() + listWidth() - 1,
                        y + DETAIL_ROW - 1, ROW_HOVER);
            }

            String distance = distanceOf(hit);
            int distanceWidth = font.width(distance);
            gfx.text(font, Component.literal(distance),
                    listLeft() + listWidth() - 4 - distanceWidth, y, TEXT_MAIN);
            gfx.text(font, Component.literal(
                            truncate(describe(hit), textWidth - distanceWidth - 4)),
                    textX, y, TEXT_MAIN);
        }
        hoverLabel = "Click to be guided  -  right-click to go back";
    }

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
        ItemStack stack = iconFor(itemId);
        return stack.isEmpty() ? shortName(itemId) : stack.getHoverName().getString();
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        StringBuilder trimmed = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(trimmed.toString() + c + "...") > maxWidth) break;
            trimmed.append(c);
        }
        return trimmed + "...";
    }

    private static String shortName(String registryId) {
        int colon = registryId.indexOf(':');
        return colon < 0 ? registryId : registryId.substring(colon + 1);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        if (event.button() == 0 && menuOpen) {
            if (clickMenuRow(mouseX, mouseY)) return true;
            if (!overToolbar(mouseX, mouseY)) {
                menuOpen = false;
                return true;
            }
        }

        if (event.button() == 0 && clickButton(mouseX, mouseY)) return true;
        if (!canQuery()) return super.mouseClicked(event, doubleClick);

        if (event.button() == 1 && selectedItemId != null) {
            back();
            if (itemsStale) refreshItems(false);
            return true;
        }

        if (event.button() == 0) {
            if (mouseX >= scrollbarX() && mouseX < scrollbarX() + 12
                    && mouseY >= gridY() - 1 && mouseY < gridY() - 1 + ROWS * SLOT) {
                draggingScrollbar = true;
                dragScrollbar(mouseY);
                return true;
            }
            if (selectedItemId == null) {
                int index = slotAt(mouseX, mouseY);
                if (index >= 0) {
                    selectItem(items.get(index).itemId());
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
            default -> {
                return false;
            }
        }
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
        ContainerHighlight.get().select(hit.pos(),
                minecraft.player.level().dimension().identifier().toString(),
                displayName(selectedItemId));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int step = (int) Math.signum(deltaY);
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
