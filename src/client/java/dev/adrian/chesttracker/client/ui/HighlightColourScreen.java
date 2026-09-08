package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.util.Hsv;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

public final class HighlightColourScreen extends Screen {

    private static final int[] SUGGESTED = {
            0xFF2BD0, 0xFF3B7A, 0xFF6A2B, 0xFFC400, 0x9CFF2B, 0x2BFF9C, 0x2BE5FF, 0xB478FF,
    };

    private static final int SQUARE = 120;
    private static final int HUE_H = 12;

    private static final int SWATCH = 14;
    private static final int SWATCH_GAP = 3;

    private static final int SWATCH_COLS = 2;
    private static final int SWATCH_ROWS = 4;

    private static final int GRID_GAP = 5;

    private static final int SWATCH_GRID_W = SWATCH_COLS * SWATCH + (SWATCH_COLS - 1) * SWATCH_GAP;
    private static final int SWATCH_GRID_H = SWATCH_ROWS * SWATCH + (SWATCH_ROWS - 1) * SWATCH_GAP;

    private static final int CONTENT_W = SQUARE + GRID_GAP + SWATCH_GRID_W;

    private static final int SIDE_PAD = 8;

    private static final int TAB_H = 20;
    private static final int TAB_GAP = 4;

    private static final int HEX_W = 108;
    private static final int RESET_W = CONTENT_W - HEX_W - TAB_GAP;
    private static final int FIELD_H = 14;

    private static final int PAD = 3;
    private static final int BUTTON_SIZE = 12;

    private static final int TABS_Y = Panel.TOP_H + PAD;
    private static final int SQUARE_Y = TABS_Y + TAB_H + PAD + 1;
    private static final int HUE_Y = SQUARE_Y + SQUARE + 2 + PAD + 1;
    private static final int HEX_Y = HUE_Y + HUE_H + 2 + PAD + 1;
    private static final int PANEL_H = HEX_Y + FIELD_H + PAD + Panel.BOTTOM_H;

    private static final int PANEL_W = (Panel.EDGE_W + SIDE_PAD) * 2 + CONTENT_W;

    private final Screen parent;
    private final ChestTrackerConfig config = ChestTrackerConfig.get();

    private int panelX;
    private int panelY;

    private boolean editingNearest = true;

    private float hue;
    private float saturation;
    private float value;

    private boolean draggingSquare;
    private boolean draggingHue;

    private EditBox hex;

    private boolean syncingHex;

    private String hoverTitle;

