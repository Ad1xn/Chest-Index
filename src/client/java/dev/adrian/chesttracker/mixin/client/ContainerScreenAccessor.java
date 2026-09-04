package dev.adrian.chesttracker.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

    @Accessor("hoveredSlot")
    Slot chesttracker$hoveredSlot();

    @Accessor("leftPos")
    int chesttracker$leftPos();

    @Accessor("topPos")
    int chesttracker$topPos();

    @Accessor("imageWidth")
    int chesttracker$imageWidth();
}
