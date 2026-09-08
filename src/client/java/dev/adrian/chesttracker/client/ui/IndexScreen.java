package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.ChestTracker;
import dev.adrian.chesttracker.client.index.IndexStore;
import dev.adrian.chesttracker.client.platform.Gfx;
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

/**
 * Every stored index, and a button to throw one away.
 *
 * <p>An index is a cache, and the honest thing to say about a cache is where it
 * is, how big it has got, and how to be rid of it. Until this screen the only
 * answer to "this world's index is wrong" was a command that needed operator
 * rights and only reached the world you were standing in - and for a server
 * this client had indexed for itself, there was no answer at all short of
 * finding the folder by hand.
 *
 * <h2>Deleting takes two clicks</h2>
 *
 * <p>The first click arms the row and the second does it, and moving off the
 * row disarms it again. A stored index can be minutes of scanning on a large
 * world, so a single mis-click on a twelve-pixel button being enough to destroy
 * one would be wrong - and a modal "are you sure" over a settings screen is a
 * heavier answer than the risk deserves.
 *
 * <p>Nothing here is unrecoverable in the way deleting a save is: the index is
 * rebuilt by scanning the world again. It is time, not data.
 */
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

    /** Red, for the one button on this screen that destroys something. */
    private static final int DANGER = 0xFFB03030;

    private final Screen parent;

    private int panelX;
    private int panelY;
    private int visibleRows;
    private int scroll;

    private List<IndexStore.Location> locations = List.of();

    /** Which row is armed, in the list's own numbering, or -1. */
    private int armed = -1;

    /** What went wrong with the last delete, shown in the footer. */
    private String problem;

    private String hoverTitle;

    /** Set while drawing, drawn last so nothing covers it. */
    private List<String> pendingTooltip;

    /** The band the tooltip must stay clear of: the hovered row. */
    private int tooltipAvoidTop;
    private int tooltipAvoidBottom;

    /**
     * How wide the path tooltip may grow.
     *
     * <p>Wider than a prose tooltip. A path is one long unbreakable-looking
     * token and the whole point of showing it is that it can be read.
     */
    private static final int TOOLTIP_W = 220;

    public IndexScreen(Screen parent) {
        super(Component.literal("Stored indexes"));
        this.parent = parent;
    }

    private static final int CHROME_H =
            Panel.TOP_H + PAD + 2 + PAD + Panel.BOTTOM_H;

    /**
     * Lays the window out first, then reads the disk.
     *
     * <p>The order matters and is the whole reason this is commented. Reading
     * came first, and when it threw - a corrupted mod jar, in the case that
     * found this - {@code init} never reached the two lines that place the
     * window. Vanilla carries on rendering a screen whose init failed, so the
     * panel drew at 0,0 with a zero size: a window jammed into the top-left
     * corner, which looks like a layout bug and is actually an exception three
     * frames earlier.
     *
     * <p>So the geometry is set from {@code width} and {@code height} alone,
     * which cannot fail, and the listing is fetched afterwards behind a guard.
     */
    @Override
    protected void init() {
        int room = height - 40 - CHROME_H;
        visibleRows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, room / ROW_H));

        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH()) / 2;

        try {
            locations = IndexStore.all();
            problem = null;
        } catch (RuntimeException unreadable) {
            // Nothing here is worth taking the game down for. The screen still
            // draws, and the footer says why it is empty.
            locations = List.of();
            problem = "Could not read the index folders.";
            ChestTracker.LOG.warn("Could not list stored indexes: {}", unreadable.toString());
        }
        scroll = Math.min(scroll, maxScroll());
    }

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

    // --- drawing --------------------------------------------------------------

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

        // Last, so nothing on the window is drawn over it.
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

        // Moving off an armed row disarms it, so a row cannot sit primed while
        // the cursor is somewhere else entirely.
        if (armed >= 0 && hovered != armed) armed = -1;

        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= locations.size()) break;

            IndexStore.Location location = locations.get(index);
            int y = rowY(i);
            if (i > 0) gfx.fill(left + 2, y, right - 2, y + 1, 0x18000000);
            if (index == hovered) {
                gfx.fill(left, y + 1, right, y + ROW_H, 0x30FFFFFF);
                // The name in the title row and the path in a tooltip, rather
                // than the path in the title row. The title row is about a
                // hundred and forty pixels and every one of these paths is far
                // longer than that, so it only ever showed the first third of
                // one - which is the least useful third, being the same for all
                // of them.
                hoverTitle = location.name();
                pendingTooltip = describe(location);
                tooltipAvoidTop = y;
                tooltipAvoidBottom = y + ROW_H;
            }

            int buttonX = right - 4 - BUTTON_SIZE;
            gfx.text(font, Component.literal(truncate(location.name(), buttonX - left - 8)),
                    left + 4, y + 4, Panel.TEXT_MAIN);
            gfx.text(font, Component.literal(truncate(location.describe(), buttonX - left - 8)),
                    left + 4, y + 14, location.active() ? Panel.ON : Panel.TEXT_MUTED);

            boolean primed = index == armed;
            int buttonY = y + (ROW_H - BUTTON_SIZE) / 2;
            Panel.raised(gfx, buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE, false);
            if (primed) {
                gfx.fill(buttonX, buttonY, buttonX + BUTTON_SIZE - 1, buttonY + BUTTON_SIZE - 1, DANGER);
            }
            bin(gfx, buttonX + 3, buttonY + 3, primed ? Panel.TEXT_MAIN : Panel.ICON);
        }
    }

    /** What the hovered row's tooltip says: what it is, and where it is. */
    private List<String> describe(IndexStore.Location location) {
        List<String> lines = new ArrayList<>();
        lines.add(location.name());
        lines.add(location.describe());
        lines.add("");
        lines.addAll(wrapPath(location.directory().toString()));
        return lines;
    }

    /**
     * Breaks a path across lines at its separators.
     *
     * <p>Word wrapping is no use here: a path has no spaces, so a prose wrapper
     * puts the whole thing on one line and lets it run off the screen. Broken
     * after the slashes instead, which is where a path reads as being divisible
     * - and any single segment still too wide for a line is cut by width, so
     * the tooltip cannot overflow whatever somebody has named a world.
     */
    private List<String> wrapPath(String path) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        // Split after each separator, keeping it on the end of its segment.
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

    /** A six-by-six waste bin, because no font glyph reads as one. */
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
        } else if (armed >= 0) {
            footer = "Click again to delete it. Scanning rebuilds it.";
        } else {
            footer = "Deleting one costs a rescan, never the world itself.";
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

    // --- input ----------------------------------------------------------------

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
        // Only the button deletes. Clicking the row's text arms nothing, so
        // reading down the list cannot prime anything by accident.
        if (mouseX < right - 4 - BUTTON_SIZE || mouseX >= right - 4) return true;

        if (armed != index) {
            armed = index;
            problem = null;
            VanillaButton.playClick();
            return true;
        }

        try {
            problem = IndexStore.delete(locations.get(index));
            // Re-read rather than removing the row by hand, so a delete that
            // only half worked shows what is actually left.
            locations = IndexStore.all();
        } catch (RuntimeException failed) {
            problem = "Could not delete it.";
            ChestTracker.LOG.warn("Could not delete a stored index: {}", failed.toString());
        }
        armed = -1;
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
