package dev.adrian.chestindex.platform;

import dev.adrian.chestindex.core.model.StackDetail;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.store.StringPalette;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.ItemContainerContents;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a live {@link Container} into the index's own representation.
 *
 * <p>The offline region scanner produces exactly the same {@link StackEntry}
 * shape from serialised NBT, so the two sources are interchangeable and the
 * index never has to care which one a record came from.
 */
public final class LiveContainerReader {

    /** Deeper than this is broken or hostile data, not a real shulker chain. */
    private static final int MAX_NESTING = 8;

    private LiveContainerReader() {}

    /** Flattens a container's stacks, descending into nested storage. */
    public static List<StackEntry> read(Container container, StringPalette palette) {
        List<StackEntry> entries = new ArrayList<>();
        int size = container.getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack == null || stack.isEmpty()) continue;
            appendStack(stack, entries, 0, palette);
        }
        return entries;
    }

    /**
     * The same flattening, from loose stacks rather than a {@link Container}.
     *
     * <p>For the client-side index, which has no container to read: a client
     * connected to a vanilla server never receives the block entity's
     * inventory, only the slots of a menu the player has open. Those are the
     * same stacks and must flatten the same way, so this shares the descent
     * rather than repeating it - a shulker box inside a chest has to count as
     * nested whichever side observed it.
     */
    public static List<StackEntry> read(List<ItemStack> stacks, StringPalette palette) {
        List<StackEntry> entries = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            appendStack(stack, entries, 0, palette);
        }
        return entries;
    }

    /**
     * What tells this stack apart from another of the same item.
     *
     * <p>Read from the components rather than from the rendered tooltip: a
     * tooltip is built for a screen, in the player's language, out of whatever
     * every installed mod wants to add to it - and it can only be built where
     * there is a client. The components are the same on both sides and say the
     * same thing in every language.
     *
     * <p>Empty for almost every stack in a world, which is what keeps this
     * affordable: the list costs one byte in the file when there is nothing in
     * it.
     */
    private static List<Integer> detailsOf(ItemStack stack, String customName, StringPalette palette) {
        // The overwhelming majority of stacks in a world carry none of these.
        // Asking first costs four component lookups either way; not asking
        // cost an ArrayList per stack on every read path in the mod.
        if (customName == null
                && stack.get(DataComponents.ENCHANTMENTS) == null
                && stack.get(DataComponents.STORED_ENCHANTMENTS) == null
                && stack.get(DataComponents.POTION_CONTENTS) == null
                && stack.get(DataComponents.LORE) == null) {
            return List.of();
        }

        List<Integer> details = new ArrayList<>(4);

        addEnchantments(stack.get(DataComponents.ENCHANTMENTS), details, palette);
        // A book's enchantments are stored rather than applied, and looking for
        // "mending" plainly means both.
        addEnchantments(stack.get(DataComponents.STORED_ENCHANTMENTS), details, palette);

        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            potion.potion().ifPresent(holder -> holder.unwrapKey().ifPresent(key ->
                    add(details, palette, StackDetail.potion(key.identifier().toString()))));
        }

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            int lines = 0;
            for (Component line : lore.lines()) {
                if (lines++ >= StackDetail.MAX_LORE_LINES) break;
                add(details, palette, StackDetail.lore(line.getString()));
            }
        }

        // Kept as a detail as well as on the entry, so that one search for
        // words in a tooltip covers a name somebody wrote on an anvil without
        // needing to know that names are stored somewhere else.
        add(details, palette, StackDetail.name(customName));

        return details;
    }

    private static void addEnchantments(ItemEnchantments enchantments,
                                        List<Integer> details, StringPalette palette) {
        if (enchantments == null || enchantments.isEmpty()) return;
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            holder.unwrapKey().ifPresent(key ->
                    add(details, palette, StackDetail.enchantment(key.identifier().toString())));
        }
    }

    private static void add(List<Integer> details, StringPalette palette, String detail) {
        if (detail == null) return;
        int id = palette.intern(detail);
        // A stack can carry the same thing twice - a book with an enchantment
        // both applied and stored - and the list is walked per query.
        if (!details.contains(id)) details.add(id);
    }

    /**
     * The registry id of an item, worked out once per item rather than per
     * stack.
     *
     * <p>{@code ResourceLocation.toString()} joins its namespace and path into
     * a fresh string every time it is asked, and this is asked for every stack
     * in every container on every read path - a chunk unloading, the dirty
     * drain, a container being opened. The set of items in a game is fixed
     * once the registries are frozen, so the answer can simply be kept.
     *
     * <p>Identity-keyed because items are singletons, which makes the lookup a
     * reference comparison rather than a hash of the item's own fields.
     */
    private static final Map<Item, String> ITEM_IDS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private static String idOf(Item item) {
        String cached = ITEM_IDS.get(item);
        if (cached != null) return cached;

        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null) return null;
        String id = key.toString();
        ITEM_IDS.put(item, id);
        return id;
    }

    private static void appendStack(ItemStack stack, List<StackEntry> out, int depth, StringPalette palette) {
        if (depth > MAX_NESTING) return;

        // Checked here rather than only at the two entry points, because the
        // recursion has its own sources. A shulker box's CONTAINER component is
        // a fixed-length list with an entry per slot, and the empty ones are
        // real ItemStacks holding air - so descending into a half-full shulker
        // used to intern "minecraft:air" and file a stack of zero under it.
        // That reached the grid as an "Air" item saying "0 in 1".
        if (stack == null || stack.isEmpty()) return;

        String itemId = idOf(stack.getItem());
        if (itemId == null) return;
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        String name = customName == null ? null : customName.getString();
        out.add(new StackEntry(
                palette.intern(itemId),
                stack.getCount(),
                depth,
                name,
                detailsOf(stack, name, palette)));

        // A shulker box in a chest: its contents are searchable too, flattened
        // with a depth so the UI can say where the item actually is.
        ItemContainerContents nested = stack.get(DataComponents.CONTAINER);
        if (nested != null) {
            ItemContentsCompat.stacks(nested).forEach(inner -> appendStack(inner, out, depth + 1, palette));
        }

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            // itemCopyStream() is identical on both target versions, unlike
            // items(), whose element type changed after 1.21.11.
            bundle.itemCopyStream().forEach(inner -> appendStack(inner, out, depth + 1, palette));
        }
    }
}
