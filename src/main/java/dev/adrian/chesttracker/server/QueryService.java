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

/**
 * Answers a {@link QueryDto} request against the index.
 *
 * <p>Both routes to the index end here: the client's own integrated server in
 * singleplayer, and the network handler on a dedicated server. That is the
 * point of the class. Two implementations of "what does this search return"
 * would drift, and the singleplayer one - which nobody exercises while working
 * on multiplayer - would be the one that rotted unnoticed.
 *
 * <p><b>Threading:</b> every method here touches the index, so every method
 * must be called on the server thread.
 */
public final class QueryService {

    /** Nothing the client asks for may exceed these, whatever it sends. */
    private static final int MAX_ITEMS = 2000;
    private static final int MAX_CONTAINERS = 128;

    /**
     * Block entity types whose contents churn constantly and are rarely
     * searched for. Filtering lives here rather than on the client because the
     * client may not have an index to filter, and because a limit applied
     * before filtering would spend result slots on rows nobody asked for.
     */
    // The two groups live in the config rather than here, so a modded machine
    // can be filed with the vanilla ones without a build. Anything in neither
    // is ordinary storage and is always shown.

    /**
     * Container entities near the asker, searched as if they were an index.
     *
     * <p>They are put into a throwaway {@link WorldIndex} and the real query is
     * run against it. That is the point: matching, nested contents, origin
     * filters, distance ranking and the result limit are one implementation,
     * and a chest minecart has to answer all of them exactly as a chest does.
     * The alternative - a second matcher here - is two sets of rules that would
     * drift apart on the first change to either.
     *
     * <p>Empty whenever there is nothing to read: no level, the filter is off,
     * or the question is about a dimension the asker is not standing in. That
     * last one is not a limitation to be worked around; entities exist only
     * where they are loaded, and we are not there.
     */
    private static Entities entityIndex(Level level, String hereDimension, String dimensionId,
                                        TrackerService tracker, QueryDto.Filters filters,
                                        long centre) {
        if (level == null || !ChestTrackerConfig.get().trackEntityContainers) return null;
        if (filters != null && !filters.includeEntities()) return null;
        if (!dimensionId.equals(hereDimension)) return null;

        List<dev.adrian.chesttracker.platform.EntityContainers.Found> found =
                dev.adrian.chesttracker.platform.EntityContainers
                        .near(level, dimensionId, tracker.palette(), centre);
        if (found.isEmpty()) return null;

        WorldIndex index = new WorldIndex(tracker.palette().intern(dimensionId));
        // Keyed on the record itself rather than on its position, because a
        // minecart standing on a chest shares that chest's block: by position
        // the chest would come back claiming to be an entity, and the client
        // would draw its box wherever the cart went.
        java.util.Map<ContainerRecord, Integer> ids = new java.util.IdentityHashMap<>();
        for (var one : found) {
            index.put(one.record());
            ids.put(one.record(), one.entityId());
        }
        return new Entities(index, ids);
    }

    /**
     * The throwaway index of container entities, and which entity each record
     * came from.
     *
     * @param entityIds identity-keyed; see {@link #entityIndex}
     */
    private record Entities(WorldIndex index, java.util.Map<ContainerRecord, Integer> entityIds) {}

    private QueryService() {}

    // --- Permission ---------------------------------------------------------

    /**
     * Whether this player may query at all under {@code access}.
     *
     * <p>{@code OWNED} can always query - it simply sees less - so only
     * {@code OP} can refuse outright.
     */
    public static boolean mayQuery(ServerPlayer player, ChestTrackerConfig.Access access) {
        return access != ChestTrackerConfig.Access.OP || isOperator(player);
    }

