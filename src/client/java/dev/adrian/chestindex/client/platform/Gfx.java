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

/**
 * The one place the GUI rendering rewrite is allowed to show.
 *
 * <p>26.x replaced immediate-mode screen drawing with a deferred render-state
 * model, almost certainly to support the Vulkan backend. The changes are
 * pervasive but mechanical - every call this mod needs kept its signature and
 * changed only its name:
 *
 * <pre>
 *   GuiGraphics        -&gt; GuiGraphicsExtractor
 *   drawString(...)    -&gt; text(...)
 *   renderItem(...)    -&gt; item(...)
 *   Screen.render(...) -&gt; Screen.extractRenderState(...)
 * </pre>
 *
 * <p>Wrapping them here means the screens themselves are written once. The only
 * other place that has to know is the single overridden entry point in
 * {@link dev.adrian.chestindex.client.ui.ChestIndexScreen}, because that is
 * a method signature and cannot be hidden behind a facade.
 *
 * <p>Note that {@code blit} is identical on both versions, so vanilla's own GUI
 * textures can be drawn from one code path.
 *
 * <p>Inside the version-conditional blocks below, only {@code //} comments are
 * used: the inactive branch is itself wrapped in a block comment, and a nested
 * {@code *}{@code /} would close it early.
 */
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

    // Text shrunk about its bottom-right corner, for a label with more
    // characters than its box has room for. The corner is the anchor because
    // the callers right-align: a count sits against the right edge of its slot
    // whatever it says, so that edge is the one that must not move.
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

    // Vertical only, top colour to bottom colour, and identical on both
    // versions. The colour picker draws its saturation/value square out of
    // these one column at a time, which is a hundred and twenty calls a frame
    // instead of the fourteen thousand a per-pixel fill would take.
    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        raw.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    public void item(ItemStack stack, int x, int y) {
        raw.item(stack, x, y);
    }

    // An item drawn larger than the sixteen pixels vanilla gives it, about its
    // own top-left corner. The block models the game renders into an item slot
    // are the only three-dimensional pictures of a chest a screen can get
    // hold of, and at sixteen pixels a chest is too small to show a marker
    // drawn around it. The pose stack is two-dimensional on both targets, so
    // this scales the finished picture rather than the model - which is all
    // that is wanted here: bigger, not turned.
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

    // Draws part of a texture, so the panel and slots are vanilla's own art.
    public void blit(Identifier texture, int x, int y, float u, float v,
                     int width, int height, int textureWidth, int textureHeight) {
        raw.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    // The same, stretching a source region to a different size. The window is
    // widened for a scrollbar column vanilla's art does not cover, and the gap
    // is filled by repeating one uniform pixel column out of that art. Drawing
    // it a pixel at a time cost a blit per pixel per band per frame - several
    // hundred a frame for one window. Stretching the same column is one call
    // and, because GUI textures are sampled nearest-neighbour, the same pixels:
    // the fill still comes from whatever texture a resource pack supplies.
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

    // Text shrunk about its bottom-right corner, for a label with more
    // characters than its box has room for. The corner is the anchor because
    // the callers right-align: a count sits against the right edge of its slot
    // whatever it says, so that edge is the one that must not move.
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

    // Vertical only, top colour to bottom colour, and identical on both
    // versions. The colour picker draws its saturation/value square out of
    // these one column at a time, which is a hundred and twenty calls a frame
    // instead of the fourteen thousand a per-pixel fill would take.
    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        raw.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    public void item(ItemStack stack, int x, int y) {
        raw.renderItem(stack, x, y);
    }

    // An item drawn larger than the sixteen pixels vanilla gives it, about its
    // own top-left corner. The block models the game renders into an item slot
    // are the only three-dimensional pictures of a chest a screen can get
    // hold of, and at sixteen pixels a chest is too small to show a marker
    // drawn around it. The pose stack is two-dimensional on both targets, so
    // this scales the finished picture rather than the model - which is all
    // that is wanted here: bigger, not turned.
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

    // Draws part of a texture, so the panel and slots are vanilla's own art.
    public void blit(Identifier texture, int x, int y, float u, float v,
                     int width, int height, int textureWidth, int textureHeight) {
        raw.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    // The same, stretching a source region to a different size. The window is
    // widened for a scrollbar column vanilla's art does not cover, and the gap
    // is filled by repeating one uniform pixel column out of that art. Drawing
    // it a pixel at a time cost a blit per pixel per band per frame - several
    // hundred a frame for one window. Stretching the same column is one call
    // and, because GUI textures are sampled nearest-neighbour, the same pixels:
    // the fill still comes from whatever texture a resource pack supplies.
    public void blitStretched(Identifier texture, int x, int y, float u, float v,
                              int width, int height, int uWidth, int vHeight,
                              int textureWidth, int textureHeight) {
        raw.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v,
                width, height, uWidth, vHeight, textureWidth, textureHeight);
    }
    //?}
}
