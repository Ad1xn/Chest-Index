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

/**
 * Marks the slots holding what was searched for, in whatever container is open.
 *
 * <p>Walking to the right chest is only most of the answer. Opening it leaves
 * the player scanning fifty-four slots for the thing they just asked the mod to
 * find, which is the search working right up until the moment it matters.
 *
 * <p>Three cases, and the second is the one that makes this worth having:
 * <ul>
 *   <li>the item is loose in the container - mark it;
 *   <li>the item is inside a shulker box or a bundle in the container - mark
 *       <em>that</em>, in a second colour, because opening it is the next step
 *       rather than the answer;
 *   <li>the shulker is then opened, which is just another container screen, so
 *       the first case marks the item without knowing anything new.
 * </ul>
 *
 * <p>That last point is why this reads the open screen rather than being told
 * about it: a shulker opened from an inventory, placed and opened as a block,
 * or opened by another mod's viewer all arrive here the same way.
 */
public final class SlotHighlight {

    private SlotHighlight() {}

    /**
     * How deep to look inside container items.
     *
     * <p>Three, because the real chain is longer than it looks: an item in a
     * bundle, in a shulker box, in the ender chest. Two stopped one level short
     * of the bundle and left it unmarked in a shulker that was marked, which
     * reads as the mod having lost the thread at the last step.
     */
    private static final int MAX_DEPTH = 3;

    private static final int PULSE_MS = 1400;

    public static void draw(Gfx gfx, AbstractContainerScreen<?> screen, int leftPos, int topPos) {
        if (!ChestTrackerConfig.get().highlightFoundSlots) return;

        Set<String> wanted = ContainerHighlight.get().searchedItemIds();
        if (wanted.isEmpty() || !ContainerHighlight.get().isActive()) return;

        Set<Item> targets = resolve(wanted);
        if (targets.isEmpty()) return;

        retireWhatIsFound(screen, wanted);

        // Items that are not the answer but hold it - the ender chest, when the
        // search was for something inside it. Marked in the same colour a
        // shulker holding the item is, because they mean the same thing: this
        // is the thing to open next.
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

            // Over the item rather than behind it - this runs after the screen
            // has finished drawing, which is the only point a mod can add to
            // it. So the wash is held well under the pulse's own alpha: the
            // slot still has to show its item and its stack count, and at full
            // strength it would hide both. Vanilla tints its own hovered slot
            // the same way, over the top and half transparent.
            // Drawn before the outline so it cannot paint over it.
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

    /**
     * The same colour at a fraction of its opacity, for the background wash.
     *
     * <p>An outline can be fully opaque because it sits beside the item; a fill
     * covers it, so it has to stay a tint rather than become a block of colour
     * with an item lost somewhere inside it.
     */
    private static int dim(int colour) {
        int opacity = ((colour >>> 24) * BACKGROUND_OPACITY_PERCENT) / 100;
        return (colour & 0x00FFFFFF) | (opacity << 24);
    }

    /** How much of the mark's opacity the background wash gets. */
    private static final int BACKGROUND_OPACITY_PERCENT = 45;

    /**
     * Stops marking anything the player has evidently found.
     *
     * <p>Two signals, and both mean the same thing: the stack is on the cursor,
     * or the cursor is on the slot. Either way the search has been answered and
     * the mark is finished - leaving it pulsing turns a helpful mark into one
     * more thing to dismiss.
     *
     * <p>Not while a material list is being gathered. That search retires an
     * item on a count - enough of it, not any of it - and hovering one stack of
     * cobblestone does not mean the schematic has its cobblestone.
     */
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

    /**
     * Fades the mark in and out.
     *
     * <p>A static border on a slot is easy to mistake for part of the container
     * texture, especially in a modded GUI. Movement is what makes it read as
     * something the mod is saying rather than something that was always there.
     */
    private static int alpha(int colour) {
        double phase = (System.currentTimeMillis() % PULSE_MS) / (double) PULSE_MS;
        float wave = (float) (0.55 + 0.45 * Math.sin(phase * Math.PI * 2));
        return (colour & 0x00FFFFFF) | ((int) (wave * 255) << 24);
    }



    /**
     * Whether a container item holds the wanted item, at any depth.
     *
     * <p>Reads the stack's own components rather than the index: the index
     * knows what was in a shulker when it was last seen, and the one in this
     * slot is the truth. Covers both shulker boxes and bundles, which store
     * their contents under different components.
     */
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
        // itemCopyStream() rather than items(): the latter hands back
        // ItemStackTemplate on 26.2 and ItemStack on 1.21.11, while this one
        // is a Stream<ItemStack> on both and needs no shim.
        return bundle != null && bundle.itemCopyStream()
                .anyMatch(inner -> targets.contains(inner.getItem())
                        || holds(inner, targets, depth - 1));
    }

    /** The last set of ids resolved, and what they resolved to. */
    private static Set<String> cachedIds = Set.of();
    private static Set<Item> cachedItems = Set.of();

    /** The same, for the hint set, which changes independently of the target set. */
    private static Set<String> cachedHintIds = Set.of();
    private static Set<Item> cachedHintItems = Set.of();

    /** As {@link #resolve}, cached separately so the two sets do not evict each other. */
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

    /**
     * Turns registry ids into items, remembering the last answer.
     *
     * <p>This runs every frame, for a set that changes only when the player
     * starts a new search. The first version resolved a registry id per stack
     * per frame and built a string for each - hundreds of lookups and hundreds
     * of throwaway strings every frame for a chest of shulkers, which is
     * exactly what made the GUI feel heavy. Caching on the set's identity works
     * because the highlight hands back an immutable set that is replaced, never
     * edited.
     */
    private static Set<Item> resolve(Set<String> ids) {
        if (ids == cachedIds) return cachedItems;

        Set<Item> items = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (String id : ids) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) continue;
            // A server can name an item this client does not have, so the
            // lookup genuinely can miss.
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            if (item != null) items.add(item);
        }
        cachedIds = ids;
        cachedItems = items;
        return items;
    }
}
