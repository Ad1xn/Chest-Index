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

/**
 * Picks the two colours the in-world markers are drawn in.
 *
 * <p>A proper picker: a hue strip and a saturation/value square, the pair every
 * other piece of software uses, plus a hex box for anyone who already knows the
 * value they want and a grid of suggestions for anyone who does not.
 *
 * <p>It used to be sixteen fixed swatches. The reasoning was that three
 * channel sliders ask the player to know what a colour is <em>made of</em> in
 * order to choose one, which is true - but the answer to that is a picker, not
 * a menu of sixteen. Wanting a colour that was not one of the sixteen left no
 * way to say so.
 *
 * <p>The suggestions are kept, because the point they were making is real: a
 * marker's job is to not be the background, and Minecraft's background is sky
 * blue, sun yellow and grass green. Those are still bad choices; they are just
 * no longer the only ones on offer.
 *
 * <h2>Why it is drawn in the chest window</h2>
 *
 * <p>Because the screen that opens it is. This used to be bare rectangles with
 * hand-drawn black borders floating on the blurred background - which was fine
 * on its own and wrong the moment {@link SettingsScreen} became the chest
 * window, because clicking one row of a chest-shaped settings page dropped you
 * onto something that looked like a different mod. The picker's parts are now
 * inset into the panel the way vanilla insets a slot, through {@link Panel}, so
 * a resource pack carries through here too.
 *
 * <h2>Drawing a gradient without a gradient texture</h2>
 *
 * <p>The square is drawn as one-pixel-wide vertical gradients, one per column
 * of saturation, each running from that column's full-value colour down to
 * black. {@code fillGradient} is vertical-only and identical on both target
 * versions, so a hundred and twenty of them costs a hundred and twenty draw
 * calls rather than the fourteen thousand a per-pixel fill would take.
 */
public final class HighlightColourScreen extends Screen {

    /**
     * Colours worth marking something with, as a starting point.
     *
     * <p>Deliberately not a uniform spread of the spectrum: these are the ones
     * that survive being drawn over Minecraft, skipping the sky blues, the sun
     * yellows and the greens that sit in grass.
     */
    private static final int[] SUGGESTED = {
            0xFF2BD0, 0xFF3B7A, 0xFF6A2B, 0xFFC400, 0x9CFF2B, 0x2BFF9C, 0x2BE5FF, 0xB478FF,
    };

    // --- geometry ----------------------------------------------------------

    private static final int SQUARE = 120;
    private static final int HUE_H = 12;

    private static final int SWATCH = 14;
    private static final int SWATCH_GAP = 3;

    /** Two columns of suggestions beside the square rather than a row under it. */
    private static final int SWATCH_COLS = 2;
    private static final int SWATCH_ROWS = 4;

    /** Gap between the square and the suggestions. */
    private static final int GRID_GAP = 5;

    private static final int SWATCH_GRID_W = SWATCH_COLS * SWATCH + (SWATCH_COLS - 1) * SWATCH_GAP;
    private static final int SWATCH_GRID_H = SWATCH_ROWS * SWATCH + (SWATCH_ROWS - 1) * SWATCH_GAP;

    private static final int CONTENT_W = SQUARE + GRID_GAP + SWATCH_GRID_W;

    /** Panel border, plus the eight pixels vanilla leaves inside it. */
    private static final int SIDE_PAD = 8;

    private static final int TAB_H = 20;
    private static final int TAB_GAP = 4;

    private static final int HEX_W = 108;
    private static final int RESET_W = CONTENT_W - HEX_W - TAB_GAP;
    private static final int FIELD_H = 14;

    private static final int PAD = 3;
    private static final int BUTTON_SIZE = 12;

    // Offsets from the panel's top edge, stacked in the order they are drawn.
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

    /** Which of the two colours the picker is currently editing. */
    private boolean editingNearest = true;

    /**
     * The picker's own hue, saturation and value.
     *
     * <p>Held separately from the config's packed RGB because they carry
     * information it cannot. Black is one RGB value but every hue, and a fully
     * desaturated colour has no hue at all - so round-tripping through RGB on
     * every frame would make the cursor jump to a corner the moment the player
     * dragged into one, and lose the hue they had chosen. These are the truth
     * while the screen is open; the config is written from them.
     */
    private float hue;
    private float saturation;
    private float value;

