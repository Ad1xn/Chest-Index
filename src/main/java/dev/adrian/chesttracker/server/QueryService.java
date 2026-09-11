package dev.adrian.chesttracker.server;

import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.index.IndexQuery;
import dev.adrian.chesttracker.core.index.SearchResult;
import dev.adrian.chesttracker.core.index.WorldIndex;
import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackEntry;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.core.store.StringPalette;
import dev.adrian.chesttracker.core.util.BlockKey;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import dev.adrian.chesttracker.platform.ItemContentsCompat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class QueryService {

    private static final int MAX_ITEMS = 2000;
    private static final int MAX_CONTAINERS = 128;

    private static Entities entityIndex(Level level, String hereDimension, String dimensionId,
                                        TrackerService tracker, QueryDto.Filters filters,
                                        long centre) {
        if (level == null || !ChestTrackerConfig.get().trackEntityContainers) return null;
        if (filters != null && !filters.includeEntities()) return null;
        if (!dimensionId.equals(hereDimension)) return null;

        long now = System.currentTimeMillis();
        if (entityCacheLevel.get() == level
                && entityCacheTracker.get() == tracker
                && dimensionId.equals(entityCacheDimension)
                && now - entityCacheAt < ENTITY_CACHE_MS
                && withinDrift(entityCacheCentre, centre)) {
            return entityCache;
        }

        List<dev.adrian.chesttracker.platform.EntityContainers.Found> found =
                dev.adrian.chesttracker.platform.EntityContainers
                        .near(level, dimensionId, tracker.palette(), centre);

        entityCacheLevel = new java.lang.ref.WeakReference<>(level);
        entityCacheTracker = new java.lang.ref.WeakReference<>(tracker);
        entityCacheDimension = dimensionId;
        entityCacheCentre = centre;
        entityCacheAt = now;

        if (found.isEmpty()) {
            entityCache = null;
            return null;
        }

        WorldIndex index = new WorldIndex(tracker.palette().intern(dimensionId));
        java.util.Map<ContainerRecord, Integer> ids = new java.util.IdentityHashMap<>();
        for (var one : found) {
            index.put(one.record());
            ids.put(one.record(), one.entityId());
        }
        entityCache = new Entities(index, ids);
        return entityCache;
    }

    private static final long ENTITY_CACHE_MS = 250;

    private static final int CACHE_DRIFT_BLOCKS = 16;

    private static java.lang.ref.WeakReference<Level> entityCacheLevel =
            new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<TrackerService> entityCacheTracker =
            new java.lang.ref.WeakReference<>(null);
    private static String entityCacheDimension;
    private static long entityCacheCentre;
    private static long entityCacheAt;
    private static Entities entityCache;

    private static boolean withinDrift(long a, long b) {
        return Math.abs(BlockKey.x(a) - BlockKey.x(b)) <= CACHE_DRIFT_BLOCKS
                && Math.abs(BlockKey.y(a) - BlockKey.y(b)) <= CACHE_DRIFT_BLOCKS
                && Math.abs(BlockKey.z(a) - BlockKey.z(b)) <= CACHE_DRIFT_BLOCKS;
    }

    private record Entities(WorldIndex index, java.util.Map<ContainerRecord, Integer> entityIds) {}

    private QueryService() {}

    public static boolean mayQuery(ServerPlayer player, ChestTrackerConfig.Access access) {
        return access != ChestTrackerConfig.Access.OP || isOperator(player);
    }

    private static boolean isOperator(ServerPlayer player) {
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    private static java.util.UUID ownerLimit(ServerPlayer player, ChestTrackerConfig.Access access) {
        if (access != ChestTrackerConfig.Access.OWNED) return null;
        return isOperator(player) ? null : player.getUUID();
    }

    @FunctionalInterface
    public interface Refresher {
        int refresh(String dimensionId, List<Long> positions);

        Refresher NONE = (dimensionId, positions) -> 0;
    }

    public static QueryDto.SummaryResponse summarise(
            TrackerService tracker, ServerPlayer player,
            QueryDto.SummaryRequest request, ChestTrackerConfig.Access access) {

        if (!mayQuery(player, access)) return QueryDto.SummaryResponse.refused(request.requestId());

        return summarise(tracker, request, centreOf(player),
                Trackers.dimensionId(player.level()), ownerLimit(player, access),
                player.getEnderChestInventory(), player.level());
    }

    public static QueryDto.SummaryResponse summarise(
            TrackerService tracker, QueryDto.SummaryRequest request,
            long centre, String hereDimension, java.util.UUID ownerLimit, Container enderChest) {
        return summarise(tracker, request, centre, hereDimension, ownerLimit, enderChest, null);
    }

    public static QueryDto.SummaryResponse summarise(
            TrackerService tracker, QueryDto.SummaryRequest request,
            long centre, String hereDimension, java.util.UUID ownerLimit, Container enderChest,
            Level entitySource) {

        if (QueryDto.ENDER_CHEST.equals(request.dimensionId()) && enderChest != null) {
            return enderChestSummary(enderChest, request);
        }

        String dimensionId = requestedDimension(tracker, hereDimension, request.dimensionId());
        IndexQuery.Builder builder = IndexQuery.builder()
                .center(centre)
                .owner(ownerLimit);
        applyFilters(builder, request.filters(), tracker);

        dev.adrian.chesttracker.core.util.SearchQuery search =
                dev.adrian.chesttracker.core.util.SearchQuery.parse(request.text());

        Set<Integer> itemIds = null;
        String needle = normalise(search.text());
        if (!needle.isEmpty()) itemIds = matchingItemIds(tracker, needle);

        Set<Integer> byCategory = itemIdsForTerms(tracker, search);
        if (byCategory != null) {
            if (itemIds == null) {
                itemIds = byCategory;
            } else {
                itemIds = new HashSet<>(itemIds);
                itemIds.retainAll(byCategory);
            }
        }
        if (itemIds != null) {
            if (itemIds.isEmpty()) return QueryDto.SummaryResponse.of(request.requestId(), List.of());
            builder.items(itemIds);
        }

        Set<Integer> onlyTypes = typeIdsForTerms(tracker, search);
        if (onlyTypes != null) {
            if (onlyTypes.isEmpty()) return QueryDto.SummaryResponse.of(request.requestId(), List.of());
            builder.types(onlyTypes);
            builder.excludeTypes(Set.of());
        }

        Set<Integer> details = detailIdsForTerms(tracker, search);
        if (details != null) {
            if (details.isEmpty()) return QueryDto.SummaryResponse.of(request.requestId(), List.of());
            builder.details(details);
        }

        int limit = clamp(request.limit(), MAX_ITEMS);
        IndexQuery query = builder.build();
        List<WorldIndex.ItemSummary> summaries = tracker.index(dimensionId).summarise(query);

        Entities entities = entityIndex(entitySource, hereDimension, dimensionId,
                tracker, request.filters(), centre);
        if (entities != null) summaries = mergeSummaries(summaries, entities.index().summarise(query));

        List<QueryDto.ItemSummary> items = new java.util.ArrayList<>(Math.min(summaries.size(), limit));
        for (WorldIndex.ItemSummary summary : summaries) {
            if (items.size() >= limit) break;
            String itemId = tracker.palette().value(summary.itemId());
            if (itemId == null) continue;
            items.add(new QueryDto.ItemSummary(itemId, summary.totalCount(),
                    summary.containerCount(), summary.nestedCount(), summary.nearestDistSq()));
        }
        return QueryDto.SummaryResponse.of(request.requestId(), items);
    }

    public static QueryDto.ContainerResponse containers(
            TrackerService tracker, ServerPlayer player,
            QueryDto.ContainerRequest request, ChestTrackerConfig.Access access) {

        if (!mayQuery(player, access)) return QueryDto.ContainerResponse.refused(request.requestId());

        ServerLevel level = player.level();
        return containers(tracker, request, centreOf(player),
                Trackers.dimensionId(level), ownerLimit(player, access),
                (dimensionId, positions) ->
                        Trackers.refreshDirty(Trackers.levelFor(dimensionId), dimensionId, positions),
                level);
    }

    public static QueryDto.ContainerResponse containers(
            TrackerService tracker, QueryDto.ContainerRequest request,
            long centre, String hereDimension, java.util.UUID ownerLimit, Refresher refresher) {
        return containers(tracker, request, centre, hereDimension, ownerLimit, refresher, null);
    }

    public static QueryDto.ContainerResponse containers(
            TrackerService tracker, QueryDto.ContainerRequest request,
            long centre, String hereDimension, java.util.UUID ownerLimit, Refresher refresher,
            Level entitySource) {

        int id = request.requestId();

        if (QueryDto.ENDER_CHEST.equals(request.dimensionId())) {
            return QueryDto.ContainerResponse.of(id, List.of());
        }

        Set<Integer> itemIds = new HashSet<>();
        for (String wanted : request.itemIds()) {
            int itemId = tracker.palette().lookup(wanted);
            if (itemId >= 0) itemIds.add(itemId);
        }
        if (itemIds.isEmpty()) return QueryDto.ContainerResponse.of(id, List.of());

        String dimensionId = requestedDimension(tracker, hereDimension, request.dimensionId());
        IndexQuery.Builder builder = IndexQuery.builder()
                .items(itemIds)
                .center(centre)
                .owner(ownerLimit)
                .limit(clamp(request.limit(), MAX_CONTAINERS));
        applyFilters(builder, request.filters(), tracker);

        dev.adrian.chesttracker.core.util.SearchQuery search =
                dev.adrian.chesttracker.core.util.SearchQuery.parse(request.text());

        Set<Integer> onlyTypes = typeIdsForTerms(tracker, search);
        if (onlyTypes != null) {
            if (onlyTypes.isEmpty()) return QueryDto.ContainerResponse.of(id, List.of());
            builder.types(onlyTypes);
            builder.excludeTypes(Set.of());
        }

        Set<Integer> details = detailIdsForTerms(tracker, search);
        if (details != null) {
            if (details.isEmpty()) return QueryDto.ContainerResponse.of(id, List.of());
            builder.details(details);
        }

        IndexQuery query = builder.build();

        List<SearchResult> results = tracker.search(dimensionId, query);
        if (refreshStale(refresher, dimensionId, results) > 0) {
            results = tracker.search(dimensionId, query);
        }

        Entities entities = entityIndex(entitySource, hereDimension, dimensionId,
                tracker, request.filters(), centre);
        if (entities != null) {
            List<SearchResult> both = new java.util.ArrayList<>(results);
            both.addAll(entities.index().query(query));
            both.sort(java.util.Comparator.comparingDouble(SearchResult::distanceSq));
            int limit = clamp(request.limit(), MAX_CONTAINERS);
            results = both.size() > limit ? both.subList(0, limit) : both;
        }
        List<QueryDto.ContainerHit> hits = new java.util.ArrayList<>(results.size());
        for (SearchResult result : results) {
            String typeId = tracker.palette().value(result.container().typeId());
            if (typeId == null) continue;
            hits.add(new QueryDto.ContainerHit(
                    typeId,
                    result.container().pos(),
                    result.matchedCount(),
                    result.distanceSq(),
                    hasNestedMatch(result),
                    result.container().origin() == Origin.NATURAL,
                    result.container().contentsKnown(),
                    entities == null ? QueryDto.ContainerHit.NOT_AN_ENTITY
                            : entities.entityIds().getOrDefault(result.container(),
                                    QueryDto.ContainerHit.NOT_AN_ENTITY)));
        }
        return QueryDto.ContainerResponse.of(id, hits);
    }

    private static int refreshStale(Refresher refresher, String dimensionId, List<SearchResult> results) {
        if (results.isEmpty() || refresher == Refresher.NONE) return 0;
        List<Long> positions = new java.util.ArrayList<>(results.size());
        for (SearchResult result : results) positions.add(result.container().pos());
        return refresher.refresh(dimensionId, positions);
    }

    private static boolean hasNestedMatch(SearchResult result) {
        for (StackEntry entry : result.matches()) {
            if (entry.isNested()) return true;
        }
        return false;
    }

    private static long centreOf(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return BlockKey.pack(pos.getX(), pos.getY(), pos.getZ());
    }

    private static QueryDto.SummaryResponse enderChestSummary(
            Container ender, QueryDto.SummaryRequest request) {

        Map<String, int[]> totals = new java.util.LinkedHashMap<>();
        boolean nested = request.filters().includeNested();
        for (int slot = 0; slot < ender.getContainerSize(); slot++) {
            collectEnderStack(totals, ender.getItem(slot), nested, false);
        }

        String[] tokens = dev.adrian.chesttracker.core.util.SearchText.tokens(request.text());
        List<QueryDto.ItemSummary> items = new java.util.ArrayList<>(totals.size());
        totals.forEach((itemId, counts) -> {
            if (!dev.adrian.chesttracker.core.util.SearchText.matches(itemId, tokens)) return;
            items.add(new QueryDto.ItemSummary(itemId, counts[0], 1, counts[1], 0.0));
        });
        items.sort((a, b) -> Integer.compare(b.totalCount(), a.totalCount()));
        return QueryDto.SummaryResponse.of(request.requestId(), items);
    }

    private static void collectEnderStack(Map<String, int[]> totals, ItemStack stack,
                                          boolean includeNested, boolean isNested) {
        if (stack.isEmpty()) return;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            int[] counts = totals.computeIfAbsent(id.toString(), key -> new int[2]);
            counts[0] += stack.getCount();
            if (isNested) counts[1] += stack.getCount();
        }
        if (!includeNested) return;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            ItemContentsCompat.stacks(contents)
                    .forEach(inner -> collectEnderStack(totals, inner, true, true));
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            bundle.itemCopyStream().forEach(inner -> collectEnderStack(totals, inner, true, true));
        }
    }

    private static boolean hasEnderChestContents(Container ender) {
        if (ender == null) return false;
        for (int slot = 0; slot < ender.getContainerSize(); slot++) {
            if (!ender.getItem(slot).isEmpty()) return true;
        }
        return false;
    }

    private static String requestedDimension(TrackerService tracker, String here, String asked) {
        if (asked == null || asked.isBlank()) return here;
        return tracker.dimensions().contains(asked) ? asked : here;
    }

    public static QueryDto.StatusResponse status(
            TrackerService tracker, ServerPlayer player,
            QueryDto.StatusRequest request, ChestTrackerConfig.Access access) {

        if (!mayQuery(player, access)) return QueryDto.StatusResponse.empty(request.requestId());
        return status(tracker, request, hasEnderChestContents(player.getEnderChestInventory()));
    }

    public static QueryDto.StatusResponse status(
            TrackerService tracker, QueryDto.StatusRequest request, boolean offerEnderChest) {

        List<QueryDto.DimensionSummary> dimensions = new java.util.ArrayList<>();
        for (String dimensionId : tracker.dimensions()) {
            if (QueryDto.ENDER_CHEST.equals(dimensionId)) continue;
            int containers = tracker.index(dimensionId).stats().containers();
            if (containers > 0) dimensions.add(new QueryDto.DimensionSummary(dimensionId, containers));
        }
        dimensions.sort(java.util.Comparator.comparing(QueryDto.DimensionSummary::dimensionId));

        if (offerEnderChest) {
            dimensions.add(new QueryDto.DimensionSummary(QueryDto.ENDER_CHEST, 1));
        }

        dev.adrian.chesttracker.server.scan.RegionScanner scanner = Trackers.regionScanner();
        dev.adrian.chesttracker.server.scan.RegionScanner.Progress progress =
                scanner == null ? null : scanner.progress();
        boolean scanning = progress != null && progress.running();

        return new QueryDto.StatusResponse(request.requestId(), scanning,
                progress == null ? 0 : progress.regionsRead(),
                progress == null ? 0 : progress.regionsTotal(),
                progress == null ? 0 : progress.chunksRead(),
                dimensions);
    }

    private static List<WorldIndex.ItemSummary> mergeSummaries(
            List<WorldIndex.ItemSummary> stored, List<WorldIndex.ItemSummary> live) {

        if (live.isEmpty()) return stored;

        java.util.Map<Integer, WorldIndex.ItemSummary> byItem = new java.util.LinkedHashMap<>();
        for (WorldIndex.ItemSummary summary : stored) byItem.put(summary.itemId(), summary);
        for (WorldIndex.ItemSummary summary : live) {
            byItem.merge(summary.itemId(), summary, (a, b) -> new WorldIndex.ItemSummary(
                    a.itemId(),
                    a.totalCount() + b.totalCount(),
                    a.containerCount() + b.containerCount(),
                    a.nestedCount() + b.nestedCount(),
                    Math.min(a.nearestDistSq(), b.nearestDistSq())));
        }

        List<WorldIndex.ItemSummary> merged = new java.util.ArrayList<>(byItem.values());
        merged.sort((a, b) -> Integer.compare(b.totalCount(), a.totalCount()));
        return merged;
    }

    private static String normalise(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static int clamp(int requested, int ceiling) {
        return requested <= 0 ? ceiling : Math.min(requested, ceiling);
    }

    private static void applyFilters(IndexQuery.Builder builder, QueryDto.Filters filters,
                                     TrackerService tracker) {
        QueryDto.Filters effective = filters == null ? QueryDto.Filters.defaults() : filters;
        builder.includeNested(effective.includeNested());
        if (!effective.origins().isEmpty()) builder.origins(effective.origins());

        ChestTrackerConfig config = ChestTrackerConfig.get();
        Set<String> hidden = new HashSet<>();
        if (!effective.includeMachines()) hidden.addAll(config.machineTypes);
        if (!effective.includeUtility()) hidden.addAll(config.utilityTypes);
        if (!hidden.isEmpty()) builder.excludeTypes(typeIdsOf(tracker, hidden));
    }

    private static Set<Integer> matchingItemIds(TrackerService tracker, String needle) {
        String[] tokens = dev.adrian.chesttracker.core.util.SearchText.tokens(needle);
        Set<Integer> matches = new HashSet<>();
        List<String> entries = tracker.palette().view();
        for (int id = 0; id < entries.size(); id++) {
            if (dev.adrian.chesttracker.core.util.SearchText.matches(entries.get(id), tokens)) {
                matches.add(id);
            }
        }
        return matches;
    }

    private static Set<Integer> itemIdsForTerms(
            TrackerService tracker, dev.adrian.chesttracker.core.util.SearchQuery search) {

        Set<Integer> allowed = null;

        List<String> mods = search.valuesOf(
                dev.adrian.chesttracker.core.util.SearchQuery.Category.MOD);
        if (!mods.isEmpty()) allowed = narrow(allowed, itemIdsFromMods(tracker, mods));

        List<String> tags = search.valuesOf(
                dev.adrian.chesttracker.core.util.SearchQuery.Category.TAG);
        if (!tags.isEmpty()) allowed = narrow(allowed, itemIdsFromTags(tracker, tags));

        return allowed;
    }

    private static Set<Integer> narrow(Set<Integer> soFar, Set<Integer> and) {
        if (soFar == null) return and;
        Set<Integer> both = new HashSet<>(soFar);
        both.retainAll(and);
        return both;
    }

    private static Set<Integer> itemIdsFromMods(TrackerService tracker, List<String> mods) {
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
        return ids;
    }

    private static Set<Integer> itemIdsFromTags(TrackerService tracker, List<String> tags) {
        Set<Integer> ids = new HashSet<>();
        for (String tag : tags) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(
                    tag.indexOf(':') < 0 ? "minecraft:" + tag : tag);
            if (id == null) continue;

            var key = net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, id);
            for (var holder : net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
                net.minecraft.resources.Identifier itemId =
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(holder.value());
                if (itemId == null) continue;
                int paletteId = tracker.palette().lookup(itemId.toString());
                if (paletteId >= 0) ids.add(paletteId);
            }
        }
        return ids;
    }

    private static Set<Integer> typeIdsForTerms(
            TrackerService tracker, dev.adrian.chesttracker.core.util.SearchQuery search) {

        List<String> wanted = search.valuesOf(
                dev.adrian.chesttracker.core.util.SearchQuery.Category.TYPE);
        if (wanted.isEmpty()) return null;

        Set<Integer> ids = new HashSet<>();
        List<String> entries = tracker.palette().view();
        for (String type : wanted) {
            String[] tokens = dev.adrian.chesttracker.core.util.SearchText.tokens(type);
            for (int id = 0; id < entries.size(); id++) {
                if (dev.adrian.chesttracker.core.util.SearchText.matches(entries.get(id), tokens)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static Set<Integer> detailIdsForTerms(
            TrackerService tracker, dev.adrian.chesttracker.core.util.SearchQuery search) {

        List<String> enchantments = search.valuesOf(
                dev.adrian.chesttracker.core.util.SearchQuery.Category.ENCHANTMENT);
        List<String> potions = search.valuesOf(
                dev.adrian.chesttracker.core.util.SearchQuery.Category.POTION);
        List<String> tooltip = search.valuesOf(
                dev.adrian.chesttracker.core.util.SearchQuery.Category.TOOLTIP);

        if (enchantments.isEmpty() && potions.isEmpty() && tooltip.isEmpty()) return null;

        String[][] enchantmentTerms = tokenise(enchantments);
        String[][] potionTerms = tokenise(potions);
        String[][] tooltipTerms = tokenise(tooltip);

        Set<Integer> ids = new HashSet<>();
        List<String> entries = tracker.palette().view();
        for (int id = 0; id < entries.size(); id++) {
            String entry = entries.get(id);
            if (entry == null) continue;

            if (matchesDetail(entry, dev.adrian.chesttracker.core.model.StackDetail.ENCHANTMENT, enchantmentTerms)
                    || matchesDetail(entry, dev.adrian.chesttracker.core.model.StackDetail.POTION, potionTerms)
                    || matchesDetail(entry, null, tooltipTerms)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String[][] tokenise(List<String> terms) {
        String[][] tokens = new String[terms.size()][];
        for (int i = 0; i < terms.size(); i++) {
            tokens[i] = dev.adrian.chesttracker.core.util.SearchText.tokens(terms.get(i));
        }
        return tokens;
    }

    private static boolean matchesDetail(String entry, String prefix, String[][] wanted) {
        if (wanted.length == 0) return false;

        if (prefix != null) {
            if (!dev.adrian.chesttracker.core.model.StackDetail.is(entry, prefix)) return false;
        } else if (!isAnyDetail(entry)) {
            return false;
        }

        String value = dev.adrian.chesttracker.core.model.StackDetail.value(entry);
        for (String[] tokens : wanted) {
            if (dev.adrian.chesttracker.core.util.SearchText.matches(value, tokens)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnyDetail(String entry) {
        return dev.adrian.chesttracker.core.model.StackDetail.is(
                        entry, dev.adrian.chesttracker.core.model.StackDetail.ENCHANTMENT)
                || dev.adrian.chesttracker.core.model.StackDetail.is(
                        entry, dev.adrian.chesttracker.core.model.StackDetail.POTION)
                || dev.adrian.chesttracker.core.model.StackDetail.is(
                        entry, dev.adrian.chesttracker.core.model.StackDetail.LORE)
                || dev.adrian.chesttracker.core.model.StackDetail.is(
                        entry, dev.adrian.chesttracker.core.model.StackDetail.NAME);
    }

    private static Set<Integer> typeIdsOf(TrackerService tracker, Set<String> typeNames) {
        StringPalette palette = tracker.palette();
        Set<Integer> ids = new HashSet<>(typeNames.size() * 2);
        for (String name : typeNames) {
            int id = palette.lookup(name);
            if (id >= 0) ids.add(id);
        }
        return ids;
    }
}
