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

import java.util.Set;

public final class SlotHighlight {

    private SlotHighlight() {}

    private static final int MAX_DEPTH = 3;

    private static final int PULSE_MS = 1400;

    public static void draw(Gfx gfx, AbstractContainerScreen<?> screen, int leftPos, int topPos) {
        if (!ChestTrackerConfig.get().highlightFoundSlots) return;

        Set<String> wanted = ContainerHighlight.get().searchedItemIds();
        if (wanted.isEmpty() || !ContainerHighlight.get().isActive()) return;

        Set<Item> targets = resolve(wanted);
        if (targets.isEmpty()) return;

        retireWhatIsFound(screen, wanted);

        Set<Item> hints = resolveHints(ContainerHighlight.get().hintItemIds());

        ChestTrackerConfig config = ChestTrackerConfig.get();
        int direct = alpha(0xFF000000 | config.nearestColour);
        int inside = alpha(0xFF000000 | config.otherColour);

        ChestTrackerConfig.SlotStyle style = config.slotHighlightStyle();
        boolean outline = style.drawsOutline();
        boolean background = style.drawsBackground();

        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            int colour;
            if (targets.contains(stack.getItem())) {
                colour = direct;
            } else if (hints.contains(stack.getItem()) || holds(stack, targets, MAX_DEPTH)) {
                colour = inside;
            } else {
                continue;
            }

            int x = leftPos + slot.x;
            int y = topPos + slot.y;

            if (background) {
                gfx.fill(x, y, x + 16, y + 16, dim(colour));
            }
            if (outline) {
                gfx.fill(x - 1, y - 1, x + 17, y, colour);
                gfx.fill(x - 1, y + 16, x + 17, y + 17, colour);
                gfx.fill(x - 1, y, x, y + 16, colour);
                gfx.fill(x + 16, y, x + 17, y + 16, colour);
            }
        }
    }

    private static int dim(int colour) {
        int opacity = ((colour >>> 24) * BACKGROUND_OPACITY_PERCENT) / 100;
        return (colour & 0x00FFFFFF) | (opacity << 24);
    }

    private static final int BACKGROUND_OPACITY_PERCENT = 45;

    private static void retireWhatIsFound(AbstractContainerScreen<?> screen, Set<String> wanted) {
        if (dev.adrian.chesttracker.client.MaterialGoals.isTracking()) return;

        retire(screen.getMenu().getCarried(), wanted);

        Slot hovered = ((dev.adrian.chesttracker.mixin.client.ContainerScreenAccessor) screen)
                .chesttracker$hoveredSlot();
        if (hovered != null && hovered.hasItem()) retire(hovered.getItem(), wanted);
    }

    private static void retire(ItemStack stack, Set<String> wanted) {
        if (stack == null || stack.isEmpty()) return;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;
        String itemId = id.toString();
        if (wanted.contains(itemId)) ContainerHighlight.get().retire(itemId);
    }

    private static int alpha(int colour) {
        double phase = (System.currentTimeMillis() % PULSE_MS) / (double) PULSE_MS;
        float wave = (float) (0.55 + 0.45 * Math.sin(phase * Math.PI * 2));
        return (colour & 0x00FFFFFF) | ((int) (wave * 255) << 24);
    }

    private static boolean holds(ItemStack stack, Set<Item> targets, int depth) {
        if (depth <= 0) return false;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null
                && ItemContentsCompat.stacks(contents)
                        .anyMatch(inner -> targets.contains(inner.getItem())
                                || holds(inner, targets, depth - 1))) {
            return true;
        }

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundle != null && bundle.itemCopyStream()
                .anyMatch(inner -> targets.contains(inner.getItem())
                        || holds(inner, targets, depth - 1));
    }

    private static Set<String> cachedIds = Set.of();
    private static Set<Item> cachedItems = Set.of();

    private static Set<String> cachedHintIds = Set.of();
    private static Set<Item> cachedHintItems = Set.of();

    private static Set<Item> resolveHints(Set<String> ids) {
        if (ids.isEmpty()) return Set.of();
        if (ids == cachedHintIds) return cachedHintItems;

        Set<Item> items = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (String id : ids) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) continue;
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            if (item != null) items.add(item);
        }
        cachedHintIds = ids;
        cachedHintItems = items;
        return items;
    }

    private static Set<Item> resolve(Set<String> ids) {
        if (ids == cachedIds) return cachedItems;

        Set<Item> items = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (String id : ids) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) continue;
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            if (item != null) items.add(item);
        }
        cachedIds = ids;
        cachedItems = items;
        return items;
    }
}
