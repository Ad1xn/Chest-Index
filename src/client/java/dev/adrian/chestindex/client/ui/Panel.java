package dev.adrian.chestindex.client.ui;

import dev.adrian.chestindex.client.platform.Gfx;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

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

    public static void tooltip(Gfx gfx, Font font, List<String> lines,
                               int mouseX, int screenW, int screenH,
                               int avoidTop, int avoidBottom) {
        if (lines.isEmpty()) return;

        int textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, font.width(line));
        int textHeight = lines.size() * 10 - 2;

        int textX = mouseX + 12 + textWidth + 4 > screenW ? mouseX - 16 - textWidth : mouseX + 12;
        textX = Math.max(8, textX);

        int textY = avoidTop - 6 - textHeight;
        if (textY < 8) textY = avoidBottom + 6;
        textY = Math.max(8, Math.min(textY, screenH - textHeight - 8));

        tooltipFrame(gfx, textX, textY, textWidth, textHeight);
        for (int i = 0; i < lines.size(); i++) {
            gfx.text(font, Component.literal(lines.get(i)),
                    textX, textY + i * 10, i == 0 ? TEXT_MAIN : TEXT_MUTED);
        }
    }

    public static void tick(Gfx gfx, int x, int y, int colour) {
        gfx.fill(x, y + 3, x + 1, y + 6, colour);
        gfx.fill(x + 1, y + 4, x + 2, y + 7, colour);
        gfx.fill(x + 2, y + 5, x + 3, y + 8, colour);
        gfx.fill(x + 3, y + 3, x + 4, y + 6, colour);
        gfx.fill(x + 4, y + 1, x + 5, y + 4, colour);
        gfx.fill(x + 5, y, x + 6, y + 2, colour);
    }

    private static final Identifier HOPPER = Identifier.parse("minecraft:textures/item/hopper.png");
    private static final Identifier SPYGLASS = Identifier.parse("minecraft:textures/item/spyglass.png");

    private static final Identifier COMPASS = Identifier.parse("minecraft:textures/item/compass_16.png");

    private static final Identifier REDSTONE_TORCH =
            Identifier.parse("minecraft:textures/block/redstone_torch.png");

    private static final Identifier SPECTRAL_ARROW =
            Identifier.parse("minecraft:textures/item/spectral_arrow.png");

    private static final Identifier CHEST =
            Identifier.parse("minecraft:textures/entity/chest/normal.png");

    private static final int CHEST_SHEET = 64;

    private static final int ICON_INSET = 2;

    public static void sectionIcon(Gfx gfx, int index, int x, int y) {
        switch (index) {
            case 0 -> chestFront(gfx, x + 3, y + ICON_INSET);
            case 1 -> icon(gfx, HOPPER, x, y);
            case 2 -> icon(gfx, SPYGLASS, x, y);
            case 3 -> icon(gfx, COMPASS, x, y);
            case 4 -> icon(gfx, SPECTRAL_ARROW, x, y);
            default -> icon(gfx, REDSTONE_TORCH, x, y);
        }
    }

    private static void icon(Gfx gfx, Identifier texture, int x, int y) {
        gfx.blit(texture, x + ICON_INSET, y + ICON_INSET, 0, 0, 16, 16, 16, 16);
    }

    private static void chestFront(Gfx gfx, int x, int y) {
        gfx.blit(CHEST, x, y, 14, 14, 14, 5, CHEST_SHEET, CHEST_SHEET);
        gfx.blit(CHEST, x, y + 5, 14, 33, 14, 10, CHEST_SHEET, CHEST_SHEET);
        gfx.blit(CHEST, x + 6, y + 3, 1, 1, 2, 4, CHEST_SHEET, CHEST_SHEET);
    }

    public static void chevron(Gfx gfx, int x, int y, int colour) {
        gfx.fill(x, y, x + 1, y + 2, colour);
        gfx.fill(x + 1, y + 1, x + 2, y + 3, colour);
        gfx.fill(x + 2, y + 2, x + 3, y + 4, colour);
        gfx.fill(x + 1, y + 3, x + 2, y + 5, colour);
        gfx.fill(x, y + 4, x + 1, y + 6, colour);
    }
}
