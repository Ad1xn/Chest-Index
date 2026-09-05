package dev.adrian.chesttracker.server;

import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.index.IndexQuery;
import dev.adrian.chesttracker.core.index.SearchResult;
import dev.adrian.chesttracker.core.index.WorldIndex;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackEntry;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.core.util.BlockKey;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class QueryService {

    private static final int MAX_ITEMS = 2000;
    private static final int MAX_CONTAINERS = 128;

    private static final Set<String> MACHINES = Set.of(
            "minecraft:hopper", "minecraft:dropper", "minecraft:dispenser",
            "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
            "minecraft:brewing_stand", "minecraft:crafter", "minecraft:campfire",
            "minecraft:jukebox", "minecraft:lectern", "minecraft:decorated_pot",
            "minecraft:chiseled_bookshelf");

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

    public static QueryDto.SummaryResponse summarise(
            TrackerService tracker, ServerPlayer player,
            QueryDto.SummaryRequest request, ChestTrackerConfig.Access access) {

        if (!mayQuery(player, access)) return QueryDto.SummaryResponse.refused(request.requestId());

        if (QueryDto.ENDER_CHEST.equals(request.dimensionId())) {
            return enderChestSummary(player, request);
        }

        String dimensionId = requestedDimension(tracker, player, request.dimensionId());
        IndexQuery.Builder builder = IndexQuery.builder()
                .center(centreOf(player))
                .owner(ownerLimit(player, access));
        applyFilters(builder, request.filters(), tracker);

        String needle = normalise(request.text());
        if (!needle.isEmpty()) {
            Set<Integer> itemIds = matchingItemIds(tracker, needle);
            if (itemIds.isEmpty()) return QueryDto.SummaryResponse.of(request.requestId(), List.of());
            builder.items(itemIds);
        }

        int limit = clamp(request.limit(), MAX_ITEMS);
        List<WorldIndex.ItemSummary> summaries = tracker.index(dimensionId).summarise(builder.build());

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

        int id = request.requestId();
        if (!mayQuery(player, access)) return QueryDto.ContainerResponse.refused(id);

        if (QueryDto.ENDER_CHEST.equals(request.dimensionId())) {
            return QueryDto.ContainerResponse.of(id, List.of());
        }

        int itemId = tracker.palette().lookup(request.itemId());
        if (itemId < 0) return QueryDto.ContainerResponse.of(id, List.of());

        String dimensionId = requestedDimension(tracker, player, request.dimensionId());
        IndexQuery.Builder builder = IndexQuery.builder()
                .item(itemId)
                .center(centreOf(player))
                .owner(ownerLimit(player, access))
                .limit(clamp(request.limit(), MAX_CONTAINERS));
        applyFilters(builder, request.filters(), tracker);
        IndexQuery query = builder.build();

        List<SearchResult> results = tracker.search(dimensionId, query);
        if (refreshStale(player.level(), dimensionId, results) > 0) {
            results = tracker.search(dimensionId, query);
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
                    result.container().contentsKnown()));
        }
        return QueryDto.ContainerResponse.of(id, hits);
    }

    private static int refreshStale(ServerLevel level, String dimensionId, List<SearchResult> results) {
        if (results.isEmpty()) return 0;
        List<Long> positions = new java.util.ArrayList<>(results.size());
        for (SearchResult result : results) positions.add(result.container().pos());
        return Trackers.refreshDirty(level, dimensionId, positions);
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
            ServerPlayer player, QueryDto.SummaryRequest request) {

        Map<String, int[]> totals = new java.util.LinkedHashMap<>();
        Container ender = player.getEnderChestInventory();
        for (int slot = 0; slot < ender.getContainerSize(); slot++) {
            ItemStack stack = ender.getItem(slot);
            if (stack.isEmpty()) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) continue;
            totals.computeIfAbsent(id.toString(), key -> new int[1])[0] += stack.getCount();
        }

        String needle = normalise(request.text());
        List<QueryDto.ItemSummary> items = new java.util.ArrayList<>(totals.size());
        totals.forEach((itemId, count) -> {
            if (!needle.isEmpty() && !itemId.toLowerCase(java.util.Locale.ROOT).contains(needle)) return;
            items.add(new QueryDto.ItemSummary(itemId, count[0], 1, 0, 0.0));
        });
        items.sort((a, b) -> Integer.compare(b.totalCount(), a.totalCount()));
        return QueryDto.SummaryResponse.of(request.requestId(), items);
    }

    private static boolean hasEnderChestContents(ServerPlayer player) {
        Container ender = player.getEnderChestInventory();
        for (int slot = 0; slot < ender.getContainerSize(); slot++) {
            if (!ender.getItem(slot).isEmpty()) return true;
        }
        return false;
    }

    private static String requestedDimension(TrackerService tracker, ServerPlayer player, String asked) {
        String here = Trackers.dimensionId(player.level());
        if (asked == null || asked.isBlank()) return here;
        return tracker.dimensions().contains(asked) ? asked : here;
    }

    public static QueryDto.StatusResponse status(
            TrackerService tracker, ServerPlayer player,
            QueryDto.StatusRequest request, ChestTrackerConfig.Access access) {

        if (!mayQuery(player, access)) return QueryDto.StatusResponse.empty(request.requestId());

        List<QueryDto.DimensionSummary> dimensions = new java.util.ArrayList<>();
        for (String dimensionId : tracker.dimensions()) {
            int containers = tracker.index(dimensionId).stats().containers();
            if (containers > 0) dimensions.add(new QueryDto.DimensionSummary(dimensionId, containers));
        }
        dimensions.sort(java.util.Comparator.comparing(QueryDto.DimensionSummary::dimensionId));

        if (hasEnderChestContents(player)) {
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
        if (!effective.includeMachines()) builder.excludeTypes(machineTypeIds(tracker));
    }

    private static Set<Integer> matchingItemIds(TrackerService tracker, String needle) {
        Set<Integer> matches = new HashSet<>();
        List<String> entries = tracker.palette().entries();
        for (int id = 0; id < entries.size(); id++) {
            if (entries.get(id).toLowerCase(Locale.ROOT).contains(needle)) matches.add(id);
        }
        return matches;
    }

    private static Set<Integer> machineTypeIds(TrackerService tracker) {
        Set<Integer> ids = new HashSet<>();
        List<String> entries = tracker.palette().entries();
        for (int id = 0; id < entries.size(); id++) {
            if (MACHINES.contains(entries.get(id))) ids.add(id);
        }
        return ids;
    }
}
