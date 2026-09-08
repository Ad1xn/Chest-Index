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

/**
 * What the search box can offer to complete, and the half of a search only the
 * client can answer.
 *
 * <p>The grammar itself is in {@link SearchQuery}, in {@code core}, and most of
 * it is resolved by whoever owns the index - a mod's items and an item tag are
 * questions about registries, which the server has too. Creative tabs are not:
 * they are assembled from the client's own registry and the client's enabled
 * feature flags, and a dedicated server has no reason to have built them. So
 * {@code tab:} is applied here, to the answers that come back, rather than
 * being sent along with the question.
 *
 * <p>Candidates are cached because the sources do not change while the game is
 * running and because this is asked on every keystroke, once per candidate.
 */
public final class SearchCategories {

    private SearchCategories() {}

    /**
     * The categories offered in the box.
     *
     * <p>All of them: the ones that ask about a particular stack rather than a
     * kind of item - what it is enchanted with, which potion it is, what its
     * tooltip says - are answered from what the index now stores per stack.
     * A world indexed before it did will answer them as the containers in it
     * are read again.
     */
    public static List<SearchQuery.Category> available() {
        return List.of(SearchQuery.Category.values());
    }

    /**
     * Completions for the word the cursor is in, written out in full.
     *
     * <p>Empty when the word is not a category at all - an ordinary name is
     * matched as it is typed and has nothing to complete to.
     */
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

    /**
     * Whether an item survives the terms only this side can resolve.
     *
     * <p>Everything else has already been applied by whoever answered the
     * query, so this is only asked about the items that came back.
     */
    public static boolean allows(SearchQuery query, String itemId) {
        List<String> tabs = query.valuesOf(SearchQuery.Category.TAB);
        if (tabs.isEmpty()) return true;

        Set<String> inTab = itemsInTabs(tabs);
        // No such tab, or a client that has not built its tabs: say nothing
        // rather than hide everything. An empty grid the player cannot explain
        // is the worst answer available.
        return inTab.isEmpty() || inTab.contains(itemId);
    }

    // --- candidates ---------------------------------------------------------

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
            // Free text: there is no list of the words somebody might have
            // written on an item.
            case TOOLTIP -> List.of();
        };
    }

    /** Every namespace that has an item in it, which is every mod worth naming. */
    private static List<String> mods() {
        Set<String> namespaces = new TreeSet<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) namespaces.add(id.getNamespace());
        return List.copyOf(namespaces);
    }

    /**
     * Item tags, with vanilla's namespace left off.
     *
     * <p>{@code #logs} rather than {@code #minecraft:logs}, because that is
     * what people type and the parser accepts the short form. A modded tag
     * keeps its namespace, since without it there would be nothing to tell two
     * mods' tags of the same name apart.
     */
    private static List<String> tags() {
        Set<String> names = new TreeSet<>();
        BuiltInRegistries.ITEM.getTags().forEach(tag -> {
            Identifier id = tag.key().location();
            names.add("minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString());
        });
        return List.copyOf(names);
    }

    /**
     * The kinds of container worth naming: everything the game knows as one,
     * plus whatever has been filed as a machine or a functional block.
     *
     * <p>Short names, for the same reason the tags are short.
     */
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

    /** The creative tabs, by the path of their id: {@code redstone}, {@code tools}. */
    private static List<String> tabs() {
        Set<String> names = new TreeSet<>();
        for (Identifier id : BuiltInRegistries.CREATIVE_MODE_TAB.keySet()) {
            names.add("minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString());
        }
        return List.copyOf(names);
    }

    /**
     * The enchantments this world has.
     *
     * <p>From the world's own registry rather than a built-in list, because
     * enchantments are datapack content since 1.21 - a pack can add one and a
     * pack can take one away, and the only correct list is the one the server
     * sent this client on joining.
     */
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

    /** Vanilla's namespace left off, as everywhere else in the box. */
    private static String shortName(Identifier id) {
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    // --- creative tab contents ---------------------------------------------

    private static final Map<String, Set<String>> TAB_CONTENTS = new HashMap<>();

    /**
     * Every item id the named tabs display.
     *
     * <p>Read from the built tabs rather than from any list of our own: what is
     * in a tab is decided by the game and by every mod that adds to one, and
     * the only place that is answered is the tab itself. Cached per tab, since
     * building the set walks a few thousand stacks.
     */
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
            // Matched the way the item search matches, so a half-typed or
            // half-remembered tab name still finds it.
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

    /**
     * A tab's contents, or nothing if they have not been built.
     *
     * <p>They are built when the player first opens the creative menu or a
     * search reaches them, and asking too early throws rather than returning
     * empty. This is a search box, not a reason to take the game down.
     */
    private static Collection<ItemStack> displayItems(CreativeModeTab tab) {
        try {
            return tab.getDisplayItems();
        } catch (RuntimeException notBuiltYet) {
            return List.of();
        }
    }

    /**
     * Drops every cache.
     *
     * <p>Called when the world changes: the enchantment list is that world's
     * datapack content, and the creative tabs are rebuilt on a resource reload.
     */
    public static void forget() {
        CANDIDATES.clear();
        TAB_CONTENTS.clear();
    }
}
