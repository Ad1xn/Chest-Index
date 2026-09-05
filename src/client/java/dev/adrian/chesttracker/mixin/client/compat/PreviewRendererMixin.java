package dev.adrian.chesttracker.mixin.client.compat;

import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.client.ui.PreviewHighlight;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

@Mixin(targets = {
        "com.misterpemodder.shulkerboxtooltip.impl.renderer.ModPreviewRenderer",
        "com.misterpemodder.shulkerboxtooltip.impl.renderer.VanillaPreviewRenderer",
}, remap = false)
public class PreviewRendererMixin {

    //? if >=26.1 {
    /*@Inject(method = "drawSlot", at = @At("TAIL"), require = 0)
    private void chesttracker$markSearched(ItemStack stack, int x, int y,
                                           GuiGraphicsExtractor graphics, Font font,
                                           int slot, boolean a, boolean b, CallbackInfo ci) {
        PreviewHighlight.slot(new Gfx(graphics), stack, x, y);
    }
    *///?} else {
    @Inject(method = "drawSlot", at = @At("TAIL"), require = 0)
    private void chesttracker$markSearched(ItemStack stack, int x, int y,
                                           GuiGraphics graphics, Font font,
                                           int slot, boolean a, boolean b, CallbackInfo ci) {
        PreviewHighlight.slot(new Gfx(graphics), stack, x, y);
    }
    //?}
}
