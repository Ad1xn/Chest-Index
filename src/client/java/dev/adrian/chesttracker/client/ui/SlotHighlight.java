package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.platform.ItemContentsCompat;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

public final class SlotHighlight {

    private SlotHighlight() {}

    private static final int MAX_DEPTH = 2;

    private static final int PULSE_MS = 1400;

    public static void draw(Gfx gfx, AbstractContainerScreen<?> screen, int leftPos, int topPos) {
        if (!ChestTrackerConfig.get().highlightFoundSlots) return;

        String wanted = ContainerHighlight.get().searchedItemId();
        if (wanted == null || !ContainerHighlight.get().isActive()) return;

        Item target = BuiltInRegistries.ITEM.getValue(Identifier.parse(wanted));
        if (target == null) return;

        ChestTrackerConfig config = ChestTrackerConfig.get();
        int direct = alpha(0xFF000000 | config.nearestColour);
        int inside = alpha(0xFF000000 | config.otherColour);

        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            int colour;
            if (stack.getItem() == target) {
                colour = direct;
            } else if (holds(stack, target, MAX_DEPTH)) {
                colour = inside;
            } else {
                continue;
            }

            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            gfx.fill(x - 1, y - 1, x + 17, y, colour);
            gfx.fill(x - 1, y + 16, x + 17, y + 17, colour);
            gfx.fill(x - 1, y, x, y + 16, colour);
            gfx.fill(x + 16, y, x + 17, y + 16, colour);
        }
    }

    private static int alpha(int colour) {
        double phase = (System.currentTimeMillis() % PULSE_MS) / (double) PULSE_MS;
        float wave = (float) (0.55 + 0.45 * Math.sin(phase * Math.PI * 2));
        return (colour & 0x00FFFFFF) | ((int) (wave * 255) << 24);
    }

    private static boolean holds(ItemStack stack, Item target, int depth) {
        if (depth <= 0) return false;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null
                && ItemContentsCompat.stacks(contents)
                        .anyMatch(inner -> inner.getItem() == target || holds(inner, target, depth - 1))) {
            return true;
        }

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundle != null && bundle.itemCopyStream()
                .anyMatch(inner -> inner.getItem() == target || holds(inner, target, depth - 1));
    }
}
