package dev.adrian.chestindex.client.ui;

import dev.adrian.chestindex.client.highlight.ContainerHighlight;
import dev.adrian.chestindex.client.platform.Gfx;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.core.highlight.HighlightPulse;
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

    public static void draw(Gfx gfx, AbstractContainerScreen<?> screen, int leftPos, int topPos) {
        if (!ChestIndexConfig.get().highlightFoundSlots) return;

        Set<String> wanted = ContainerHighlight.get().searchedItemIds();
        if (wanted.isEmpty() || !ContainerHighlight.get().isActive()) return;

        Set<Item> targets = resolve(wanted);
        if (targets.isEmpty()) return;

        // Items that are not the answer but hold it - the ender chest, when the
        // search was for something inside it. Marked in the same colour a
        // shulker holding the item is, because they mean the same thing: this
        // is the thing to open next.
        Set<Item> hints = resolveHints(ContainerHighlight.get().hintItemIds());

        byte[] marks = marks(screen, wanted, targets, hints);

        ChestIndexConfig config = ChestIndexConfig.get();

        // The mark settles on the resting colour and stays there. It arrives
        // animated - see settleAmount - because a border that is simply there
        // when the container opens is easy to read as part of the container's
        // own texture, especially in a modded GUI; and it stops moving once
        // that has landed, because a slot that never stops pulsing is one more
        // thing to dismiss rather than an answer.
        float settle = settleAmount(screen);
        int direct = 0xFF000000 | mix(config.otherColour, config.nearestColour, settle);

        // What holds the answer rather than being it - a shulker box, or the
        // ender chest. Steady on the accent colour, so "open this next" does
        // not compete with the mark that means "here it is".
        int inside = 0xFF000000 | config.nearestColour;

        ChestIndexConfig.SlotStyle style = config.slotHighlightStyle();
        boolean outline = style.drawsOutline();
        boolean background = style.drawsBackground();

        java.util.List<Slot> slots = screen.getMenu().slots;
        for (int index = 0; index < slots.size() && index < marks.length; index++) {
            byte mark = marks[index];
            if (mark == NOT_MARKED) continue;

            int colour = mark == MARK_DIRECT ? direct : inside;
            Slot slot = slots.get(index);
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

    /** This slot holds nothing worth marking. */
    private static final byte NOT_MARKED = 0;

    /** This slot holds the item that was searched for. */
    private static final byte MARK_DIRECT = 1;

    /** This slot holds something the item is inside, or the thing to open next. */
    private static final byte MARK_INSIDE = 2;

    /**
     * How often the marking is worked out again.
     *
     * <p>A tenth of a second is well below what anybody notices on a screen
     * they are reaching across, and it is the difference between deciding six
     * hundred times a minute and sixty thousand.
     */
    private static final long RECOMPUTE_MS = 100;

    /** The last decision, the menu it describes, and when it was made. */
    private static byte[] marked = new byte[0];
    private static java.lang.ref.WeakReference<Object> markedMenu = new java.lang.ref.WeakReference<>(null);
    private static Set<String> markedFor = Set.of();
    private static long markedAt;

    /**
     * Which slots to mark, worked out on a timer rather than once per frame.
     *
     * <p>Deciding is not cheap: it descends into every container item on the
     * screen, and a chest of shulkers is fifty-four stacks each holding
     * twenty-seven more - three levels deep, and a bundle has to be copied
     * before it can be read at all. Per frame that was tens of thousands of
     * stack reads a second, to answer a question whose answer only changes
     * when somebody moves an item.
     *
     * <p>What still runs per frame is the pulse, which is the part that has to
     * be smooth. Only the decision behind it is cached.
     *
     * <p>Held by weak reference, so a cached decision cannot keep a closed
     * screen's menu - and through it that container's block entity - alive.
     */
    private static byte[] marks(AbstractContainerScreen<?> screen, Set<String> wanted,
                                Set<Item> targets, Set<Item> hints) {
        java.util.List<Slot> slots = screen.getMenu().slots;
        long now = System.currentTimeMillis();

        boolean current = markedMenu.get() == screen.getMenu()
                && markedFor == wanted
                && marked.length == slots.size()
                && now - markedAt < RECOMPUTE_MS;
        if (current) return marked;

        // Picking the item up or hovering it retires it, which replaces the
        // set this is keyed on - so it belongs inside the recompute rather
        // than beside it, where it was costing two registry lookups and two
        // throwaway strings every frame.
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

    /** The screen the settling animation is timing, and when it opened. */
    private static java.lang.ref.WeakReference<Object> openedMenu =
            new java.lang.ref.WeakReference<>(null);
    private static long openedAt;

    /**
     * How far towards the accent colour the mark is, while it settles.
     *
     * <p>The mark swings between the two colours for the first moment a
     * container is open and then holds the resting one. That is the same curve
     * the world markers pulse on, run once instead of forever: the arrival is
     * what says a mark has appeared, and stopping is what lets the player get
     * on with reading the container.
     *
     * <p>Timed from the menu rather than from the search, so opening a second
     * chest animates again - each one is a fresh thing to find the item in -
     * and re-opening the same one does too, because the menu is new every time
     * the screen is.
     */
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

    /**
     * How long the mark takes to settle.
     *
     * <p>Short. This is an arrival, not an animation to watch - long enough to
     * catch the eye while it is still moving to the container, over before it
     * is something being waited out.
     */
    private static final long SETTLE_MS = 1600L;

    /** Swings through the accent colour before settling. */
    private static final int SETTLE_SWINGS = 2;

    /** Two {@code 0xRRGGBB} colours mixed, {@code amount} 0 giving the first. */
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
