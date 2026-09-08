package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.util.SearchQuery;
import dev.adrian.chesttracker.core.util.SearchText;
import dev.adrian.chesttracker.platform.ContainerTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class SearchCategories {

    private SearchCategories() {}

    public static List<SearchQuery.Category> available() {
        return List.of(SearchQuery.Category.values());
    }

    public static List<String> completionsFor(String word) {
        SearchQuery.Category category = SearchQuery.categoryOf(word);
        if (category == null || !available().contains(category)) return List.of();

        String typed = word.substring(category.prefix().length()).toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates(category)) {
            if (candidate.startsWith(typed)) matches.add(category.prefix() + candidate);
        }
        return matches;
    }

    public static boolean allows(SearchQuery query, String itemId) {
        List<String> tabs = query.valuesOf(SearchQuery.Category.TAB);
        if (tabs.isEmpty()) return true;

        Set<String> inTab = itemsInTabs(tabs);
        return inTab.isEmpty() || inTab.contains(itemId);
    }

    private static final Map<SearchQuery.Category, List<String>> CANDIDATES = new HashMap<>();

    private static List<String> candidates(SearchQuery.Category category) {
        return CANDIDATES.computeIfAbsent(category, SearchCategories::buildCandidates);
    }

    private static List<String> buildCandidates(SearchQuery.Category category) {
        return switch (category) {
            case MOD -> mods();
            case TAG -> tags();
            case TYPE -> containerTypes();
            case TAB -> tabs();
            case ENCHANTMENT -> enchantments();
            case POTION -> potions();
            case TOOLTIP -> List.of();
        };
    }

    private static List<String> mods() {
        Set<String> namespaces = new TreeSet<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) namespaces.add(id.getNamespace());
        return List.copyOf(namespaces);
    }

    private static List<String> tags() {
        Set<String> names = new TreeSet<>();
        BuiltInRegistries.ITEM.getTags().forEach(tag -> {
            Identifier id = tag.key().location();
            names.add("minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString());
        });
        return List.copyOf(names);
    }

    private static List<String> containerTypes() {
        Set<String> names = new TreeSet<>();
        ChestTrackerConfig config = ChestTrackerConfig.get();

        List<String> known = new ArrayList<>(ContainerTypes.vanilla());
        known.addAll(config.machineTypes);
        known.addAll(config.utilityTypes);

        for (String id : known) {
            int colon = id.indexOf(':');
            names.add(colon < 0 || id.startsWith("minecraft:") ? id.substring(colon + 1) : id);
        }
        return List.copyOf(names);
    }

    private static List<String> tabs() {
        Set<String> names = new TreeSet<>();
        for (Identifier id : BuiltInRegistries.CREATIVE_MODE_TAB.keySet()) {
            names.add("minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString());
        }
        return List.copyOf(names);
    }

    private static List<String> enchantments() {
        net.minecraft.client.multiplayer.ClientLevel level =
                net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return List.of();

        Set<String> names = new TreeSet<>();
        level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .listElementIds()
                .forEach(key -> names.add(shortName(key.identifier())));
        return List.copyOf(names);
    }

    private static List<String> potions() {
        Set<String> names = new TreeSet<>();
        for (Identifier id : BuiltInRegistries.POTION.keySet()) names.add(shortName(id));
        return List.copyOf(names);
    }

    private static String shortName(Identifier id) {
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private static final Map<String, Set<String>> TAB_CONTENTS = new HashMap<>();

    private static Set<String> itemsInTabs(List<String> wanted) {
        Set<String> items = new LinkedHashSet<>();
        for (String name : wanted) {
            items.addAll(TAB_CONTENTS.computeIfAbsent(name, SearchCategories::itemsInTab));
        }
        return items;
    }

    private static Set<String> itemsInTab(String name) {
        String[] tokens = SearchText.tokens(name);
        Set<String> items = new LinkedHashSet<>();

        for (Map.Entry<net.minecraft.resources.ResourceKey<CreativeModeTab>, CreativeModeTab> entry
                : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {

            Identifier id = entry.getKey().identifier();
            if (!SearchText.matches(id.getPath(), tokens) && !SearchText.matches(id.toString(), tokens)) {
                continue;
            }
            for (ItemStack stack : displayItems(entry.getValue())) {
                Item item = stack.getItem();
                Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                if (itemId != null) items.add(itemId.toString());
            }
        }
        return items;
    }

    private static Collection<ItemStack> displayItems(CreativeModeTab tab) {
        try {
            return tab.getDisplayItems();
        } catch (RuntimeException notBuiltYet) {
            return List.of();
        }
    }

    public static void forget() {
        CANDIDATES.clear();
        TAB_CONTENTS.clear();
    }
}