    /** Set while dragging, so a drag that leaves an area keeps controlling it. */
    private boolean draggingSquare;
    private boolean draggingHue;

    private EditBox hex;

    /** Set while the code itself is writing the hex box, so its responder can ignore it. */
    private boolean syncingHex;

    /** Set while drawing, read when the title row is drawn. */
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

        // Inside the groove drawn for it rather than beside it, the way the
        // other two screens place their boxes.
        hex = new EditBox(font, contentX() + 4, panelY + HEX_Y + 2, HEX_W - 8, 12,
                Component.literal("Hex"));
        hex.setBordered(false);
        hex.setMaxLength(7);
        hex.setResponder(this::onHexTyped);
        addRenderableWidget(hex);
        syncHexBox();
    }

    // --- the value being edited ---------------------------------------------

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

    /** Pulls the edited colour into hue/saturation/value, for the cursor to sit on. */
    private void readFromConfig() {
        float[] hsv = Hsv.toHsv(current());
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    /** Writes hue/saturation/value back out as the packed colour. */
    private void commit() {
        store(Hsv.toRgb(hue, saturation, value));
        syncHexBox();
    }

    // --- geometry -----------------------------------------------------------

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

    /** Centred beside the square rather than aligned to its top, which read as adrift. */
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

    // --- drawing ------------------------------------------------------------

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

    /**
     * The two colours, side by side, as the pair of tabs.
     *
     * <p>Side by side because the only question that matters about these two is
     * whether they can be told apart, and that is not a question a screen can
     * answer one colour at a time.
     */
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

            // A white frame rather than the on-green the other screens use for
            // an active button: green beside an arbitrary colour chip is a
            // second colour making a claim, and this screen is about the chip.
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

    /**
     * Saturation left to right, value top to bottom.
     *
     * <p>One vertical gradient per column: the top of each is that column's
     * saturation at full brightness, the bottom is black, and the hardware
     * interpolates everything between.
     */
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

    /** The hue strip, a solid column per step around the wheel. */
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

    /**
     * A line under the window saying which of the two is being edited.
     *
     * <p>The tabs already show it, but they show it as a white frame around a
     * colour, and a frame is not a sentence. This is the one place on the
     * screen that can say what "nearest" means without abbreviating it.
     */
    private void drawFooter(Gfx gfx) {
        String footer = editingNearest
                ? "Editing the colour the nearest match is drawn in."
                : "Editing the colour every other match is drawn in.";
        gfx.text(font, Component.literal(footer),
                panelX + (PANEL_W - font.width(footer)) / 2, panelY + PANEL_H + 6,
                Panel.TEXT_MUTED);
    }

    /** A colour, with a dark frame so a dark one does not bleed into the panel. */
    private void chip(Gfx gfx, int x, int y, int w, int h, int rgb) {
        gfx.fill(x, y, x + w, y + h, 0xFF000000);
        gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000 | rgb);
    }

    /**
     * The cursor on the square: a small white ring with a dark outline.
     *
     * <p>Two colours because one of them is always invisible - a white cursor
     * disappears into the white corner and a black one into the black edge.
     */
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

    // --- input --------------------------------------------------------------

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
                // The other colour has its own hue and brightness; the cursor
                // has to move to where that colour actually is.
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
        // Clamped rather than ignored when the pointer leaves: letting go of a
        // slider by moving one pixel off it is the thing that makes a picker
        // feel broken.
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

    // --- the hex box --------------------------------------------------------

    private void syncHexBox() {
        if (hex == null) return;
        syncingHex = true;
        hex.setValue(String.format("#%06X", current() & 0xFFFFFF));
        syncingHex = false;
    }

    /**
     * Accepts a typed hex colour, and ignores anything that is not one yet.
     *
     * <p>Half-typed input is the normal state of a text field, not an error, so
     * "#FF2" simply does nothing until it is six digits. Rejecting it visibly
     * would mean complaining at somebody halfway through typing.
     */
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
        // Not syncHexBox(): rewriting the field under the cursor while it is
        // being typed in would fight the player for it.
        readFromConfig();
    }

    @Override
    public void onClose() {
        config.save();
        minecraft.setScreenAndShow(parent);
    }

    // The window is drawn in the background hook so the hex field renders on
    // top of it rather than under; see SettingsScreen for the two signatures
    // the Gfx facade cannot hide.
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
