package dev.adrian.chestindex.client.platform;

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

    public void textFromCorner(Font font, String text, int rightX, int bottomY,
                               float scale, int colour) {
        var pose = raw.pose();
        pose.pushMatrix();
        pose.translate(rightX, bottomY);
        pose.scale(scale, scale);
        raw.text(font, text, -font.width(text), -font.lineHeight, colour);
        pose.popMatrix();
    }

    public void fill(int x1, int y1, int x2, int y2, int colour) {
        raw.fill(x1, y1, x2, y2, colour);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        raw.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    public void item(ItemStack stack, int x, int y) {
        raw.item(stack, x, y);
    }

    public void item(ItemStack stack, int x, int y, float scale) {
        var pose = raw.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        raw.item(stack, 0, 0);
        pose.popMatrix();
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        raw.itemDecorations(font, stack, x, y);
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

    public void blitStretched(Identifier texture, int x, int y, float u, float v,
                              int width, int height, int uWidth, int vHeight,
                              int textureWidth, int textureHeight) {
        raw.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v,
                width, height, uWidth, vHeight, textureWidth, textureHeight);
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

    public void textFromCorner(Font font, String text, int rightX, int bottomY,
                               float scale, int colour) {
        var pose = raw.pose();
        pose.pushMatrix();
        pose.translate(rightX, bottomY);
        pose.scale(scale, scale);
        raw.drawString(font, text, -font.width(text), -font.lineHeight, colour);
        pose.popMatrix();
    }

    public void fill(int x1, int y1, int x2, int y2, int colour) {
        raw.fill(x1, y1, x2, y2, colour);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        raw.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    public void item(ItemStack stack, int x, int y) {
        raw.renderItem(stack, x, y);
    }

    public void item(ItemStack stack, int x, int y, float scale) {
        var pose = raw.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        raw.renderItem(stack, 0, 0);
        pose.popMatrix();
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        raw.renderItemDecorations(font, stack, x, y);
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

    public void blitStretched(Identifier texture, int x, int y, float u, float v,
                              int width, int height, int uWidth, int vHeight,
                              int textureWidth, int textureHeight) {
        raw.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v,
                width, height, uWidth, vHeight, textureWidth, textureHeight);
    }
    //?}
}
