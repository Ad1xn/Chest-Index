package dev.adrian.chestindex.client.ui;

import dev.adrian.chestindex.client.highlight.ContainerHighlight;
import dev.adrian.chestindex.client.platform.Gfx;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.core.highlight.HighlightPulse;
import dev.adrian.chestindex.mixin.client.ContainerScreenAccessor;
import dev.adrian.chestindex.platform.ItemContentsCompat;
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

    private static boolean drawnBeforeTooltips;

    public static void drawBeforeTooltips(Gfx gfx, AbstractContainerScreen<?> screen) {
        drawnBeforeTooltips = true;
        ContainerScreenAccessor access = (ContainerScreenAccessor) screen;
        draw(gfx, screen, access.chestindex$leftPos(), access.chestindex$topPos());
    }

    public static boolean alreadyDrawn() {
        if (!drawnBeforeTooltips) return false;
        drawnBeforeTooltips = false;
        return true;
    }

    public static void draw(Gfx gfx, AbstractContainerScreen<?> screen, int leftPos, int topPos) {
        if (!ChestIndexConfig.get().highlightFoundSlots) return;

        Set<String> wanted = ContainerHighlight.get().searchedItemIds();
        if (wanted.isEmpty() || !ContainerHighlight.get().isActive()) return;

        Set<Item> targets = resolve(wanted);
        if (targets.isEmpty()) return;

        Set<Item> hints = resolveHints(ContainerHighlight.get().hintItemIds());

        byte[] marks = marks(screen, wanted, targets, hints);

        ChestIndexConfig config = ChestIndexConfig.get();

        float settle = settleAmount(screen);
        int direct = 0xFF000000 | mix(config.otherColour, config.nearestColour, settle);

        int inside = 0xFF000000 | config.nearestColour;

        ChestIndexConfig.SlotStyle style = config.slotHighlightStyle();
        boolean outline = style.drawsOutline();
        boolean background = style.drawsBackground();

        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;

        java.util.List<Slot> slots = screen.getMenu().slots;
        for (int index = 0; index < slots.size() && index < marks.length; index++) {
            byte mark = marks[index];
            if (mark == NOT_MARKED) continue;

            int colour = mark == MARK_DIRECT ? direct : inside;
            Slot slot = slots.get(index);
            ItemStack stack = slot.getItem();
            int x = leftPos + slot.x;
            int y = topPos + slot.y;

            if (background) {
                gfx.fill(x, y, x + 16, y + 16, dim(colour));
                gfx.item(stack, x, y);
                gfx.itemDecorations(font, stack, x, y);
            }
            if (outline) {
                gfx.fill(x - 1, y - 1, x + 17, y, colour);
                gfx.fill(x - 1, y + 16, x + 17, y + 17, colour);
                gfx.fill(x - 1, y, x, y + 16, colour);
                gfx.fill(x + 16, y, x + 17, y + 16, colour);
            }
        }
    }

    private static final byte NOT_MARKED = 0;

    private static final byte MARK_DIRECT = 1;

    private static final byte MARK_INSIDE = 2;

    private static final long RECOMPUTE_MS = 100;

    private static byte[] marked = new byte[0];
    private static java.lang.ref.WeakReference<Object> markedMenu = new java.lang.ref.WeakReference<>(null);
    private static Set<String> markedFor = Set.of();
    private static long markedAt;

    private static byte[] marks(AbstractContainerScreen<?> screen, Set<String> wanted,
                                Set<Item> targets, Set<Item> hints) {
        java.util.List<Slot> slots = screen.getMenu().slots;
        long now = System.currentTimeMillis();

        boolean current = markedMenu.get() == screen.getMenu()
                && markedFor == wanted
                && marked.length == slots.size()
                && now - markedAt < RECOMPUTE_MS;
        if (current) return marked;

        retireWhatIsFound(screen, wanted);

        byte[] decided = new byte[slots.size()];
        for (int index = 0; index < slots.size(); index++) {
            ItemStack stack = slots.get(index).getItem();
            if (stack.isEmpty()) continue;
            if (targets.contains(stack.getItem())) {
                decided[index] = MARK_DIRECT;
            } else if (hints.contains(stack.getItem()) || holds(stack, targets, MAX_DEPTH)) {
                decided[index] = MARK_INSIDE;
            }
        }

        marked = decided;
        markedMenu = new java.lang.ref.WeakReference<>(screen.getMenu());
        markedFor = wanted;
        markedAt = now;
        return decided;
    }

    private static int dim(int colour) {
        int opacity = ((colour >>> 24) * BACKGROUND_OPACITY_PERCENT) / 100;
        return (colour & 0x00FFFFFF) | (opacity << 24);
    }

    private static final int BACKGROUND_OPACITY_PERCENT = 75;

    private static void retireWhatIsFound(AbstractContainerScreen<?> screen, Set<String> wanted) {
        if (dev.adrian.chestindex.client.MaterialGoals.isTracking()) return;

        retire(screen.getMenu().getCarried(), wanted);

        Slot hovered = ((dev.adrian.chestindex.mixin.client.ContainerScreenAccessor) screen)
                .chestindex$hoveredSlot();
        if (hovered != null && hovered.hasItem()) retire(hovered.getItem(), wanted);
    }

    private static void retire(ItemStack stack, Set<String> wanted) {
        if (stack == null || stack.isEmpty()) return;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;
        String itemId = id.toString();
        if (wanted.contains(itemId)) ContainerHighlight.get().retire(itemId);
    }

    private static java.lang.ref.WeakReference<Object> openedMenu =
            new java.lang.ref.WeakReference<>(null);
    private static long openedAt;

    private static float settleAmount(AbstractContainerScreen<?> screen) {
        Object menu = screen.getMenu();
        long now = System.currentTimeMillis();

        if (openedMenu.get() != menu) {
            openedMenu = new java.lang.ref.WeakReference<>(menu);
            openedAt = now;
        }

        long since = now - openedAt;
        if (since >= SETTLE_MS) return 0.0f;
        return HighlightPulse.at(since, SETTLE_MS / SETTLE_SWINGS);
    }

    private static final long SETTLE_MS = 1600L;

    private static final int SETTLE_SWINGS = 2;

    private static int mix(int rest, int accent, float amount) {
        if (amount <= 0.0f) return rest & 0x00FFFFFF;
        float t = Math.min(1.0f, amount);
        int red = channel(rest, 16, accent, t);
        int green = channel(rest, 8, accent, t);
        int blue = channel(rest, 0, accent, t);
        return (red << 16) | (green << 8) | blue;
    }

    private static int channel(int rest, int shift, int accent, float t) {
        int from = (rest >> shift) & 0xFF;
        int to = (accent >> shift) & 0xFF;
        return Math.clamp(Math.round(from + (to - from) * t), 0, 255);
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
