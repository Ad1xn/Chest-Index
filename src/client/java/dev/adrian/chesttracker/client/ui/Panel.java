package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.platform.Gfx;
import net.minecraft.resources.Identifier;

/**
 * The chest window, drawn at whatever size is asked for.
 *
 * <p>{@link ChestTrackerScreen} draws vanilla's {@code generic_54} texture
 * directly because it wants exactly what that texture is: a title row, nine
 * slots across and six down. The settings screen wants the same window without
 * the slots, and taller than the art is, so the geometry that used to be
 * private to one screen lives here and takes a size.
 *
 * <p>Everything is built from the texture rather than from fixed colours, for
 * the reason the tracker screen already gives: a resource pack repaints this
 * window, and a hardcoded grey sits in the middle of somebody's brown chest
 * looking like a hole. The one exception is the bevel palette below, which is
 * vanilla's own for the parts - thumbs, buttons, grooves - the art has no
 * pixels for.
 *
 * <h2>How a band of arbitrary size is drawn</h2>
 *
 * <p>Each horizontal band is three blits: the left corner 1:1, the right
 * corner 1:1, and a single uniform pixel column out of the middle stretched to
 * bridge whatever is between them. The vertical borders are the same trick
 * turned ninety degrees - a seven-pixel-wide, one-pixel-tall slice of the side
 * border stretched down the window. GUI textures are sampled
 * nearest-neighbour, so a stretched uniform slice is the same pixels the art
 * has; it is one draw call instead of one per pixel.
 */
public final class Panel {

    private Panel() {}

    public static final Identifier TEXTURE =
            Identifier.parse("minecraft:textures/gui/container/generic_54.png");

    /** Geometry of generic_54.png: a 176x222 window on a 256x256 sheet. */
    private static final int SHEET = 256;
    private static final int GUI_W = 176;

    /** The top border and title row. */
    public static final int TOP_H = 17;

    /** The bottom border. */
    public static final int BOTTOM_H = 7;

    /** The window's side border. */
    public static final int EDGE_W = 7;

    /** Height of the strip vanilla uses for a text field. */
    public static final int SEARCH_H = 16;

    /** Where the slot rows - and so the flat side borders - start in the sheet. */
    private static final int ROWS_V = 17;

    /** The bottom border's row in the sheet. */
    private static final int BOTTOM_V = 215;

    /** The search strip's row in the sheet. */
    private static final int SEARCH_V = 30;

    /** A uniform column of the border bands, for stretching. */
    private static final int FILLER_U = 100;

    /** The flat panel column between the last slot and the edge. */
    private static final int FLAT_U = 170;

    // Vanilla's container palette, for the parts drawn rather than blitted.
    public static final int BEVEL_LIGHT = 0xFFFFFFFF;
    public static final int BEVEL_DARK = 0xFF555555;
    public static final int GROOVE = 0xFF8B8B8B;
    public static final int GROOVE_DARK = 0xFF373737;

    /**
     * Standard Minecraft text: white, with the drop shadow the font draws by
     * default. Alpha is spelled out - {@code 0xFFFFFF} is fully transparent in
     * ARGB and draws nothing at all.
     */
    public static final int TEXT_MAIN = 0xFFFFFFFF;

    /** Standard secondary text, for a value sitting at its default. */
    public static final int TEXT_MUTED = 0xFFAAAAAA;

    /** Icons drawn as shapes rather than glyphs, against the light panel. */
    public static final int ICON = 0xFF404040;

    /** The wash vanilla puts over a hovered slot. */
    public static final int HOVER = 0x80FFFFFF;

    /** The green this mod uses for anything switched on. */
    public static final int ON = 0xFF6A9A4A;

    // Vanilla's tooltip palette, taken from its own renderer so a panel drawn
    // here sits beside a real item tooltip rather than next to one.
    public static final int TOOLTIP_BG = 0xF0100010;
    public static final int TOOLTIP_EDGE_TOP = 0x505000FF;
    public static final int TOOLTIP_EDGE_BOTTOM = 0x5028007F;

