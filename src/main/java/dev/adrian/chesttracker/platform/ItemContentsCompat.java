package dev.adrian.chesttracker.platform;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.stream.Stream;

public final class ItemContentsCompat {

    private ItemContentsCompat() {}

    public static Stream<ItemStack> stacks(ItemContainerContents contents) {
        //? if >=26.1 {
        /*return contents.allItemsCopyStream();
        *///?} else {
        return contents.stream();
        //?}
    }
}
