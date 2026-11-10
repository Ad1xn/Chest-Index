package dev.adrian.chestindex.paper;

import dev.adrian.chestindex.core.model.StackDetail;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.store.StringPalette;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

final class ItemReader {
    private static final int MAX_NESTING = 8;

    private ItemReader() {}

    static List<StackEntry> read(ItemStack[] stacks, StringPalette palette) {
        List<StackEntry> entries = new ArrayList<>();
        for (ItemStack stack : stacks) append(stack, entries, 0, palette);
        return entries;
    }

    static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static void append(ItemStack stack, List<StackEntry> out, int depth, StringPalette palette) {
        if (depth > MAX_NESTING || isEmpty(stack)) return;

        String itemId = LoadedBlocks.idOf(stack.getType());
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        String name = meta != null && meta.hasCustomName() ? plain(meta.customName()) : null;

        out.add(new StackEntry(palette.intern(itemId), stack.getAmount(), depth, name,
                meta == null ? List.of() : detailsOf(meta, name, palette)));

        if (meta == null) return;
        forEachInside(meta, inner -> append(inner, out, depth + 1, palette));
    }

    static void forEachInside(ItemMeta meta, java.util.function.Consumer<ItemStack> visit) {
        if (meta instanceof BlockStateMeta blockMeta && blockMeta.hasBlockState()
                && blockMeta.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inner : box.getInventory().getContents()) visit.accept(inner);
        }
        if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            for (ItemStack inner : bundle.getItems()) visit.accept(inner);
        }
    }

    private static List<Integer> detailsOf(ItemMeta meta, String customName, StringPalette palette) {
        List<Integer> details = new ArrayList<>(4);
        BiConsumer<Enchantment, Integer> enchantment = (which, level) ->
                add(details, palette, StackDetail.enchantment(which.getKey().toString()));

        if (meta.hasEnchants()) meta.getEnchants().forEach(enchantment);
        if (meta instanceof EnchantmentStorageMeta stored && stored.hasStoredEnchants()) {
            for (Map.Entry<Enchantment, Integer> entry : stored.getStoredEnchants().entrySet()) {
                enchantment.accept(entry.getKey(), entry.getValue());
            }
        }

        if (meta instanceof PotionMeta potion) {
            PotionType type = potion.getBasePotionType();
            if (type != null) add(details, palette, StackDetail.potion(type.getKey().toString()));
        }

        if (meta.hasLore()) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                int lines = 0;
                for (Component line : lore) {
                    if (lines++ >= StackDetail.MAX_LORE_LINES) break;
                    add(details, palette, StackDetail.lore(plain(line)));
                }
            }
        }

        add(details, palette, StackDetail.name(customName));
        return details.isEmpty() ? List.of() : details;
    }

    private static void add(List<Integer> details, StringPalette palette, String detail) {
        if (detail == null) return;
        int id = palette.intern(detail);
        if (!details.contains(id)) details.add(id);
    }
}