    private static boolean isOperator(ServerPlayer player) {
        // The same bar the commands use: permission level 2, the usual "op".
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    /**
     * The owner a query must be restricted to, or null for no restriction.
     *
     * <p>An operator is not restricted even under {@code OWNED}: the tier is
     * about what ordinary players may see, and an op can read the same data out
     * of {@code /chesttracker find} anyway.
     */
    private static java.util.UUID ownerLimit(ServerPlayer player, ChestTrackerConfig.Access access) {
        if (access != ChestTrackerConfig.Access.OWNED) return null;
        return isOperator(player) ? null : player.getUUID();
    }

    // --- Queries ------------------------------------------------------------

    /**
     * Re-reads containers about to be shown, where there is a live world to
     * re-read them from.
     *
     * <p>The client-side index has none - it holds what the player saw when
     * they last looked, and nothing on this machine can tell whether a chest
     * two hundred blocks away has changed since. So it passes a refresher that
     * refreshes nothing, and the same query code runs either way.
     */
    @FunctionalInterface
    public interface Refresher {
        int refresh(String dimensionId, List<Long> positions);

        /** For an index with no world behind it. */
        Refresher NONE = (dimensionId, positions) -> 0;
    }

    /** Totals every matching item, for the item-first grid. */
    public static QueryDto.SummaryResponse summarise(
            TrackerService tracker, ServerPlayer player,
            QueryDto.SummaryRequest request, ChestTrackerConfig.Access access) {

        if (!mayQuery(player, access)) return QueryDto.SummaryResponse.refused(request.requestId());

        return summarise(tracker, request, centreOf(player),
                Trackers.dimensionId(player.level()), ownerLimit(player, access),
                player.getEnderChestInventory(), player.level());
    }

    /**
     * The same summary, without a player.
     *
     * <p>Split out so the client-side index for a vanilla server runs this
     * code rather than a second implementation of it. Everything a
     * {@link ServerPlayer} was being consulted for - where the asker is, which
     * dimension they mean, whether an owner filter applies, whose ender chest -
     * is passed in, and nothing else about them was ever used.
     *
     * @param centre         packed position to rank distance from
     * @param hereDimension  the dimension the asker is in, used when the
     *                       request names one this index has never heard of
     * @param ownerLimit     restrict to one player's containers, or null
     * @param enderChest     the asker's ender chest when it can be read live,
     *                       null when it can only come out of the index
     */
    public static QueryDto.SummaryResponse summarise(
            TrackerService tracker, QueryDto.SummaryRequest request,
            long centre, String hereDimension, java.util.UUID ownerLimit, Container enderChest) {
        return summarise(tracker, request, centre, hereDimension, ownerLimit, enderChest, null);
    }

    /** As above, and also totalling the container entities around the asker. */
    public static QueryDto.SummaryResponse summarise(
            TrackerService tracker, QueryDto.SummaryRequest request,
            long centre, String hereDimension, java.util.UUID ownerLimit, Container enderChest,
            Level entitySource) {

        // A live read beats a stored one and is the only version that is
        // correct about whose ender chest it is. Without one - the client-side
        // case - it is served out of the index like any other view.
        if (QueryDto.ENDER_CHEST.equals(request.dimensionId()) && enderChest != null) {
            return enderChestSummary(enderChest, request);
        }

        String dimensionId = requestedDimension(tracker, hereDimension, request.dimensionId());
        IndexQuery.Builder builder = IndexQuery.builder()
                .center(centre)
                .owner(ownerLimit);
        applyFilters(builder, request.filters(), tracker);

        // The typed text is a small language, not just a name: see SearchQuery.
        // Everything it can ask that the index and the registries can answer is
        // resolved here; the client resolves the rest against what it gets back.
        dev.adrian.chesttracker.core.util.SearchQuery search =
                dev.adrian.chesttracker.core.util.SearchQuery.parse(request.text());

        Set<Integer> itemIds = null;
        String needle = normalise(search.text());
        if (!needle.isEmpty()) itemIds = matchingItemIds(tracker, needle);

        Set<Integer> byCategory = itemIdsForTerms(tracker, search);
        if (byCategory != null) {
            // Categories narrow: "@create #logs" is create's logs, not both
            // lists put together.
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
            // Asking for hoppers outranks the toggle that hides them. The
            // filter row is a default; typing the question is not.
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
            // A palette id with no string behind it cannot be named on the wire,
            // and an unnamed row is no use to the client anyway.
            if (itemId == null) continue;
            items.add(new QueryDto.ItemSummary(itemId, summary.totalCount(),
                    summary.containerCount(), summary.nestedCount(), summary.nearestDistSq()));
        }
        return QueryDto.SummaryResponse.of(request.requestId(), items);
    }

    /** The containers holding one item, nearest first. */
    public static QueryDto.ContainerResponse containers(
            TrackerService tracker, ServerPlayer player,
            QueryDto.ContainerRequest request, ChestTrackerConfig.Access access) {

        if (!mayQuery(player, access)) return QueryDto.ContainerResponse.refused(request.requestId());

        ServerLevel level = player.level();
        return containers(tracker, request, centreOf(player),
                Trackers.dimensionId(level), ownerLimit(player, access),
                // Resolved from the dimension being searched, not from the one
                // the player is standing in. A query can name another dimension
                // - the search screen's buttons do, and so does the key when it
                // looks for an item that is not here - and refreshing a Nether
                // position against the overworld reads the wrong block, which
                // can decide a container is gone and delete it.
                (dimensionId, positions) ->
                        Trackers.refreshDirty(Trackers.levelFor(dimensionId), dimensionId, positions),
                // Container entities are read from the level the player is in,
                // which is the only one whose entities are loaded here.
                level);
    }

    /** As above, without a player. See {@link #summarise(TrackerService, QueryDto.SummaryRequest, long, String, java.util.UUID, Container)}. */
    public static QueryDto.ContainerResponse containers(
            TrackerService tracker, QueryDto.ContainerRequest request,
            long centre, String hereDimension, java.util.UUID ownerLimit, Refresher refresher) {
        return containers(tracker, request, centre, hereDimension, ownerLimit, refresher, null);
    }

    /**
     * As above, and also reading the container entities around the asker.
     *
     * @param entitySource the level to read chest minecarts and the like from,
     *                     or null not to. Never persisted - see
     *                     {@link dev.adrian.chesttracker.platform.EntityContainers}
     */
    public static QueryDto.ContainerResponse containers(
            TrackerService tracker, QueryDto.ContainerRequest request,
            long centre, String hereDimension, java.util.UUID ownerLimit, Refresher refresher,
            Level entitySource) {

        int id = request.requestId();

        // Nothing to walk to. The ender chest is wherever the player is, so a
        // list of places holding an item has no places in it.
        if (QueryDto.ENDER_CHEST.equals(request.dimensionId())) {
            return QueryDto.ContainerResponse.of(id, List.of());
        }

        // Ids the palette has never interned are dropped rather than failing
        // the query: a set of wanted items may legitimately include some this
        // world has never held, and the rest are still worth finding.
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

        // The same terms the grid was narrowed by, so the list of places agrees
        // with the count that was clicked. The item ids are already decided -
        // the player picked one - so only what a term says about the container
        // or the stack applies here.
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
        // Only re-search when a refresh actually changed something. A refresh
        // can drop a container that is no longer there, so the first result
        // list cannot simply be reused - but usually nothing was stale and the
        // second search is skipped entirely.
        if (refreshStale(refresher, dimensionId, results) > 0) {
            results = tracker.search(dimensionId, query);
        }

        Entities entities = entityIndex(entitySource, hereDimension, dimensionId,
                tracker, request.filters(), centre);
        if (entities != null) {
            List<SearchResult> both = new java.util.ArrayList<>(results);
            both.addAll(entities.index().query(query));
            // Re-ranked and re-capped over the whole set: the limit means "the
            // nearest N containers", and applying it to each source separately
            // would answer "the nearest N chests and also the nearest N carts".
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

    // --- Helpers ------------------------------------------------------------

    /**
     * Brings any about-to-be-shown container up to date, if it is not already.
     *
     * <p>A stale result is the failure that costs the mod its credibility, but
     * that does not mean re-reading everything: live tracking already refreshes
     * changed containers every tick, so all but the ones still queued are
     * current. Only those are re-read here, and there are usually none.
     *
     * @return how many were actually re-read
     */
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

    /**
     * The asking player's own ender chest, totalled like any other view.
     *
     * <p>Read straight off the player rather than out of the index. There is
     * nothing to index: the block stores nothing, the contents live in player
     * data, and they change without any of the events the index listens to. A
     * live read is also the only version that is correct about whose ender
     * chest it is - a stored one would have to answer "whose", and the answer
     * is never anybody but the asker.
     */
    private static QueryDto.SummaryResponse enderChestSummary(
            Container ender, QueryDto.SummaryRequest request) {

        // {total, nested}, so the detail panel can say how much of it is
        // sealed inside something - the same question it answers everywhere else.
        Map<String, int[]> totals = new java.util.LinkedHashMap<>();
        boolean nested = request.filters().includeNested();
        for (int slot = 0; slot < ender.getContainerSize(); slot++) {
            collectEnderStack(totals, ender.getItem(slot), nested, false);
        }

        String[] tokens = dev.adrian.chesttracker.core.util.SearchText.tokens(request.text());
        List<QueryDto.ItemSummary> items = new java.util.ArrayList<>(totals.size());
        totals.forEach((itemId, counts) -> {
            if (!dev.adrian.chesttracker.core.util.SearchText.matches(itemId, tokens)) return;
            // One container, no distance: it is in your pocket, not the world.
            items.add(new QueryDto.ItemSummary(itemId, counts[0], 1, counts[1], 0.0));
        });
        items.sort((a, b) -> Integer.compare(b.totalCount(), a.totalCount()));
        return QueryDto.SummaryResponse.of(request.requestId(), items);
    }

    /**
     * Adds one stack, and what is inside it, to the ender chest totals.
     *
     * <p>A shulker box in an ender chest is counted both ways: the box itself,
     * because it is a thing you own, and its contents, because "list every item
     * in it" means the items. The second half is skipped when the menu's nested
     * filter is off, exactly as it is for containers in the world.
     */
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

    /** True when the asking player has anything in their ender chest. */
    private static boolean hasEnderChestContents(Container ender) {
        if (ender == null) return false;
        for (int slot = 0; slot < ender.getContainerSize(); slot++) {
            if (!ender.getItem(slot).isEmpty()) return true;
        }
        return false;
    }

    /**
     * Which index a request means.
     *
     * <p>An unknown or blank name falls back to where the player is standing,
     * so a client asking about a dimension this server has never heard of gets
     * its own surroundings rather than an error - and cannot use the field to
     * probe what dimensions exist.
     */
    private static String requestedDimension(TrackerService tracker, String here, String asked) {
        if (asked == null || asked.isBlank()) return here;
        return tracker.dimensions().contains(asked) ? asked : here;
    }

    /**
     * What the index holds, and whether it is still filling.
     *
     * <p>Permission-gated like everything else: a player who may not search may
     * not learn which dimensions have storage in them either, since that is the
     * same information at lower resolution.
     */
    public static QueryDto.StatusResponse status(
            TrackerService tracker, ServerPlayer player,
            QueryDto.StatusRequest request, ChestTrackerConfig.Access access) {

        if (!mayQuery(player, access)) return QueryDto.StatusResponse.empty(request.requestId());
        return status(tracker, request, hasEnderChestContents(player.getEnderChestInventory()));
    }

    /** As above, without a player. */
    public static QueryDto.StatusResponse status(
            TrackerService tracker, QueryDto.StatusRequest request, boolean offerEnderChest) {

        List<QueryDto.DimensionSummary> dimensions = new java.util.ArrayList<>();
        for (String dimensionId : tracker.dimensions()) {
            // Never a real dimension, and it is appended below on its own terms.
            // Without this the client index, which stores it like any other
            // view, would list it twice and sort it under "c".
            if (QueryDto.ENDER_CHEST.equals(dimensionId)) continue;
            int containers = tracker.index(dimensionId).stats().containers();
            if (containers > 0) dimensions.add(new QueryDto.DimensionSummary(dimensionId, containers));
        }
        dimensions.sort(java.util.Comparator.comparing(QueryDto.DimensionSummary::dimensionId));

        // Appended after the sort, so it is always last rather than filed under
        // "c" among the real dimensions. Offered only when it holds something:
        // an empty ender chest is a button that answers nothing.
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

    /**
     * Adds two summaries of the same items together.
     *
     * <p>Kept out of {@link WorldIndex} because it is not a property of an
     * index - it is what to do when an item is in two of them, which only
     * happens because entity containers are searched separately from the
     * stored ones. Counts and container tallies add; the nearest distance is
     * whichever is nearer.
     */
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

        // Re-sorted, because the grid is built most-plentiful first and the
        // caller truncates to a limit straight after this.
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

        // One exclusion set for both groups: the query takes a single list, and
        // "hide machines" and "hide furniture" are two reasons to be on it.
        ChestTrackerConfig config = ChestTrackerConfig.get();
        Set<String> hidden = new HashSet<>();
        if (!effective.includeMachines()) hidden.addAll(config.machineTypes);
        if (!effective.includeUtility()) hidden.addAll(config.utilityTypes);
        if (!hidden.isEmpty()) builder.excludeTypes(typeIdsOf(tracker, hidden));
    }

    /**
     * Word match over interned item ids, so "diamond" finds every variant and
     * "blue wool" finds {@code minecraft:light_blue_wool}.
     *
     * <p>The words are split once here rather than per candidate; see
     * {@link dev.adrian.chesttracker.core.util.SearchText}.
     */
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

    /**
     * The items a search's categories allow, or null when they constrain none.
     *
     * <p>Only the categories that can be answered from an item id and the
     * game's registries - which mod it came from, and what it is tagged as. The
     * ones that need to know about a particular stack rather than a kind of
     * item are answered elsewhere; see {@code SearchQuery.Category#isIndexed}.
     */
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

    /**
     * Palette ids whose namespace names one of these mods.
     *
     * <p>Prefix-matched, so {@code @cre} narrows to Create while it is being
     * typed rather than only once the name is finished.
     */
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

    /**
     * Palette ids for the items in these tags.
     *
     * <p>The tag is read from the game's registry rather than from anything
     * this mod stores: tags are datapack content and change with the pack, so
     * the only correct answer is the one the running game would give.
     *
     * <p>A bare {@code #logs} means {@code minecraft:logs}, the same shorthand
     * the command parser allows.
     */
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
                // Only what this world actually holds: an id the palette has
                // never interned cannot be in any container in it, and asking
                // it to intern one here would grow the palette from a search.
                int paletteId = tracker.palette().lookup(itemId.toString());
                if (paletteId >= 0) ids.add(paletteId);
            }
        }
        return ids;
    }

