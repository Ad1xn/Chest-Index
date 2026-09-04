package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.ClientTracker;
import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.platform.Gfx;
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
    private static final int TEXT_DARK = 0xFF404040;
    private static final int TEXT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_HOVER = 0x80FFFFFF;
    private static final int ROW_HOVER = 0x40000000;
    private static final int BUTTON_ON = 0xFF6A9A4A;

    private static final int MAX_ITEMS = 900;
    private static final int MAX_CONTAINERS = 64;
    private static final int DETAIL_ROW = 11;

    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

    private enum Sort {
        COUNT("Most"), NEAREST("Nearest"), NAME("A-Z");

        final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    private EditBox search;
    private String pending = "";

    private List<QueryDto.ItemSummary> items = List.of();
    private List<QueryDto.ContainerHit> containers = List.of();
    private String selectedItemId;
    private int scrollRow;
    private boolean draggingScrollbar;

    private ClientTracker.Availability availability = ClientTracker.Availability.NONE;

    private int newestItemsReply;
    private int newestContainersReply;

    private boolean containersPending;

    private Sort sort = Sort.COUNT;
    private boolean includeNested = true;
    private boolean includeMachines;
    private int originIndex;

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
        search.setResponder(value -> {
            pending = value;
            refreshItems();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        availability = ClientTracker.availability();
        refreshItems();
    }

    private QueryDto.Filters filters() {
        return new QueryDto.Filters(includeNested, includeMachines, originIndex);
    }

    private void refreshItems() {
        if (!canQuery()) return;
        ClientTracker.summarise(pending, filters(), MAX_ITEMS).thenAccept(response ->
                minecraft.execute(() -> {
                    if (response.requestId() <= newestItemsReply) return;
                    newestItemsReply = response.requestId();
                    items = sorted(response.items());
                    scrollRow = 0;
                    back();
                }));
    }

    private boolean canQuery() {
        return availability == ClientTracker.Availability.LOCAL
                || availability == ClientTracker.Availability.SERVER;
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
        ClientTracker.containers(itemId, filters(), MAX_CONTAINERS).thenAccept(response ->
                minecraft.execute(() -> {
                    if (!itemId.equals(selectedItemId)) return;
                    if (response.requestId() <= newestContainersReply) return;
                    newestContainersReply = response.requestId();
                    containers = response.hits();
                    containersPending = false;
                }));
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

    private int maxScrollRow() {
        return Math.max(0, (items.size() + COLS - 1) / COLS - ROWS);
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
            refreshItems();
        }

        if (!canQuery()) {
            gfx.text(font, Component.literal(unavailableMessage()), gridX(), gridY() + 4, TEXT_DARK);
            return;
        }

        if (selectedItemId == null) {
            drawGrid(gfx, mouseX, mouseY);
            drawScrollbar(gfx);
        } else {
            drawLocations(gfx, mouseX, mouseY);
        }

        String title = hoverLabel != null ? hoverLabel
                : selectedItemId != null ? displayName(selectedItemId) : "Chest Tracker";
        int available = buttonX(0) - (panelX + 8) - 4;
        gfx.text(font, Component.literal(truncate(title, available)), panelX + 8, panelY + 6, TEXT_DARK);
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
                    gridX(), gridY() + 4, TEXT_DARK);
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

    private void drawScrollbar(Gfx gfx) {
        int x = scrollbarX();
        int y = gridY() - 1;
        int height = ROWS * SLOT;
        drawGroove(gfx, x, y, 12, height);

        int maxScroll = maxScrollRow();
        int thumbHeight = maxScroll == 0 ? height - 2
                : Math.max(15, (height - 2) * ROWS / ((items.size() + COLS - 1) / COLS));
        int travel = height - 2 - thumbHeight;
        int thumbY = y + 1 + (maxScroll == 0 ? 0 : travel * scrollRow / maxScroll);

        fillFromTexture(gfx, x + 1, thumbY, 10, thumbHeight);
        gfx.fill(x + 1, thumbY, x + 11, thumbY + 1, BEVEL_LIGHT);
        gfx.fill(x + 1, thumbY, x + 2, thumbY + thumbHeight, BEVEL_LIGHT);
        gfx.fill(x + 1, thumbY + thumbHeight - 1, x + 11, thumbY + thumbHeight, BEVEL_DARK);
        gfx.fill(x + 10, thumbY, x + 11, thumbY + thumbHeight, BEVEL_DARK);
    }

    private record Toolbar(String glyph, String label, boolean active) {}

    private List<Toolbar> toolbar() {
        String origin = switch (originIndex) {
            case 1 -> "Player-placed only";
            case 2 -> "Natural only";
            default -> "All origins";
        };
        return List.of(
                new Toolbar("S", "Sort: " + sort.label, sort != Sort.COUNT),
                new Toolbar("O", origin, originIndex != 0),
                new Toolbar("N", includeNested ? "Counting nested items" : "Ignoring nested items", includeNested),
                new Toolbar("M", includeMachines ? "Showing machines" : "Hiding machines", includeMachines),
                new Toolbar("X", "Close", false));
    }

    private int buttonX(int index) {
        return panelX + panelW - 8 - (5 - index) * 13;
    }

    private void drawButtons(Gfx gfx, int mouseX, int mouseY) {
        List<Toolbar> buttons = toolbar();
        for (int i = 0; i < buttons.size(); i++) {
            Toolbar button = buttons.get(i);
            int x = buttonX(i);
            int y = panelY + 3;
            boolean hovered = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;

            gfx.fill(x, y, x + 12, y + 12, BEVEL_DARK);
            if (button.active()) {
                gfx.fill(x, y, x + 11, y + 11, BUTTON_ON);
            } else {
                fillFromTexture(gfx, x, y, 11, 11);
            }
            gfx.fill(x, y, x + 11, y + 1, BEVEL_LIGHT);
            gfx.fill(x, y, x + 1, y + 11, BEVEL_LIGHT);
            if (hovered) {
                gfx.fill(x + 1, y + 1, x + 11, y + 11, SLOT_HOVER);
                hoverLabel = button.label();
            }
            gfx.text(font, button.glyph(), x + 3, y + 2, TEXT_DARK);
        }
    }

    private void drawLocations(Gfx gfx, int mouseX, int mouseY) {
        int x = gridX();
        int y = gridY();
        int bottom = gridY() + ROWS * SLOT;

        if (containers.isEmpty()) {
            gfx.text(font, Component.literal(containersPending ? "Looking..." : "Nothing holds that."),
                    x, y, TEXT_DARK);
            return;
        }

        for (QueryDto.ContainerHit hit : containers) {
            if (y + DETAIL_ROW > bottom) break;
            boolean hovered = mouseX >= x && mouseX <= panelX + panelW - 10
                    && mouseY >= y && mouseY < y + DETAIL_ROW;
            if (hovered) gfx.fill(x - 1, y - 1, panelX + panelW - 10, y + DETAIL_ROW - 1, ROW_HOVER);
            gfx.text(font, Component.literal(describe(hit)), x, y, TEXT_DARK);
            y += DETAIL_ROW;
        }
        hoverLabel = "Click to be guided  -  right-click to go back";
    }

    private String describe(QueryDto.ContainerHit hit) {
        StringBuilder line = new StringBuilder();
        line.append(hit.matchedCount()).append("x ")
                .append(shortName(hit.typeId()))
                .append("  ").append(BlockKey.toString(hit.pos()))
                .append(String.format("  %.0fm", Math.sqrt(hit.distanceSq())));
        if (hit.nested()) line.append(" *");
        if (!hit.contentsKnown()) line.append(" ?");
        return line.toString();
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

        if (event.button() == 0 && clickButton(mouseX, mouseY)) return true;
        if (!canQuery()) return super.mouseClicked(event, doubleClick);

        if (event.button() == 1 && selectedItemId != null) {
            back();
            return true;
        }

        if (event.button() == 0) {
            if (selectedItemId == null) {
                if (mouseX >= scrollbarX() && mouseX < scrollbarX() + 12
                        && mouseY >= gridY() - 1 && mouseY < gridY() - 1 + ROWS * SLOT) {
                    draggingScrollbar = true;
                    dragScrollbar(mouseY);
                    return true;
                }
                int index = slotAt(mouseX, mouseY);
                if (index >= 0) {
                    selectItem(items.get(index).itemId());
                    return true;
                }
            } else if (!containers.isEmpty()) {
                int row = (mouseY - gridY()) / DETAIL_ROW;
                if (row >= 0 && row < containers.size() && mouseX >= gridX()) {
                    guideTo(containers.get(row));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean clickButton(int mouseX, int mouseY) {
        int y = panelY + 3;
        if (mouseY < y || mouseY >= y + 12) return false;

        for (int i = 0; i < 5; i++) {
            int x = buttonX(i);
            if (mouseX < x || mouseX >= x + 12) continue;

            switch (i) {
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
                default -> onClose();
            }
            return true;
        }
        return false;
    }

    private void dragScrollbar(int mouseY) {
        int height = ROWS * SLOT;
        int maxScroll = maxScrollRow();
        if (maxScroll == 0) return;
        double fraction = (mouseY - (gridY() - 1)) / (double) height;
        scrollRow = Math.max(0, Math.min(maxScroll, (int) Math.round(fraction * maxScroll)));
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
        if (selectedItemId == null) {
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) Math.signum(deltaY)));
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
