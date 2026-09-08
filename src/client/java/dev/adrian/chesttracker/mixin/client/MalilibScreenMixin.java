package dev.adrian.chesttracker.mixin.client;

import dev.adrian.chesttracker.client.LitematicaSearch;
import dev.adrian.chesttracker.client.platform.Gfx;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * Draws this mod's buttons into malilib's screen at the right moment.
 *
 * <p>Fabric's {@code afterRender} event fires after the whole screen is drawn,
 * tooltips included, so buttons drawn there land <em>on top of</em> the item
 * tooltip a material list row shows while it is hovered - which is what the
 * search icon was doing to it.
 *
 * <p>malilib's own draw order is fixed and, happily, identical on both targets:
 *
 * <pre>
 *   background, title, widgets, buttons, contents, text fields,
 *   drawHoveredWidget      &lt;- the row's item tooltip
 *   drawButtonHoverTexts
 *   drawGuiMessages
 * </pre>
 *
 * <p>So the buttons are drawn immediately before {@code drawHoveredWidget}:
 * after everything they should sit on top of, before everything that should
 * sit on top of them.
 *
 * <h2>Why this is safe with Litematica absent</h2>
 *
 * <p>{@link Pseudo} is what makes a mixin against a class that may not exist
 * legal - if malilib is not installed there is nothing to apply it to and it is
 * skipped. {@code require = 0} extends the same tolerance to a malilib that has
 * reshuffled its render method: the injection simply does not happen, and
 * {@link LitematicaSearch} notices it did not and falls back to drawing after
 * the screen, exactly as before. A missing tooltip ordering is worth a
 * fallback; a crash on somebody's schematic GUI is not.
 *
 * <h2>Why the method name is spelled differently per version</h2>
 *
 * <p>The target is an override of vanilla's own screen render, so it carries
 * whatever name that method has at runtime: the intermediary
 * {@code method_25394} on 1.21.11, and {@code extractRenderState} on 26.x,
 * where the deferred-renderer rework renamed it. Neither can be remapped for us
 * - the class is not on the compile classpath - so both are written out
 * literally, which also means this injection does not apply in a development
 * run against named mappings. Only published builds meet a published malilib.
 */
@Pseudo
@Mixin(targets = "fi.dy.masa.malilib.gui.GuiBase", remap = false)
public abstract class MalilibScreenMixin {

    //? if >=26.1 {
    /*@Inject(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lfi/dy/masa/malilib/gui/GuiBase;drawHoveredWidget(Lfi/dy/masa/malilib/render/GuiContext;II)V"),
            require = 0)
    private void chesttracker$drawSearchButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo ci) {
        LitematicaSearch.drawBeforeTooltips(new Gfx(graphics), (Screen) (Object) this, mouseX, mouseY);
    }
    *///?} else {
    @Inject(
            method = "method_25394",
            at = @At(
                    value = "INVOKE",
                    target = "Lfi/dy/masa/malilib/gui/GuiBase;drawHoveredWidget(Lfi/dy/masa/malilib/render/GuiContext;II)V"),
            require = 0)
    private void chesttracker$drawSearchButtons(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo ci) {
        LitematicaSearch.drawBeforeTooltips(new Gfx(graphics), (Screen) (Object) this, mouseX, mouseY);
    }
    //?}
}
