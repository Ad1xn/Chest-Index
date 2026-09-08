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