    /**
     * The whole window: title row, body, bottom border.
     *
     * @param h the outside height, bands included
     */
    public static void window(Gfx gfx, int x, int y, int w, int h) {
        int bodyH = h - TOP_H - BOTTOM_H;
        band(gfx, x, y, w, 0, TOP_H, FILLER_U);
        body(gfx, x, y + TOP_H, w, bodyH);
        band(gfx, x, y + h - BOTTOM_H, w, BOTTOM_V, BOTTOM_H, FILLER_U);
    }

    /**
     * One horizontal band of the border, widened to any width.
     *
     * <p>The band cannot simply be blitted twice, once right-aligned: its ends
     * are corner art, so the second copy paints a corner into the middle of the
     * window.
     */
    public static void band(Gfx gfx, int x, int y, int w, int srcV, int h, int fillerU) {
        if (w < EDGE_W * 2) return;
        gfx.blit(TEXTURE, x, y, 0, srcV, EDGE_W, h, SHEET, SHEET);
        gfx.blitStretched(TEXTURE, x + EDGE_W, y, fillerU, srcV,
                w - EDGE_W * 2, h, 1, h, SHEET, SHEET);
        gfx.blit(TEXTURE, x + w - EDGE_W, y, GUI_W - EDGE_W, srcV, EDGE_W, h, SHEET, SHEET);
    }

    /**
     * The window's middle: two side borders and flat panel between them.
     *
     * <p>Taken from the slot rows' band, which is the only part of the art with
     * a side border that is uniform top to bottom - the title and bottom rows
     * both carry a shadow that would repeat down the window as stripes.
     */
    public static void body(Gfx gfx, int x, int y, int w, int h) {
        if (w < EDGE_W * 2 || h <= 0) return;
        gfx.blitStretched(TEXTURE, x, y, 0, ROWS_V, EDGE_W, h, EDGE_W, 1, SHEET, SHEET);
        flat(gfx, x + EDGE_W, y, w - EDGE_W * 2, h);
        gfx.blitStretched(TEXTURE, x + w - EDGE_W, y, GUI_W - EDGE_W, ROWS_V,
                EDGE_W, h, EDGE_W, 1, SHEET, SHEET);
    }

    /**
     * Fills a rectangle with the window's own flat panel pixels.
     *
     * <p>Used instead of a fixed colour everywhere vanilla has no art - button
     * faces, scrollbar thumbs, the inside of a list - so they follow a resource
     * pack rather than sitting in it as grey rectangles.
     */
    public static void flat(Gfx gfx, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        // One pixel stretched, not an h-tall column stretched. Sampling h
        // pixels reads down past the slot band - which is only 108 tall - into
        // the player-inventory art below it and then off the end of the window
        // entirely. Vanilla's sheet happens to be more grey down there so it
        // looked right; a pack that paints that region dark filled the bottom
        // of every panel with black.
        gfx.blitStretched(TEXTURE, x, y, FLAT_U, ROWS_V, w, h, 1, 1, SHEET, SHEET);
    }

    /** The strip vanilla puts a text field in, with the field's groove in it. */
    public static void searchStrip(Gfx gfx, int x, int y, int w) {
        band(gfx, x, y, w, SEARCH_V, SEARCH_H, FLAT_U);
        groove(gfx, x + EDGE_W, y + 2, w - EDGE_W * 2, 12);
    }

