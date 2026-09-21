package dev.adrian.chestindex.platform;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.stream.Stream;

public final class ItemContentsCompat {
    private ItemContentsCompat() {}

    public static Stream<ItemStack> stacks(ItemContainerContents contents) {
        //? if >=26.3 {
        /*return contents.itemCopies();
        *///?} else if >=26.1 {
        /*return contents.allItemsCopyStream();
        *///?} else {
        return contents.stream();
        //?}
    }

    public static Stream<ItemStack> stacks(BundleContents bundle) {
        //? if >=26.3 {
        /*return bundle.itemCopies();
        *///?} else {
        return bundle.itemCopyStream();
        //?}
    }
}
