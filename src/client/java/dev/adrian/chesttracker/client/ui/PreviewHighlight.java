package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class PreviewHighlight {

    private PreviewHighlight() {}

    private static final int PULSE_MS = 1400;

    public static void slot(Gfx gfx, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        if (!ChestTrackerConfig.get().highlightFoundSlots) return;

        ContainerHighlight highlight = ContainerHighlight.get();
        String wanted = highlight.searchedItemId();
        if (wanted == null || !highlight.isActive()) return;

        Item target = BuiltInRegistries.ITEM.getValue(Identifier.parse(wanted));
        if (target == null || stack.getItem() != target) return;

        int colour = pulse(0xFF000000 | ChestTrackerConfig.get().nearestColour);
        gfx.fill(x - 1, y - 1, x + 17, y, colour);
        gfx.fill(x - 1, y + 16, x + 17, y + 17, colour);
        gfx.fill(x - 1, y, x, y + 16, colour);
        gfx.fill(x + 16, y, x + 17, y + 16, colour);
    }

    private static int pulse(int colour) {
        double phase = (System.currentTimeMillis() % PULSE_MS) / (double) PULSE_MS;
        float wave = (float) (0.55 + 0.45 * Math.sin(phase * Math.PI * 2));
        return (colour & 0x00FFFFFF) | ((int) (wave * 255) << 24);
    }
}
