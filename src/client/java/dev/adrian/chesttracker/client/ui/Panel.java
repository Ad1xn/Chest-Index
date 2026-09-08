package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.platform.Gfx;
import net.minecraft.resources.Identifier;

public final class Panel {

    private Panel() {}

    public static final Identifier TEXTURE =
            Identifier.parse("minecraft:textures/gui/container/generic_54.png");

    private static final int SHEET = 256;
    private static final int GUI_W = 176;

    public static final int TOP_H = 17;

    public static final int BOTTOM_H = 7;

    public static final int EDGE_W = 7;

    public static final int SEARCH_H = 16;

    private static final int ROWS_V = 17;

    private static final int BOTTOM_V = 215;

    private static final int SEARCH_V = 30;

    private static final int FILLER_U = 100;

    private static final int FLAT_U = 170;

    public static final int BEVEL_LIGHT = 0xFFFFFFFF;
    public static final int BEVEL_DARK = 0xFF555555;
    public static final int GROOVE = 0xFF8B8B8B;
    public static final int GROOVE_DARK = 0xFF373737;

    public static final int TEXT_MAIN = 0xFFFFFFFF;

    public static final int TEXT_MUTED = 0xFFAAAAAA;

    public static final int ICON = 0xFF404040;

    public static final int HOVER = 0x80FFFFFF;

    public static final int ON = 0xFF6A9A4A;

    public static final int TOOLTIP_BG = 0xF0100010;
    public static final int TOOLTIP_EDGE_TOP = 0x505000FF;
    public static final int TOOLTIP_EDGE_BOTTOM = 0x5028007F;

    public static void window(Gfx gfx, int x, int y, int w, int h) {
        int bodyH = h - TOP_H - BOTTOM_H;
        band(gfx, x, y, w, 0, TOP_H, FILLER_U);
        body(gfx, x, y + TOP_H, w, bodyH);
        band(gfx, x, y + h - BOTTOM_H, w, BOTTOM_V, BOTTOM_H, FILLER_U);
    }

    public static void band(Gfx gfx, int x, int y, int w, int srcV, int h, int fillerU) {
        if (w < EDGE_W * 2) return;
        gfx.blit(TEXTURE, x, y, 0, srcV, EDGE_W, h, SHEET, SHEET);
        gfx.blitStretched(TEXTURE, x + EDGE_W, y, fillerU, srcV,
                w - EDGE_W * 2, h, 1, h, SHEET, SHEET);
        gfx.blit(TEXTURE, x + w - EDGE_W, y, GUI_W - EDGE_W, srcV, EDGE_W, h, SHEET, SHEET);
    }

    public static void body(Gfx gfx, int x, int y, int w, int h) {
        if (w < EDGE_W * 2 || h <= 0) return;
        gfx.blitStretched(TEXTURE, x, y, 0, ROWS_V, EDGE_W, h, EDGE_W, 1, SHEET, SHEET);
        flat(gfx, x + EDGE_W, y, w - EDGE_W * 2, h);
        gfx.blitStretched(TEXTURE, x + w - EDGE_W, y, GUI_W - EDGE_W, ROWS_V,
                EDGE_W, h, EDGE_W, 1, SHEET, SHEET);
    }

    public static void flat(Gfx gfx, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        gfx.blitStretched(TEXTURE, x, y, FLAT_U, ROWS_V, w, h, 1, 1, SHEET, SHEET);
    }

    public static void searchStrip(Gfx gfx, int x, int y, int w) {
        band(gfx, x, y, w, SEARCH_V, SEARCH_H, FLAT_U);
        groove(gfx, x + EDGE_W, y + 2, w - EDGE_W * 2, 12);
    }