    public HighlightColourScreen(Screen parent) {
        super(Component.literal("Highlight colours"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        readFromConfig();

        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        hex = new EditBox(font, contentX() + 4, panelY + HEX_Y + 3, HEX_W - 8, 10,
                Component.literal("Hex"));
        hex.setBordered(false);
        hex.setMaxLength(7);
        hex.setResponder(this::onHexTyped);
        addRenderableWidget(hex);
        syncHexBox();
    }

    private int current() {
        return editingNearest ? config.nearestColour : config.otherColour;
    }

    private void store(int rgb) {
        if (editingNearest) {
            config.nearestColour = rgb;
        } else {
            config.otherColour = rgb;
        }
    }

    private void readFromConfig() {
        float[] hsv = Hsv.toHsv(current());
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    private void commit() {
        store(Hsv.toRgb(hue, saturation, value));
        syncHexBox();
    }

    private int contentX() {
        return panelX + Panel.EDGE_W + SIDE_PAD;
    }

    private int squareLeft() {
        return contentX() + 1;
    }

    private int squareTop() {
        return panelY + SQUARE_Y + 1;
    }

    private int hueTop() {
        return panelY + HUE_Y + 1;
    }

    private int gridLeft() {
        return contentX() + SQUARE + GRID_GAP + 2;
    }

    private int gridTop() {
        return squareTop() + (SQUARE - SWATCH_GRID_H) / 2;
    }

    private int swatchX(int index) {
        return gridLeft() + (index % SWATCH_COLS) * (SWATCH + SWATCH_GAP);
    }

    private int swatchY(int index) {
        return gridTop() + (index / SWATCH_COLS) * (SWATCH + SWATCH_GAP);
    }

    private int tabX(boolean nearest) {
        int tabW = (CONTENT_W - TAB_GAP) / 2;
        return contentX() + (nearest ? 0 : tabW + TAB_GAP);
    }

    private int tabW() {
        return (CONTENT_W - TAB_GAP) / 2;
    }

    private int resetX() {
        return contentX() + HEX_W + TAB_GAP;
    }

    private int closeX() {
        return panelX + PANEL_W - SIDE_PAD - BUTTON_SIZE;
    }

    private int titleWidth() {
        return closeX() - (panelX + SIDE_PAD) - 4;
    }

    private void draw(Gfx gfx, int mouseX, int mouseY) {
        hoverTitle = null;

        drawCloseButton(gfx, mouseX, mouseY);
        drawTabs(gfx, mouseX, mouseY);
        drawSquare(gfx);
        drawHueStrip(gfx);
        drawSuggested(gfx, mouseX, mouseY);
        drawHexRow(gfx, mouseX, mouseY);

        String title = hoverTitle != null ? hoverTitle : "Highlight colours";
        gfx.text(font, Component.literal(truncate(title, titleWidth())),
                panelX + SIDE_PAD, panelY + 6, Panel.TEXT_MAIN);

        drawFooter(gfx);
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

    private void drawTabs(Gfx gfx, int mouseX, int mouseY) {
        for (boolean nearest : new boolean[] {true, false}) {
            int x = tabX(nearest);
            int y = panelY + TABS_Y;
            int w = tabW();
            boolean active = nearest == editingNearest;
            boolean hovered = within(mouseX, mouseY, x, y, w, TAB_H);

            Panel.raised(gfx, x, y, w, TAB_H, false);
            chip(gfx, x + 3, y + 3, SWATCH, SWATCH,
                    nearest ? config.nearestColour : config.otherColour);

            String label = nearest ? "Nearest" : "Others";
            gfx.text(font, Component.literal(label), x + SWATCH + 7, y + 6,
                    active ? Panel.TEXT_MAIN : Panel.TEXT_MUTED);

            if (active) {
                gfx.fill(x - 1, y - 1, x + w + 1, y, Panel.BEVEL_LIGHT);
                gfx.fill(x - 1, y + TAB_H, x + w + 1, y + TAB_H + 1, Panel.BEVEL_LIGHT);
                gfx.fill(x - 1, y, x, y + TAB_H, Panel.BEVEL_LIGHT);
                gfx.fill(x + w, y, x + w + 1, y + TAB_H, Panel.BEVEL_LIGHT);
            }
            if (hovered) {
                gfx.fill(x + 1, y + 1, x + w - 1, y + TAB_H - 1, Panel.HOVER);
                hoverTitle = nearest ? "The nearest match" : "Every other match";
            }
        }
    }

    private void drawSquare(Gfx gfx) {
        int left = squareLeft();
        int top = squareTop();
        Panel.groove(gfx, left - 1, top - 1, SQUARE + 2, SQUARE + 2);

        for (int i = 0; i < SQUARE; i++) {
            float s = i / (float) (SQUARE - 1);
            gfx.fillGradient(left + i, top, left + i + 1, top + SQUARE,
                    Hsv.toRgb(hue, s, 1.0f) | 0xFF000000, 0xFF000000);
        }

        int cursorX = left + Math.round(saturation * (SQUARE - 1));
        int cursorY = top + Math.round((1.0f - value) * (SQUARE - 1));
        ring(gfx, cursorX, cursorY);
    }

    private void drawHueStrip(Gfx gfx) {
        int left = squareLeft();
        int top = hueTop();
        Panel.groove(gfx, left - 1, top - 1, SQUARE + 2, HUE_H + 2);

        for (int i = 0; i < SQUARE; i++) {
            float h = i / (float) (SQUARE - 1);
            gfx.fill(left + i, top, left + i + 1, top + HUE_H, Hsv.toRgb(h, 1.0f, 1.0f) | 0xFF000000);
        }

        int markerX = left + Math.round(hue * (SQUARE - 1));
        gfx.fill(markerX - 1, top - 2, markerX + 2, top + HUE_H + 2, 0xFF000000);
        gfx.fill(markerX, top - 1, markerX + 1, top + HUE_H + 1, Panel.BEVEL_LIGHT);
    }

    private void drawSuggested(Gfx gfx, int mouseX, int mouseY) {
        for (int i = 0; i < SUGGESTED.length; i++) {
            int x = swatchX(i);
            int y = swatchY(i);
            boolean hovered = within(mouseX, mouseY, x, y, SWATCH, SWATCH);
            boolean chosen = SUGGESTED[i] == (current() & 0xFFFFFF);

            if (chosen || hovered) {
                gfx.fill(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1,
                        chosen ? Panel.BEVEL_LIGHT : Panel.TEXT_MUTED);
            }
            chip(gfx, x, y, SWATCH, SWATCH, SUGGESTED[i]);
            if (hovered) hoverTitle = String.format("#%06X", SUGGESTED[i] & 0xFFFFFF);
        }
    }

    private void drawHexRow(Gfx gfx, int mouseX, int mouseY) {
        int y = panelY + HEX_Y;
        Panel.groove(gfx, contentX(), y, HEX_W, FIELD_H);

        int x = resetX();
        boolean hovered = within(mouseX, mouseY, x, y, RESET_W, FIELD_H);
        Panel.raised(gfx, x, y, RESET_W, FIELD_H, false);
        String label = "Reset";
        gfx.text(font, Component.literal(label),
                x + (RESET_W - font.width(label)) / 2, y + 3, Panel.TEXT_MAIN);
        if (hovered) {
            gfx.fill(x + 1, y + 1, x + RESET_W - 1, y + FIELD_H - 1, Panel.HOVER);
            hoverTitle = "Back to both defaults";
        }
    }

    private void drawFooter(Gfx gfx) {
        String footer = editingNearest
                ? "Editing the colour the nearest match is drawn in."
                : "Editing the colour every other match is drawn in.";
        gfx.text(font, Component.literal(footer),
                panelX + (PANEL_W - font.width(footer)) / 2, panelY + PANEL_H + 6,
                Panel.TEXT_MUTED);
    }

    private void chip(Gfx gfx, int x, int y, int w, int h, int rgb) {
        gfx.fill(x, y, x + w, y + h, 0xFF000000);
        gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000 | rgb);
    }

    private void ring(Gfx gfx, int cx, int cy) {
        gfx.fill(cx - 4, cy - 4, cx + 5, cy - 3, 0xFF000000);
        gfx.fill(cx - 4, cy + 4, cx + 5, cy + 5, 0xFF000000);
        gfx.fill(cx - 4, cy - 3, cx - 3, cy + 4, 0xFF000000);
        gfx.fill(cx + 4, cy - 3, cx + 5, cy + 4, 0xFF000000);

        gfx.fill(cx - 3, cy - 3, cx + 4, cy - 2, 0xFFFFFFFF);
        gfx.fill(cx - 3, cy + 3, cx + 4, cy + 4, 0xFFFFFFFF);
        gfx.fill(cx - 3, cy - 2, cx - 2, cy + 3, 0xFFFFFFFF);
        gfx.fill(cx + 3, cy - 2, cx + 4, cy + 3, 0xFFFFFFFF);
    }

    private String truncate(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static boolean within(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
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

        for (boolean nearest : new boolean[] {true, false}) {
            if (within(mouseX, mouseY, tabX(nearest), panelY + TABS_Y, tabW(), TAB_H)) {
                editingNearest = nearest;
                readFromConfig();
                syncHexBox();
                VanillaButton.playClick();
                return true;
            }
        }

        if (within(mouseX, mouseY, squareLeft(), squareTop(), SQUARE, SQUARE)) {
            draggingSquare = true;
            pickFromSquare(mouseX, mouseY);
            return true;
        }

        if (within(mouseX, mouseY, squareLeft(), hueTop(), SQUARE, HUE_H)) {
            draggingHue = true;
            pickFromHue(mouseX);
            return true;
        }

        for (int i = 0; i < SUGGESTED.length; i++) {
            if (within(mouseX, mouseY, swatchX(i), swatchY(i), SWATCH, SWATCH)) {
                store(SUGGESTED[i]);
                readFromConfig();
                syncHexBox();
                VanillaButton.playClick();
                return true;
            }
        }

        if (within(mouseX, mouseY, resetX(), panelY + HEX_Y, RESET_W, FIELD_H)) {
            ChestTrackerConfig fresh = new ChestTrackerConfig();
            config.nearestColour = fresh.nearestColour;
            config.otherColour = fresh.otherColour;
            readFromConfig();
            syncHexBox();
            VanillaButton.playClick();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSquare) {
            pickFromSquare((int) event.x(), (int) event.y());
            return true;
        }
        if (draggingHue) {
            pickFromHue((int) event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingSquare = false;
        draggingHue = false;
        return super.mouseReleased(event);
    }

    private void pickFromSquare(int mouseX, int mouseY) {
        saturation = clamp01((mouseX - squareLeft()) / (float) (SQUARE - 1));
        value = 1.0f - clamp01((mouseY - squareTop()) / (float) (SQUARE - 1));
        commit();
    }

    private void pickFromHue(int mouseX) {
        hue = clamp01((mouseX - squareLeft()) / (float) (SQUARE - 1));
        commit();
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private void syncHexBox() {
        if (hex == null) return;
        syncingHex = true;
        hex.setValue(String.format("#%06X", current() & 0xFFFFFF));
        syncingHex = false;
    }

    private void onHexTyped(String typed) {
        if (syncingHex) return;

        String cleaned = typed.trim();
        if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);
        if (cleaned.length() != 6) return;

        int rgb;
        try {
            rgb = Integer.parseInt(cleaned, 16);
        } catch (NumberFormatException notHexYet) {
            return;
        }
        store(rgb);
        readFromConfig();
    }

    @Override
    public void onClose() {
        config.save();
        minecraft.setScreenAndShow(parent);
    }

    //? if >=26.1 {
    /*@Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Panel.window(new Gfx(graphics), panelX, panelY, PANEL_W, PANEL_H);
    }
    *///?} else {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Panel.window(new Gfx(graphics), panelX, panelY, PANEL_W, PANEL_H);
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
