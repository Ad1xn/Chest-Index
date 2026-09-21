package dev.adrian.chestindex.client.ui;

import dev.adrian.chestindex.client.platform.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class VanillaButton {
    private VanillaButton() {}

    private static final Identifier BUTTON =
            Identifier.parse("minecraft:textures/gui/sprites/widget/button.png");
    private static final Identifier BUTTON_HIGHLIGHTED =
            Identifier.parse("minecraft:textures/gui/sprites/widget/button_highlighted.png");

    private static final int SHEET_W = 200;
    private static final int SHEET_H = 20;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DISABLED = 0xFFA0A0A0;

    public static final int ICON = 0xFFE0E0E0;

    public static void draw(Gfx gfx, int x, int y, int width, int height, boolean hovered) {
        if (width < 2 || height < 2) return;

        Identifier texture = hovered ? BUTTON_HIGHLIGHTED : BUTTON;

        int leftW = Math.min(width / 2, SHEET_W / 2);
        int rightW = Math.min(width - leftW, SHEET_W - leftW);
        int topH = Math.min(height / 2, SHEET_H / 2);
        int bottomH = Math.min(height - topH, SHEET_H - topH);

        gfx.blit(texture, x, y, 0, 0, leftW, topH, SHEET_W, SHEET_H);
        gfx.blit(texture, x + width - rightW, y, SHEET_W - rightW, 0, rightW, topH, SHEET_W, SHEET_H);
        gfx.blit(texture, x, y + height - bottomH, 0, SHEET_H - bottomH, leftW, bottomH, SHEET_W, SHEET_H);
        gfx.blit(texture, x + width - rightW, y + height - bottomH,
                SHEET_W - rightW, SHEET_H - bottomH, rightW, bottomH, SHEET_W, SHEET_H);
    }

    public static void draw(Gfx gfx, int x, int y, int width, int height,
                            boolean hovered, String label) {
        draw(gfx, x, y, width, height, hovered);

        Minecraft client = Minecraft.getInstance();
        int textX = x + (width - client.font.width(label)) / 2;
        int textY = y + (height - 8) / 2;
        gfx.text(client.font, Component.literal(label), textX, textY, TEXT);
    }

    public static int widthFor(String label) {
        return Minecraft.getInstance().font.width(label) + 10;
    }

    public static void magnifier(Gfx gfx, int x, int y, int colour) {
        gfx.fill(x + 1, y, x + 4, y + 1, colour);
        gfx.fill(x + 1, y + 4, x + 4, y + 5, colour);
        gfx.fill(x, y + 1, x + 1, y + 4, colour);
        gfx.fill(x + 4, y + 1, x + 5, y + 4, colour);
        gfx.fill(x + 4, y + 4, x + 6, y + 6, colour);
        gfx.fill(x + 5, y + 5, x + 8, y + 8, colour);
    }

    public static void playClick() {
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
