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

public final class LiveContainerReader {
    private static final int MAX_NESTING = 8;

    private LiveContainerReader() {}

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

    public static List<StackEntry> read(List<ItemStack> stacks, StringPalette palette) {
        List<StackEntry> entries = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            appendStack(stack, entries, 0, palette);
        }
        return entries;
    }

    private static List<Integer> detailsOf(ItemStack stack, String customName, StringPalette palette) {
        if (customName == null
                && stack.get(DataComponents.ENCHANTMENTS) == null
                && stack.get(DataComponents.STORED_ENCHANTMENTS) == null
                && stack.get(DataComponents.POTION_CONTENTS) == null
                && stack.get(DataComponents.LORE) == null) {
            return List.of();
        }

        List<Integer> details = new ArrayList<>(4);

        addEnchantments(stack.get(DataComponents.ENCHANTMENTS), details, palette);
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
        if (!details.contains(id)) details.add(id);
    }

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

        ItemContainerContents nested = stack.get(DataComponents.CONTAINER);
        if (nested != null) {
            ItemContentsCompat.stacks(nested).forEach(inner -> appendStack(inner, out, depth + 1, palette));
        }

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            ItemContentsCompat.stacks(bundle).forEach(inner -> appendStack(inner, out, depth + 1, palette));
        }
    }
}
