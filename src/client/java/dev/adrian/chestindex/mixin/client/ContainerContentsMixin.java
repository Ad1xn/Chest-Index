package dev.adrian.chestindex.mixin.client;

import dev.adrian.chestindex.client.platform.Gfx;
import dev.adrian.chestindex.client.ui.SlotHighlight;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * Marks the found slots inside the container window's own drawing pass.
 *
 * <p>The mark used to be drawn from Fabric's after-render event, which fires
 * once the screen is finished. On a screen that draws straight to the window
 * that is merely last; on this one it is too late. Both targets run the
 * deferred renderer, where {@code Screen.renderWithTooltipAndSubtitles} calls
 * {@code render} and only then {@code GuiGraphics.renderDeferredElements()} -
 * and an item tooltip is one of those deferred elements. Anything drawn after
 * {@code render} returns therefore belongs to a later stratum than the tooltip
 * and lands on top of it.
 *
 * <p>Which is exactly what was seen: the mark redraws the item over its wash,
 * so hovering a slot while a search was live put stray item icons across the
 * tooltip box.
 *
 * <p>{@code renderContents} is where the slots themselves are drawn - labels,
 * then the slot highlight behind, the slots, and the slot highlight in front.
 * Going in at its tail puts the mark immediately after the slot it marks and
 * before everything the screen draws afterwards: the carried item, and the
 * tooltip.
 *
 * <p>{@code require = 0} because {@link SlotHighlight} keeps the old
 * after-render path as a fallback and stands it down only when this injection
 * reports having run. A version that reshuffles this method gets the marks
 * drawn late again, which is a cosmetic fault; it does not get a crash on
 * opening a chest.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerContentsMixin {

    //? if >=26.1 {
    /*@Inject(method = "extractContents", at = @At("TAIL"), require = 0)
    private void chestindex$markSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                      float partialTick, CallbackInfo ci) {
        SlotHighlight.drawBeforeTooltips(
                new Gfx(graphics), (AbstractContainerScreen<?>) (Object) this);
    }
    *///?} else {
    @Inject(method = "renderContents", at = @At("TAIL"), require = 0)
    private void chestindex$markSlots(GuiGraphics graphics, int mouseX, int mouseY,
                                      float partialTick, CallbackInfo ci) {
        SlotHighlight.drawBeforeTooltips(
                new Gfx(graphics), (AbstractContainerScreen<?>) (Object) this);
    }
    //?}
}