    public static void groove(Gfx gfx, int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, GROOVE_DARK);
        gfx.fill(x + 1, y + 1, x + w, y + h, BEVEL_LIGHT);
        gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, GROOVE);
    }

    public static void well(Gfx gfx, int x, int y, int w, int h) {
        flat(gfx, x, y, w, h);
        gfx.fill(x, y, x + w - 1, y + 1, GROOVE_DARK);
        gfx.fill(x, y, x + 1, y + h - 1, GROOVE_DARK);
        gfx.fill(x + w - 1, y, x + w, y + h, BEVEL_LIGHT);
        gfx.fill(x, y + h - 1, x + w, y + h, BEVEL_LIGHT);
    }

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

    public static void tick(Gfx gfx, int x, int y, int colour) {
        gfx.fill(x, y + 3, x + 1, y + 6, colour);
        gfx.fill(x + 1, y + 4, x + 2, y + 7, colour);
        gfx.fill(x + 2, y + 5, x + 3, y + 8, colour);
        gfx.fill(x + 3, y + 3, x + 4, y + 6, colour);
        gfx.fill(x + 4, y + 1, x + 5, y + 4, colour);
        gfx.fill(x + 5, y, x + 6, y + 2, colour);
    }

    public static void glyph(Gfx gfx, int index, int x, int y) {
        switch (index) {
            case 0 -> chest(gfx, x, y);
            case 1 -> hopper(gfx, x, y);
            case 2 -> lens(gfx, x, y);
            case 3 -> compass(gfx, x, y);
            default -> torch(gfx, x, y);
        }
    }

    private static final int OAK = 0xFF9E6B3F;
    private static final int OAK_DARK = 0xFF6B4527;
    private static final int IRON = 0xFF6E6E6E;
    private static final int IRON_DARK = 0xFF454545;

    private static void chest(Gfx gfx, int x, int y) {
        gfx.fill(x + 1, y + 3, x + 15, y + 15, OAK);
        gfx.fill(x + 1, y + 3, x + 15, y + 8, OAK_DARK);
        gfx.fill(x + 1, y + 8, x + 15, y + 9, OAK_DARK);
        gfx.fill(x + 7, y + 6, x + 10, y + 11, IRON_DARK);
    }

    private static void hopper(Gfx gfx, int x, int y) {
        gfx.fill(x + 1, y + 3, x + 15, y + 6, IRON);
        gfx.fill(x + 3, y + 6, x + 13, y + 9, IRON);
        gfx.fill(x + 5, y + 9, x + 11, y + 11, IRON);
        gfx.fill(x + 6, y + 11, x + 10, y + 15, IRON_DARK);
    }

    private static void lens(Gfx gfx, int x, int y) {
        gfx.fill(x + 3, y + 2, x + 11, y + 3, IRON_DARK);
        gfx.fill(x + 3, y + 9, x + 11, y + 10, IRON_DARK);
        gfx.fill(x + 2, y + 3, x + 3, y + 9, IRON_DARK);
        gfx.fill(x + 11, y + 3, x + 12, y + 9, IRON_DARK);
        gfx.fill(x + 3, y + 3, x + 11, y + 9, 0xFF9FD3E8);
        gfx.fill(x + 10, y + 9, x + 13, y + 12, IRON_DARK);
        gfx.fill(x + 12, y + 11, x + 15, y + 15, IRON_DARK);
    }

    private static void compass(Gfx gfx, int x, int y) {
        gfx.fill(x + 3, y + 2, x + 13, y + 14, IRON_DARK);
        gfx.fill(x + 4, y + 3, x + 12, y + 13, 0xFFD8D8D8);
        gfx.fill(x + 7, y + 4, x + 9, y + 8, 0xFFC03030);
        gfx.fill(x + 7, y + 8, x + 9, y + 12, 0xFF4A4A4A);
    }

    private static void torch(Gfx gfx, int x, int y) {
        gfx.fill(x + 7, y + 6, x + 9, y + 15, OAK_DARK);
        gfx.fill(x + 6, y + 2, x + 10, y + 6, 0xFFD03030);
        gfx.fill(x + 7, y + 3, x + 9, y + 5, 0xFFFF6A6A);
    }

    public static void chevron(Gfx gfx, int x, int y, int colour) {
        gfx.fill(x, y, x + 1, y + 2, colour);
        gfx.fill(x + 1, y + 1, x + 2, y + 3, colour);
        gfx.fill(x + 2, y + 2, x + 3, y + 4, colour);
        gfx.fill(x + 1, y + 3, x + 2, y + 5, colour);
        gfx.fill(x, y + 4, x + 1, y + 6, colour);
    }
}