    /** The inset vanilla uses for slots and text fields, filled flat grey. */
    public static void groove(Gfx gfx, int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, GROOVE_DARK);
        gfx.fill(x + 1, y + 1, x + w, y + h, BEVEL_LIGHT);
        gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, GROOVE);
    }

    /**
     * A sunken panel with the window's own pixels inside it.
     *
     * <p>Like {@link #groove} but without the flat grey fill, so a resource
     * pack's chest shows through the middle of a list rather than a vanilla
     * rectangle sitting in it.
     */
    public static void well(Gfx gfx, int x, int y, int w, int h) {
        flat(gfx, x, y, w, h);
        gfx.fill(x, y, x + w - 1, y + 1, GROOVE_DARK);
        gfx.fill(x, y, x + 1, y + h - 1, GROOVE_DARK);
        gfx.fill(x + w - 1, y, x + w, y + h, BEVEL_LIGHT);
        gfx.fill(x, y + h - 1, x + w, y + h, BEVEL_LIGHT);
    }

    /**
     * A button face standing out of the panel.
     *
     * @param lit drawn in the on-green rather than in the panel's own pixels
     */
    public static void raised(Gfx gfx, int x, int y, int w, int h, boolean lit) {
        if (w < 2 || h < 2) return;
        gfx.fill(x, y, x + w, y + h, BEVEL_DARK);
        if (lit) {
            gfx.fill(x, y, x + w - 1, y + h - 1, ON);
        } else {
            flat(gfx, x, y, w - 1, h - 1);
        }
        gfx.fill(x, y, x + w - 1, y + 1, BEVEL_LIGHT);
        gfx.fill(x, y, x + 1, y + h - 1, BEVEL_LIGHT);
    }

    /** A scrollbar in its groove, sized to what it is scrolling. */
    public static void scrollbar(Gfx gfx, int x, int y, int w, int h,
                                 int scroll, int maxScroll, int totalRows, int visibleRows) {
        groove(gfx, x, y, w, h);

        int thumbH = maxScroll == 0 ? h - 2
                : Math.max(12, (h - 2) * visibleRows / Math.max(1, totalRows));
        int travel = h - 2 - thumbH;
        int thumbY = y + 1 + (maxScroll == 0 ? 0 : travel * scroll / maxScroll);
        int inner = w - 2;

        flat(gfx, x + 1, thumbY, inner, thumbH);
        gfx.fill(x + 1, thumbY, x + 1 + inner, thumbY + 1, BEVEL_LIGHT);
        gfx.fill(x + 1, thumbY, x + 2, thumbY + thumbH, BEVEL_LIGHT);
        gfx.fill(x + 1, thumbY + thumbH - 1, x + 1 + inner, thumbY + thumbH, BEVEL_DARK);
        gfx.fill(x + inner, thumbY, x + 1 + inner, thumbY + thumbH, BEVEL_DARK);
    }

    /**
     * Vanilla's own tooltip frame, drawn to its recipe.
     *
     * <p>Vanilla insets the background three pixels around the text and runs a
     * two-stop gradient down the two side borders, and those numbers are what
     * make it recognisable, so they are copied rather than approximated.
     *
     * @param x the text's left edge, not the frame's
     */
    public static void tooltipFrame(Gfx gfx, int x, int y, int textWidth, int textHeight) {
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

    /**
     * A tick, for a box that is switched on.
     *
     * <p>Drawn rather than typed: the font's check glyph is not in every
     * language's font and a letter would say nothing.
     */
    public static void tick(Gfx gfx, int x, int y, int colour) {
        gfx.fill(x, y + 3, x + 1, y + 6, colour);
        gfx.fill(x + 1, y + 4, x + 2, y + 7, colour);
        gfx.fill(x + 2, y + 5, x + 3, y + 8, colour);
        gfx.fill(x + 3, y + 3, x + 4, y + 6, colour);
        gfx.fill(x + 4, y + 1, x + 5, y + 4, colour);
        gfx.fill(x + 5, y, x + 6, y + 2, colour);
    }

    // --- section icons, for when the real item cannot be drawn ---------------

    /**
     * Vanilla's own art for the five settings sections.
     *
     * <p>26.2 does not bind an item's components until a world is loaded, so
     * {@code new ItemStack(item)} throws at the title screen and the rail has
     * no item to render. It used to fall back to the section's initial, and
     * then to glyphs drawn here by hand - which was this mod's idea of what a
     * hopper looks like rather than the game's, the same mistake the tooltips
     * and buttons elsewhere were written to avoid.
     *
     * <p>These are the textures themselves, blitted straight out of the
     * assets. They need no components and no world, they are the pixels Mojang
     * drew, and a resource pack repaints them along with everything else on
     * the window.
     */
    private static final Identifier HOPPER = Identifier.parse("minecraft:textures/item/hopper.png");
    private static final Identifier SPYGLASS = Identifier.parse("minecraft:textures/item/spyglass.png");

    /** One frame of the compass, which is thirty-two files rather than one. */
    private static final Identifier COMPASS = Identifier.parse("minecraft:textures/item/compass_16.png");

    private static final Identifier REDSTONE_TORCH =
            Identifier.parse("minecraft:textures/block/redstone_torch.png");

    /**
     * The chest, which has no flat texture of its own.
     *
     * <p>A chest is a block entity: its art is an unwrapped 64x64 model sheet,
     * not a sixteen-pixel icon. So the front is rebuilt from it - the lid's
     * front face, the base's front face under it, and the lock across the seam
     * - at the offsets the chest model itself uses.
     */
    private static final Identifier CHEST =
            Identifier.parse("minecraft:textures/entity/chest/normal.png");

    private static final int CHEST_SHEET = 64;

    /** Where a sixteen-pixel icon sits inside a twenty-pixel rail button. */
    private static final int ICON_INSET = 2;

    /**
     * Draws one section's icon at its rail button's top-left corner.
     *
     * @param index which section, in rail order
     */
    public static void sectionIcon(Gfx gfx, int index, int x, int y) {
        switch (index) {
            case 0 -> chestFront(gfx, x + 3, y + ICON_INSET);
            case 1 -> icon(gfx, HOPPER, x, y);
            case 2 -> icon(gfx, SPYGLASS, x, y);
            case 3 -> icon(gfx, COMPASS, x, y);
            default -> icon(gfx, REDSTONE_TORCH, x, y);
        }
    }

    /** A whole 16x16 texture, drawn where the item would have been. */
    private static void icon(Gfx gfx, Identifier texture, int x, int y) {
        gfx.blit(texture, x + ICON_INSET, y + ICON_INSET, 0, 0, 16, 16, 16, 16);
    }

    /**
     * The chest's front, 14 wide and 15 tall.
     *
     * <p>The offsets are the chest model's own: the lid is a 14x5x14 box at
     * texture origin (0,0) and the base a 14x10x14 box at (0,19), and a box's
     * front face sits one depth in and one depth down from its origin - so
     * (14,14) and (14,33). The lock is a 2x4x1 box at (0,0), front face at
     * (1,1), and it straddles the seam between the two.
     */
    private static void chestFront(Gfx gfx, int x, int y) {
        gfx.blit(CHEST, x, y, 14, 14, 14, 5, CHEST_SHEET, CHEST_SHEET);
        gfx.blit(CHEST, x, y + 5, 14, 33, 14, 10, CHEST_SHEET, CHEST_SHEET);
        gfx.blit(CHEST, x + 6, y + 3, 1, 1, 2, 4, CHEST_SHEET, CHEST_SHEET);
    }

    /** A right-pointing chevron, for a row that opens something or cycles. */
    public static void chevron(Gfx gfx, int x, int y, int colour) {
        gfx.fill(x, y, x + 1, y + 2, colour);
        gfx.fill(x + 1, y + 1, x + 2, y + 3, colour);
        gfx.fill(x + 2, y + 2, x + 3, y + 4, colour);
        gfx.fill(x + 1, y + 3, x + 2, y + 5, colour);
        gfx.fill(x, y + 4, x + 1, y + 6, colour);
    }
}
