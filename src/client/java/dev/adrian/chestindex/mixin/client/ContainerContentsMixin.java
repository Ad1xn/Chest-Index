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
