package dev.adrian.chestindex.client.ui;

import dev.adrian.chestindex.ChestIndex;
import dev.adrian.chestindex.client.index.ClientObserver;
import dev.adrian.chestindex.client.index.IndexStore;
import dev.adrian.chestindex.client.platform.Gfx;
import dev.adrian.chestindex.server.Trackers;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

public final class IndexScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int CONTENT_W = 258;
    private static final int SIDE_PAD = 8;
    private static final int PAD = 2;
    private static final int BUTTON_SIZE = 12;
    private static final int SCROLLBAR_W = 8;

    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = 7;

    private static final int PANEL_W = (Panel.EDGE_W + SIDE_PAD) * 2 + CONTENT_W;

    private static final int DANGER = 0xFFB03030;

    private final Screen parent;

    private int panelX;
    private int panelY;
    private int visibleRows;
    private int scroll;

    private List<IndexStore.Location> locations = List.of();

    private int armed = -1;

    private String problem;

    private String status;

    private String hoverTitle;

    private List<String> pendingTooltip;

    private int tooltipAvoidTop;
    private int tooltipAvoidBottom;

    private static final int TOOLTIP_W = 220;

    public IndexScreen(Screen parent) {
        super(Component.literal("Stored indexes"));
        this.parent = parent;
    }

    private static final int CHROME_H =
            Panel.TOP_H + PAD + 2 + PAD + Panel.BOTTOM_H;

    @Override
    protected void init() {
        int room = height - 40 - CHROME_H;
        visibleRows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, room / ROW_H));

        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH()) / 2;

        if (!loaded) {
            loaded = true;
            try {
                locations = IndexStore.all();
                problem = null;
            } catch (RuntimeException unreadable) {
                locations = List.of();
                problem = "Could not read the index folders.";
                ChestIndex.LOG.warn("Could not list stored indexes: {}", unreadable.toString());
            }
        }
        scroll = Math.min(scroll, maxScroll());
    }

    private boolean loaded;

    private int panelH() {
        return CHROME_H + visibleRows * ROW_H;
    }

    private int contentX() {
        return panelX + Panel.EDGE_W + SIDE_PAD;
    }

    private int contentTop() {
        return panelY + Panel.TOP_H + PAD;
    }

    private int contentH() {
        return visibleRows * ROW_H + 2;
    }

    private boolean overflows() {
        return locations.size() > visibleRows;
    }

    private int maxScroll() {
        return Math.max(0, locations.size() - visibleRows);
    }

    private int rowsX() {
        return contentX() + 1;
    }

    private int rowsW() {
        return CONTENT_W - 2 - (overflows() ? SCROLLBAR_W + 1 : 0);
    }

    private int rowY(int index) {
        return contentTop() + 1 + index * ROW_H;
    }

    private int scrollbarX() {
        return contentX() + CONTENT_W - 1 - SCROLLBAR_W;
    }

    private int closeX() {
        return panelX + PANEL_W - SIDE_PAD - BUTTON_SIZE;
    }

    private int titleWidth() {
        return closeX() - (panelX + SIDE_PAD) - 4;
    }

    private void draw(Gfx gfx, int mouseX, int mouseY) {
        hoverTitle = null;
        pendingTooltip = null;

        drawCloseButton(gfx, mouseX, mouseY);
        Panel.well(gfx, contentX(), contentTop(), CONTENT_W, contentH());
        drawRows(gfx, mouseX, mouseY);

        if (overflows()) {
            Panel.scrollbar(gfx, scrollbarX(), contentTop() + 1, SCROLLBAR_W, contentH() - 2,
                    scroll, maxScroll(), locations.size(), visibleRows);
        }

        String title = hoverTitle != null ? hoverTitle : "Stored indexes";
        gfx.text(font, Component.literal(truncate(title, titleWidth())),
                panelX + SIDE_PAD, panelY + 6, Panel.TEXT_MAIN);

        drawFooter(gfx);

        if (pendingTooltip != null) {
            Panel.tooltip(gfx, font, pendingTooltip, mouseX, width, height,
                    tooltipAvoidTop, tooltipAvoidBottom);
        }
    }

    private void drawCloseButton(Gfx gfx, int mouseX, int mouseY) {
        int x = closeX();
        int y = panelY + 3;
        boolean hovered = within(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE);
        Panel.raised(gfx, x, y, BUTTON_SIZE, BUTTON_SIZE, false);
        if (hovered) {
            gfx.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, Panel.HOVER);
            hoverTitle = "Back to settings";
        }
        gfx.text(font, "X", x + 3, y + 2, Panel.TEXT_MAIN);
    }

    private void drawRows(Gfx gfx, int mouseX, int mouseY) {
        if (locations.isEmpty()) {
            String empty = "Nothing has been indexed yet.";
            gfx.text(font, Component.literal(empty),
                    contentX() + (CONTENT_W - font.width(empty)) / 2,
                    contentTop() + contentH() / 2 - 4, Panel.TEXT_MUTED);
            return;
        }

        int left = rowsX();
        int right = left + rowsW();
        int hovered = rowAt(mouseX, mouseY);

        if (armed >= 0 && hovered != armed) armed = -1;

        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= locations.size()) break;

            IndexStore.Location location = locations.get(index);
            int y = rowY(i);
            if (i > 0) gfx.fill(left + 2, y, right - 2, y + 1, 0x18000000);
            if (index == hovered) {
                gfx.fill(left, y + 1, right, y + ROW_H, 0x30FFFFFF);
                hoverTitle = location.name();
                pendingTooltip = describe(location);
                tooltipAvoidTop = y;
                tooltipAvoidBottom = y + ROW_H;
            }

            int buttonX = deleteX(right);
            int recheckX = recheckX(location, right);
            gfx.text(font, Component.literal(truncate(location.name(), recheckX - left - 8)),
                    left + 4, y + 4, Panel.TEXT_MAIN);
            gfx.text(font, Component.literal(truncate(location.describe(), recheckX - left - 8)),
                    left + 4, y + 14, location.active() ? Panel.ON : Panel.TEXT_MUTED);

            int buttonY = y + (ROW_H - BUTTON_SIZE) / 2;
            drawRecheck(gfx, location, recheckX, buttonY, mouseX, mouseY);
            if (!hosted(location)) {
                boolean primed = index == armed;
                Panel.raised(gfx, buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE, false);
                if (primed) {
                    gfx.fill(buttonX, buttonY, buttonX + BUTTON_SIZE - 1, buttonY + BUTTON_SIZE - 1, DANGER);
                }
                bin(gfx, buttonX + 3, buttonY + 3, primed ? Panel.TEXT_MAIN : Panel.ICON);
            }
        }
    }

    private static boolean hosted(IndexStore.Location location) {
        return location.kind() == IndexStore.Kind.HOSTED;
    }

    private static int deleteX(int right) {
        return right - 4 - BUTTON_SIZE;
    }

    private static int recheckX(IndexStore.Location location, int right) {
        return hosted(location) ? deleteX(right) : deleteX(right) - 3 - BUTTON_SIZE;
    }

    private String recheckBlocker(IndexStore.Location location) {
        return switch (location.kind()) {
            case WORLD -> location.active() ? null : "Open this world to re-check it.";
            case SERVER -> location.active() ? null : "Join this server to re-check it.";
            case HOSTED -> IndexStore.mayRecheckHosted() ? null : "Only operators can re-check the server's index.";
        };
    }

    private void drawRecheck(Gfx gfx, IndexStore.Location location, int x, int y, int mouseX, int mouseY) {
        String blocker = recheckBlocker(location);
        Panel.raised(gfx, x, y, BUTTON_SIZE, BUTTON_SIZE, false);
        if (within(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE)) {
            if (blocker == null) gfx.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, Panel.HOVER);
            hoverTitle = "Re-check";
            pendingTooltip = new ArrayList<>(List.of("Re-check",
                    "Reads every indexed chunk again",
                    "without a full rescan."));
            if (blocker != null) {
                pendingTooltip.add("");
                pendingTooltip.add(blocker);
            }
        }
        recheckIcon(gfx, x + 3, y + 3, blocker == null ? Panel.ICON : Panel.TEXT_MUTED);
    }

    private static final String[] RECHECK_ICON = {
            ".###.#",
            "#...##",
            "#..###",
            "#.....",
            "#....#",
            ".####."
    };

    private void recheckIcon(Gfx gfx, int x, int y, int colour) {
        for (int row = 0; row < RECHECK_ICON.length; row++) {
            String line = RECHECK_ICON[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == '#') gfx.fill(x + col, y + row, x + col + 1, y + row + 1, colour);
            }
        }
    }

    private void recheck(IndexStore.Location location) {
        problem = recheckBlocker(location);
        status = null;
        if (problem != null) return;

        switch (location.kind()) {
            case WORLD -> {
                var server = minecraft.getSingleplayerServer();
                if (server == null) {
                    problem = "Open this world to re-check it.";
                    return;
                }
                status = "Starting the re-check...";
                server.execute(() -> {
                    Trackers.Recheck result = Trackers.recheck();
                    minecraft.execute(() -> {
                        if (result.started()) {
                            status = "Re-checking " + String.format("%,d", result.chunks()) + " chunks in the background.";
                        } else {
                            status = null;
                            problem = result.problem();
                        }
                    });
                });
            }
            case SERVER -> {
                int chunks = ClientObserver.recheckLoaded();
                if (chunks < 0) {
                    problem = "Join this server to re-check it.";
                } else {
                    status = "Re-checked the " + chunks + " chunks around you.";
                }
            }
            case HOSTED -> {
                minecraft.getConnection().sendCommand("chestindex recheck");
                status = "Asked the server - its answer is in chat.";
            }
        }
    }

    private List<String> describe(IndexStore.Location location) {
        List<String> lines = new ArrayList<>();
        lines.add(location.name());
        lines.add(location.describe());
        if (location.directory() != null) {
            lines.add("");
            lines.addAll(wrapPath(location.directory().toString()));
        }
        return lines;
    }

    private List<String> wrapPath(String path) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String part : path.split("(?<=/)")) {
            if (line.length() > 0 && font.width(line + part) > TOOLTIP_W) {
                lines.add(line.toString());
                line.setLength(0);
            }
            while (font.width(part) > TOOLTIP_W) {
                String head = font.plainSubstrByWidth(part, TOOLTIP_W);
                if (head.isEmpty()) break;
                lines.add(head);
                part = part.substring(head.length());
            }
            line.append(part);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private void bin(Gfx gfx, int x, int y, int colour) {
        gfx.fill(x + 1, y, x + 5, y + 1, colour);
        gfx.fill(x, y + 1, x + 6, y + 2, colour);
        gfx.fill(x + 1, y + 2, x + 2, y + 6, colour);
        gfx.fill(x + 4, y + 2, x + 5, y + 6, colour);
        gfx.fill(x + 1, y + 6, x + 5, y + 7, colour);
    }

    private void drawFooter(Gfx gfx) {
        String footer;
        if (problem != null) {
            footer = problem;
        } else if (status != null) {
            footer = status;
        } else if (armed >= 0) {
            footer = "Click again to delete.";
        } else {
            footer = "Deleting only means a rescan.";
        }
        gfx.text(font, Component.literal(footer),
                panelX + (PANEL_W - font.width(footer)) / 2, panelY + panelH() + 6,
                problem != null ? DANGER : Panel.TEXT_MUTED);
    }

    private String truncate(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static boolean within(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < rowsX() || mouseX >= rowsX() + rowsW()) return -1;
        int top = contentTop() + 1;
        if (mouseY < top || mouseY >= top + visibleRows * ROW_H) return -1;
        int index = scroll + (mouseY - top) / ROW_H;
        return index < locations.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        if (within(mouseX, mouseY, closeX(), panelY + 3, BUTTON_SIZE, BUTTON_SIZE)) {
            VanillaButton.playClick();
            onClose();
            return true;
        }

        int index = rowAt(mouseX, mouseY);
        if (index < 0) return super.mouseClicked(event, doubleClick);

        int right = rowsX() + rowsW();
        IndexStore.Location location = locations.get(index);
        int recheckX = recheckX(location, right);
        if (mouseX >= recheckX && mouseX < recheckX + BUTTON_SIZE) {
            armed = -1;
            VanillaButton.playClick();
            recheck(location);
            return true;
        }
        if (hosted(location) || mouseX < deleteX(right) || mouseX >= right - 4) return true;

        if (armed != index) {
            armed = index;
            problem = null;
            status = null;
            VanillaButton.playClick();
            return true;
        }

        try {
            problem = IndexStore.delete(locations.get(index));
            locations = IndexStore.all();
        } catch (RuntimeException failed) {
            problem = "Could not delete it.";
            ChestIndex.LOG.warn("Could not delete a stored index: {}", failed.toString());
        }
        armed = -1;
        status = null;
        VanillaButton.playClick();
        scroll = Math.min(scroll, maxScroll());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int max = maxScroll();
        if (max == 0) return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(deltaY)));
        armed = -1;
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    //? if >=26.1 {
    /*@Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Panel.window(new Gfx(graphics), panelX, panelY, PANEL_W, panelH());
    }
    *///?} else {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Panel.window(new Gfx(graphics), panelX, panelY, PANEL_W, panelH());
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
