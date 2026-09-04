package dev.adrian.chesttracker.client.platform;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

public final class Gfx {

    //? if >=26.1 {
    /*private final GuiGraphicsExtractor raw;

    public Gfx(GuiGraphicsExtractor raw) {
        this.raw = raw;
    }

    public void text(Font font, Component text, int x, int y, int colour) {
        raw.text(font, text, x, y, colour);
    }

    public void text(Font font, String text, int x, int y, int colour) {
        raw.text(font, text, x, y, colour);
    }

    public void fill(int x1, int y1, int x2, int y2, int colour) {
        raw.fill(x1, y1, x2, y2, colour);
    }

    public void item(ItemStack stack, int x, int y) {
        raw.item(stack, x, y);
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        raw.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        raw.disableScissor();
    }

    public void blit(Identifier texture, int x, int y, float u, float v,
                     int width, int height, int textureWidth, int textureHeight) {
        raw.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }
    *///?} else {
    private final GuiGraphics raw;

    public Gfx(GuiGraphics raw) {
        this.raw = raw;
    }

    public void text(Font font, Component text, int x, int y, int colour) {
        raw.drawString(font, text, x, y, colour);
    }

    public void text(Font font, String text, int x, int y, int colour) {
        raw.drawString(font, text, x, y, colour);
    }

    public void fill(int x1, int y1, int x2, int y2, int colour) {
        raw.fill(x1, y1, x2, y2, colour);
    }

    public void item(ItemStack stack, int x, int y) {
        raw.renderItem(stack, x, y);
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        raw.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        raw.disableScissor();
    }

    public void blit(Identifier texture, int x, int y, float u, float v,
                     int width, int height, int textureWidth, int textureHeight) {
        raw.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }
    //?}
}