    /**
     * The container types a search restricts to, or null when it names none.
     *
     * <p>Word-matched like the item search, so {@code >barrel} finds barrels
     * and {@code >shulker} finds every colour of shulker box.
     */
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

    /**
     * The per-stack details a search asks for, or null when it asks for none.
     *
     * <p>Resolved against the palette the same way items are, because the
     * details are interned into it: {@code ench:mend} becomes the ids of every
     * detail string that is an enchantment whose name contains "mend".
     *
     * <p>Several detail terms mean <em>any</em> of them rather than all - a
     * stack has one potion in it, and "mending or unbreaking" is the question
     * people actually ask. The other categories still narrow.
     */
    private static Set<Integer> detailIdsForTerms(
            TrackerService tracker, dev.adrian.chesttracker.core.util.SearchQuery search) {

        List<String> enchantments = search.valuesOf(
                dev.adrian.chesttracker.core.util.SearchQuery.Category.ENCHANTMENT);
        List<String> potions = search.valuesOf(
                dev.adrian.chesttracker.core.util.SearchQuery.Category.POTION);
        List<String> tooltip = search.valuesOf(
                dev.adrian.chesttracker.core.util.SearchQuery.Category.TOOLTIP);

        if (enchantments.isEmpty() && potions.isEmpty() && tooltip.isEmpty()) return null;

        // Split once, outside the walk. The terms are the same for every entry,
        // and the palette holds every enchantment, potion and lore line in the
        // world - so tokenising them per entry did the same string work tens of
        // thousands of times per keystroke.
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
                    // Anywhere in the tooltip: a name written on an anvil, a
                    // line of lore, an enchantment, a potion. Everything this
                    // index knows about one stack beyond what it is.
                    || matchesDetail(entry, null, tooltipTerms)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Each term split into its words, once, for repeated matching. */
    private static String[][] tokenise(List<String> terms) {
        String[][] tokens = new String[terms.size()][];
        for (int i = 0; i < terms.size(); i++) {
            tokens[i] = dev.adrian.chesttracker.core.util.SearchText.tokens(terms.get(i));
        }
        return tokens;
    }

    /**
     * @param prefix the kind of detail to match, or null for any kind - which
     *               is what a tooltip search is
     * @param wanted terms already split by {@link #tokenise}
     */
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

    /** Whether a palette entry is a stack detail at all, rather than an id. */
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

    /**
     * The palette ids for a set of container type names.
     *
     * <p>Asked of the palette by name rather than by walking it. The exclusion
     * set is a couple of dozen fixed strings from the config, and the default
     * filters hide both machines and functional blocks - so this runs on every
     * query, and walking a palette of every enchantment and lore line in the
     * world to find twenty known names was the wrong way round.
     */
    private static Set<Integer> typeIdsOf(TrackerService tracker, Set<String> typeNames) {
        StringPalette palette = tracker.palette();
        Set<Integer> ids = new HashSet<>(typeNames.size() * 2);
        for (String name : typeNames) {
            int id = palette.lookup(name);
            // -1 means the world has never held one, so nothing can match it.
            if (id >= 0) ids.add(id);
        }
        return ids;
    }
}
