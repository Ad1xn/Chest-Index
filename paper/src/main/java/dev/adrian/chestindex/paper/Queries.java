package dev.adrian.chestindex.paper;

import dev.adrian.chestindex.core.index.IndexQuery;
import dev.adrian.chestindex.core.index.SearchResult;
import dev.adrian.chestindex.core.index.WorldIndex;
import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackDetail;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.core.store.StringPalette;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.SearchQuery;
import dev.adrian.chestindex.core.util.SearchText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.Lootable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class Queries {
    private static final int MAX_ITEMS = 2000;
    private static final int MAX_CONTAINERS = 128;
    private static final double ENTITY_RADIUS = 256.0;
    private static final long ENTITY_CACHE_MS = 250;
    private static final int CACHE_DRIFT_BLOCKS = 16;

    private final Tracker tracker;
    private final ChestIndexPlugin plugin;

    private World entityCacheWorld;
    private long entityCacheCentre;
    private long entityCacheAt;
    private Entities entityCache;

    private record Entities(WorldIndex index, Map<ContainerRecord, Integer> entityIds) {}

    Queries(Tracker tracker, ChestIndexPlugin plugin) {
        this.tracker = tracker;
        this.plugin = plugin;
    }

    static boolean isOperator(Player player) {
        return player.isOp() || player.hasPermission("chestindex.admin");
    }

    static boolean mayQuery(Player player, Settings.Access access) {
        if (!player.hasPermission("chestindex.use")) return false;
        return access != Settings.Access.OP || isOperator(player);
    }

    private static UUID ownerLimit(Player player, Settings.Access access) {
        if (access != Settings.Access.OWNED) return null;
        return isOperator(player) ? null : player.getUniqueId();
    }

    private static long centreOf(Player player) {
        Location at = player.getLocation();
        return BlockKey.pack(at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    QueryDto.SummaryResponse summarise(Player player, QueryDto.SummaryRequest request) {
        Settings settings = plugin.settings();
        if (!mayQuery(player, settings.access())) return QueryDto.SummaryResponse.refused(request.requestId());

        if (QueryDto.ENDER_CHEST.equals(request.dimensionId())) {
            return enderChestSummary(player.getEnderChest(), request);
        }

        String here = ChestIndexPlugin.dimensionId(player.getWorld());
        String dimensionId = requestedDimension(here, request.dimensionId());
        long centre = centreOf(player);
        IndexQuery.Builder builder = IndexQuery.builder().center(centre).owner(ownerLimit(player, settings.access()));
        applyFilters(builder, request.filters(), settings);

        SearchQuery search = SearchQuery.parse(request.text());

        Set<Integer> itemIds = null;
        String needle = search.text() == null ? "" : search.text().trim().toLowerCase(Locale.ROOT);
        if (!needle.isEmpty()) itemIds = matchingIds(SearchText.tokens(needle));

        Set<Integer> byCategory = itemIdsForTerms(search);
        if (byCategory != null) itemIds = itemIds == null ? byCategory : narrow(itemIds, byCategory);
        if (itemIds != null) {
            if (itemIds.isEmpty()) return QueryDto.SummaryResponse.of(request.requestId(), List.of());
            builder.items(itemIds);
        }

        Set<Integer> onlyTypes = typeIdsForTerms(search);
        if (onlyTypes != null) {
            if (onlyTypes.isEmpty()) return QueryDto.SummaryResponse.of(request.requestId(), List.of());
            builder.types(onlyTypes).excludeTypes(Set.of());
        }

        Set<Integer> details = detailIdsForTerms(search);
        if (details != null) {
            if (details.isEmpty()) return QueryDto.SummaryResponse.of(request.requestId(), List.of());
            builder.details(details);
        }

        int limit = clamp(request.limit(), MAX_ITEMS);
        IndexQuery query = builder.build();
        List<WorldIndex.ItemSummary> summaries = tracker.index(dimensionId).summarise(query);

        Entities entities = entityIndex(player.getWorld(), here, dimensionId, request.filters(), centre, settings);
        if (entities != null) summaries = mergeSummaries(summaries, entities.index().summarise(query));

        List<QueryDto.ItemSummary> items = new ArrayList<>(Math.min(summaries.size(), limit));
        for (WorldIndex.ItemSummary summary : summaries) {
            if (items.size() >= limit) break;
            if (!search.allowsAmount(summary.totalCount())) continue;
            String itemId = tracker.palette().value(summary.itemId());
            if (itemId == null) continue;
            items.add(new QueryDto.ItemSummary(itemId, summary.totalCount(),
                    summary.containerCount(), summary.nestedCount(), summary.nearestDistSq()));
        }
        return QueryDto.SummaryResponse.of(request.requestId(), items);
    }

    QueryDto.ContainerResponse containers(Player player, QueryDto.ContainerRequest request) {
        Settings settings = plugin.settings();
        int id = request.requestId();
        if (!mayQuery(player, settings.access())) return QueryDto.ContainerResponse.refused(id);
        if (QueryDto.ENDER_CHEST.equals(request.dimensionId())) return QueryDto.ContainerResponse.of(id, List.of());

        Set<Integer> itemIds = new HashSet<>();
        for (String wanted : request.itemIds()) {
            int itemId = tracker.palette().lookup(wanted);
            if (itemId >= 0) itemIds.add(itemId);
        }
        if (itemIds.isEmpty()) return QueryDto.ContainerResponse.of(id, List.of());

        String here = ChestIndexPlugin.dimensionId(player.getWorld());
        String dimensionId = requestedDimension(here, request.dimensionId());
        long centre = centreOf(player);
        int limit = clamp(request.limit(), MAX_CONTAINERS);
        IndexQuery.Builder builder = IndexQuery.builder()
                .items(itemIds)
                .center(centre)
                .owner(ownerLimit(player, settings.access()))
                .limit(limit);
        applyFilters(builder, request.filters(), settings);

        SearchQuery search = SearchQuery.parse(request.text());
        Set<Integer> onlyTypes = typeIdsForTerms(search);
        if (onlyTypes != null) {
            if (onlyTypes.isEmpty()) return QueryDto.ContainerResponse.of(id, List.of());
            builder.types(onlyTypes).excludeTypes(Set.of());
        }
        Set<Integer> details = detailIdsForTerms(search);
        if (details != null) {
            if (details.isEmpty()) return QueryDto.ContainerResponse.of(id, List.of());
            builder.details(details);
        }

        IndexQuery query = builder.build();
        List<SearchResult> results = tracker.search(dimensionId, query);
        if (!results.isEmpty()) {
            List<Long> positions = new ArrayList<>(results.size());
            for (SearchResult result : results) positions.add(result.container().pos());
            if (plugin.refreshDirty(dimensionId, positions) > 0) results = tracker.search(dimensionId, query);
        }

        Entities entities = entityIndex(player.getWorld(), here, dimensionId, request.filters(), centre, settings);
        if (entities != null) {
            List<SearchResult> both = new ArrayList<>(results);
            both.addAll(entities.index().query(query));
            both.sort(Comparator.comparingDouble(SearchResult::distanceSq));
            results = both.size() > limit ? both.subList(0, limit) : both;
        }

        List<QueryDto.ContainerHit> hits = new ArrayList<>(results.size());
        for (SearchResult result : results) {
            ContainerRecord container = result.container();
            String typeId = tracker.palette().value(container.typeId());
            if (typeId == null) continue;
            if (!settings.indexUnlootedGeneratedChests() && container.origin() == Origin.NATURAL
                    && container.unlooted()) {
                continue;
            }
            hits.add(new QueryDto.ContainerHit(typeId, container.pos(), result.matchedCount(), result.distanceSq(),
                    hasNestedMatch(result), container.origin() == Origin.NATURAL, container.contentsKnown(),
                    entities == null ? QueryDto.ContainerHit.NOT_AN_ENTITY
                            : entities.entityIds().getOrDefault(container, QueryDto.ContainerHit.NOT_AN_ENTITY)));
        }
        return QueryDto.ContainerResponse.of(id, hits);
    }

    QueryDto.StatusResponse status(Player player, QueryDto.StatusRequest request) {
        if (!mayQuery(player, plugin.settings().access())) return QueryDto.StatusResponse.empty(request.requestId());

        List<QueryDto.DimensionSummary> dimensions = new ArrayList<>();
        for (String dimensionId : tracker.dimensions()) {
            if (QueryDto.ENDER_CHEST.equals(dimensionId)) continue;
            int containers = tracker.index(dimensionId).stats().containers();
            if (containers > 0) dimensions.add(new QueryDto.DimensionSummary(dimensionId, containers));
        }
        dimensions.sort(Comparator.comparing(QueryDto.DimensionSummary::dimensionId));
        if (hasContents(player.getEnderChest())) dimensions.add(new QueryDto.DimensionSummary(QueryDto.ENDER_CHEST, 1));

        RegionScan.Progress progress = plugin.regionScan().progress();
        return new QueryDto.StatusResponse(request.requestId(), progress.running(),
                progress.regionsRead(), progress.regionsTotal(), progress.chunksRead(), dimensions);
    }

    private Entities entityIndex(World world, String here, String dimensionId, QueryDto.Filters filters,
                                 long centre, Settings settings) {
        if (!settings.trackEntityContainers()) return null;
        if (filters != null && !filters.includeEntities()) return null;
        if (!dimensionId.equals(here)) return null;

        long now = System.currentTimeMillis();
        if (entityCacheWorld == world && now - entityCacheAt < ENTITY_CACHE_MS && withinDrift(entityCacheCentre, centre)) {
            return entityCache;
        }
        entityCacheWorld = world;
        entityCacheCentre = centre;
        entityCacheAt = now;
        entityCache = findEntities(world, dimensionId, centre);
        return entityCache;
    }

    private static boolean withinDrift(long a, long b) {
        return Math.abs(BlockKey.x(a) - BlockKey.x(b)) <= CACHE_DRIFT_BLOCKS
                && Math.abs(BlockKey.y(a) - BlockKey.y(b)) <= CACHE_DRIFT_BLOCKS
                && Math.abs(BlockKey.z(a) - BlockKey.z(b)) <= CACHE_DRIFT_BLOCKS;
    }

    private Entities findEntities(World world, String dimensionId, long centre) {
        Location at = new Location(world, BlockKey.x(centre), BlockKey.y(centre), BlockKey.z(centre));
        StringPalette palette = tracker.palette();
        int dimensionKey = palette.intern(dimensionId);
        long tick = world.getGameTime();

        WorldIndex index = null;
        Map<ContainerRecord, Integer> ids = new IdentityHashMap<>();
        Set<Long> taken = new HashSet<>();
        for (Entity entity : world.getNearbyEntities(at, ENTITY_RADIUS, ENTITY_RADIUS, ENTITY_RADIUS,
                Queries::isContainerEntity)) {
            Location where = entity.getLocation();
            int x = where.getBlockX();
            int y = where.getBlockY();
            int z = where.getBlockZ();
            if (!BlockKey.isRepresentable(x, y, z)) continue;
            long pos = BlockKey.pack(x, y, z);
            if (!taken.add(pos)) continue;

            boolean generated = entity instanceof Lootable lootable && lootable.getLootTable() != null;
            List<StackEntry> contents = ItemReader.read(
                    ((InventoryHolder) entity).getInventory().getContents(), palette);
            ContainerRecord record = new ContainerRecord(pos, dimensionKey,
                    palette.intern(entity.getType().getKey().toString()), ContainerRecord.decided(generated),
                    null, false, true, null, tick, contents, !generated);
            if (index == null) index = new WorldIndex(dimensionKey);
            index.put(record);
            ids.put(record, entity.getEntityId());
        }
        return index == null ? null : new Entities(index, ids);
    }

    private static boolean isContainerEntity(Entity entity) {
        return entity instanceof InventoryHolder && (entity instanceof Minecart || entity instanceof Boat);
    }

    private static boolean hasNestedMatch(SearchResult result) {
        for (StackEntry entry : result.matches()) {
            if (entry.isNested()) return true;
        }
        return false;
    }

    private static boolean hasContents(Inventory inventory) {
        for (ItemStack stack : inventory.getContents()) {
            if (!ItemReader.isEmpty(stack)) return true;
        }
        return false;
    }

    private static QueryDto.SummaryResponse enderChestSummary(Inventory ender, QueryDto.SummaryRequest request) {
        Map<String, int[]> totals = new LinkedHashMap<>();
        boolean nested = request.filters() == null || request.filters().includeNested();
        for (ItemStack stack : ender.getContents()) collectEnder(totals, stack, nested, false);

        SearchQuery search = SearchQuery.parse(request.text());
        String[] tokens = SearchText.tokens(search.text());
        List<QueryDto.ItemSummary> items = new ArrayList<>(totals.size());
        totals.forEach((itemId, counts) -> {
            if (!SearchText.matches(itemId, tokens)) return;
            if (!search.allowsAmount(counts[0])) return;
            items.add(new QueryDto.ItemSummary(itemId, counts[0], 1, counts[1], 0.0));
        });
        items.sort((a, b) -> Integer.compare(b.totalCount(), a.totalCount()));
        return QueryDto.SummaryResponse.of(request.requestId(), items);
    }

    private static void collectEnder(Map<String, int[]> totals, ItemStack stack, boolean includeNested,
                                     boolean isNested) {
        if (ItemReader.isEmpty(stack)) return;
        int[] counts = totals.computeIfAbsent(LoadedBlocks.idOf(stack.getType()), key -> new int[2]);
        counts[0] += stack.getAmount();
        if (isNested) counts[1] += stack.getAmount();
        if (!includeNested || !stack.hasItemMeta()) return;
        ItemMeta meta = stack.getItemMeta();
        ItemReader.forEachInside(meta, inner -> collectEnder(totals, inner, true, true));
    }

    private String requestedDimension(String here, String asked) {
        if (asked == null || asked.isBlank()) return here;
        return tracker.dimensions().contains(asked) ? asked : here;
    }

    private static List<WorldIndex.ItemSummary> mergeSummaries(List<WorldIndex.ItemSummary> stored,
                                                               List<WorldIndex.ItemSummary> live) {
        if (live.isEmpty()) return stored;
        Map<Integer, WorldIndex.ItemSummary> byItem = new LinkedHashMap<>();
        for (WorldIndex.ItemSummary summary : stored) byItem.put(summary.itemId(), summary);
        for (WorldIndex.ItemSummary summary : live) {
            byItem.merge(summary.itemId(), summary, (a, b) -> new WorldIndex.ItemSummary(a.itemId(),
                    a.totalCount() + b.totalCount(), a.containerCount() + b.containerCount(),
                    a.nestedCount() + b.nestedCount(), Math.min(a.nearestDistSq(), b.nearestDistSq())));
        }
        List<WorldIndex.ItemSummary> merged = new ArrayList<>(byItem.values());
        merged.sort((a, b) -> Integer.compare(b.totalCount(), a.totalCount()));
        return merged;
    }

    private static int clamp(int requested, int ceiling) {
        return requested <= 0 ? ceiling : Math.min(requested, ceiling);
    }

    private void applyFilters(IndexQuery.Builder builder, QueryDto.Filters filters, Settings settings) {
        QueryDto.Filters effective = filters == null ? QueryDto.Filters.defaults() : filters;
        builder.includeNested(effective.includeNested());
        if (!effective.origins().isEmpty()) builder.origins(effective.origins());

        Set<String> hidden = new HashSet<>();
        if (!effective.includeMachines()) {
            hidden.addAll(settings.machineTypes());
            builder.hideMachineParts(true);
        }
        if (!effective.includeUtility()) hidden.addAll(settings.utilityTypes());
        if (!hidden.isEmpty()) builder.excludeTypes(idsOf(hidden));
    }

    private Set<Integer> idsOf(Set<String> names) {
        Set<Integer> ids = new HashSet<>(names.size() * 2);
        for (String name : names) {
            int id = tracker.palette().lookup(name);
            if (id >= 0) ids.add(id);
        }
        return ids;
    }

    private Set<Integer> matchingIds(String[] tokens) {
        Set<Integer> matches = new HashSet<>();
        List<String> entries = tracker.palette().view();
        for (int id = 0; id < entries.size(); id++) {
            if (SearchText.matches(entries.get(id), tokens)) matches.add(id);
        }
        return matches;
    }

    private static Set<Integer> narrow(Set<Integer> soFar, Set<Integer> and) {
        Set<Integer> both = new HashSet<>(soFar);
        both.retainAll(and);
        return both;
    }

    private Set<Integer> itemIdsForTerms(SearchQuery search) {
        Set<Integer> allowed = null;
        List<String> mods = search.valuesOf(SearchQuery.Category.MOD);
        if (!mods.isEmpty()) {
            Set<Integer> ids = new HashSet<>();
            List<String> entries = tracker.palette().view();
            for (int id = 0; id < entries.size(); id++) {
                String entry = entries.get(id);
                if (entry == null) continue;
                int colon = entry.indexOf(':');
                if (colon <= 0) continue;
                String namespace = entry.substring(0, colon);
                for (String mod : mods) {
                    if (namespace.startsWith(mod)) {
                        ids.add(id);
                        break;
                    }
                }
            }
            allowed = ids;
        }

        List<String> tags = search.valuesOf(SearchQuery.Category.TAG);
        if (!tags.isEmpty()) {
            Set<Integer> ids = new HashSet<>();
            for (String tag : tags) {
                NamespacedKey key = NamespacedKey.fromString(tag.indexOf(':') < 0 ? "minecraft:" + tag : tag);
                if (key == null) continue;
                Tag<Material> items = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
                if (items == null) continue;
                for (Material material : items.getValues()) {
                    int paletteId = tracker.palette().lookup(LoadedBlocks.idOf(material));
                    if (paletteId >= 0) ids.add(paletteId);
                }
            }
            allowed = allowed == null ? ids : narrow(allowed, ids);
        }
        return allowed;
    }

    private Set<Integer> typeIdsForTerms(SearchQuery search) {
        List<String> wanted = search.valuesOf(SearchQuery.Category.TYPE);
        if (wanted.isEmpty()) return null;
        Set<Integer> ids = new HashSet<>();
        for (String type : wanted) ids.addAll(matchingIds(SearchText.tokens(type)));
        return ids;
    }

    private Set<Integer> detailIdsForTerms(SearchQuery search) {
        List<String> enchantments = search.valuesOf(SearchQuery.Category.ENCHANTMENT);
        List<String> potions = search.valuesOf(SearchQuery.Category.POTION);
        List<String> tooltip = search.valuesOf(SearchQuery.Category.TOOLTIP);
        if (enchantments.isEmpty() && potions.isEmpty() && tooltip.isEmpty()) return null;

        String[][] enchantmentTerms = tokenise(enchantments);
        String[][] potionTerms = tokenise(potions);
        String[][] tooltipTerms = tokenise(tooltip);

        Set<Integer> ids = new HashSet<>();
        List<String> entries = tracker.palette().view();
        for (int id = 0; id < entries.size(); id++) {
            String entry = entries.get(id);
            if (entry == null) continue;
            if (matchesDetail(entry, StackDetail.ENCHANTMENT, enchantmentTerms)
                    || matchesDetail(entry, StackDetail.POTION, potionTerms)
                    || matchesDetail(entry, null, tooltipTerms)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String[][] tokenise(List<String> terms) {
        String[][] tokens = new String[terms.size()][];
        for (int i = 0; i < terms.size(); i++) tokens[i] = SearchText.tokens(terms.get(i));
        return tokens;
    }

    private static boolean matchesDetail(String entry, String prefix, String[][] wanted) {
        if (wanted.length == 0) return false;
        if (prefix != null) {
            if (!StackDetail.is(entry, prefix)) return false;
        } else if (!StackDetail.is(entry, StackDetail.ENCHANTMENT) && !StackDetail.is(entry, StackDetail.POTION)
                && !StackDetail.is(entry, StackDetail.LORE) && !StackDetail.is(entry, StackDetail.NAME)) {
            return false;
        }
        String value = StackDetail.value(entry);
        for (String[] tokens : wanted) {
            if (SearchText.matches(value, tokens)) return true;
        }
        return false;
    }
}
