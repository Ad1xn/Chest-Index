package dev.adrian.chestindex.core.index;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.util.Bits;
import dev.adrian.chestindex.core.util.BlockKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The container index for a single dimension.
 *
 * <p>Three maps, each earning its place:
 * <ul>
 *   <li>{@code byPos} is the primary store.
 *   <li>{@code byChunk} makes invalidation and reconciliation per-chunk set
 *       operations rather than full scans - without it, "this chunk changed"
 *       would mean walking every record in the world.
 *   <li>{@code byItem} is an inverted index so a search costs O(matches)
 *       instead of O(containers). Searching 100k containers for one item by
 *       brute force is the difference between instant and visibly slow.
 * </ul>
 *
 * <p>Positions are {@link BlockKey}-packed longs, and identifiers are palette
 * ids, so this class holds no Minecraft types and no strings per record.
 *
 * <p><b>Not thread-safe.</b> The scanner threads hand records to the owning
 * service, which applies them on a single thread.
 */
public final class WorldIndex {

    private final Map<Long, ContainerRecord> byPos = new HashMap<>();
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();
    private final Map<Integer, Set<Long>> byItem = new HashMap<>();

    private final int dimensionId;

    /**
     * Bumped by anything that changes what a query would answer.
     *
     * <p>Only used to date the cache below. Distinct from the change counter
     * {@code TrackerService} keeps, which deliberately does <em>not</em> move
     * when a re-read finds nothing new: this one has to move whenever the
     * stored records do, however uninteresting the change.
     */
    private long revision;

    /**
     * The last summary, and what it was the answer to.
     *
     * <p>The search screen asks the same question repeatedly: once per
     * keystroke, and again about once a second while it is open so the grid
     * follows the world. On a large world a summary is a walk of every record
     * and every stack in it - tens of milliseconds - and between two of those
     * polls nothing has usually changed at all.
     *
     * <p>So the answer is kept with the revision it was computed at, and
     * handed back unchanged while the index has not moved. One list of a few
     * thousand small records, replaced rather than accumulated.
     */
    private IndexQuery cachedSummaryQuery;
    private long cachedSummaryRevision = -1;
    private List<ItemSummary> cachedSummary;

    /**
     * Highest item id any record has ever held.
     *
     * <p>A summary accumulates into arrays indexed by item id, and this is how
     * they are sized. A high-water mark rather than a live maximum on purpose:
     * it only grows, so it can never be too small for a record still in the
     * index, and recomputing it on every removal would cost a full scan to save
     * a few kilobytes.
     */
    private int maxItemId = -1;

    public WorldIndex(int dimensionId) {
        this.dimensionId = dimensionId;
    }

    public int dimensionId() {
        return dimensionId;
    }

    public int size() {
        return byPos.size();
    }

    public boolean isEmpty() {
        return byPos.isEmpty();
    }

    public ContainerRecord get(long pos) {
        return byPos.get(pos);
    }

    public boolean contains(long pos) {
        return byPos.containsKey(pos);
    }

    public Collection<ContainerRecord> all() {
        return Collections.unmodifiableCollection(byPos.values());
    }

    /**
     * Inserts or replaces the record at its position, keeping every secondary
     * index consistent. Replacing unindexes the old record first so a stale
     * item no longer present in the container cannot linger in the inverted
     * index and produce a phantom search hit.
     */
    public void put(ContainerRecord record) {
        revision++;
        unindex(record.pos());

        byPos.put(record.pos(), record);
        byChunk.computeIfAbsent(record.chunkKey(), k -> new HashSet<>()).add(record.pos());
        for (StackEntry entry : record.contents()) {
            byItem.computeIfAbsent(entry.itemId(), k -> new HashSet<>()).add(record.pos());
            if (entry.itemId() > maxItemId) maxItemId = entry.itemId();
        }
    }

    /**
     * Refreshes a record whose data is unchanged, without moving the revision.
     *
     * <p>The live drain re-reads dirty containers every tick and each re-read
     * writes a fresh {@code lastSeenTick}, which the UI shows as staleness - so
     * the write cannot simply be skipped. Going through {@link #put} for it
     * would tear down and rebuild this record's row in the inverted index and
     * move the revision, invalidating the summary cache; on a world with a
     * hopper line that happens every tick, so the cache never survives long
     * enough to answer anything. Nothing an index is keyed on has changed here,
     * so only the stored record is replaced.
     *
     * <p>Anything that is not in fact an unchanged re-read falls through to
     * {@link #put}, so this cannot be used to slip a real change past the
     * secondary indexes.
     */
    public void touch(ContainerRecord record) {
        ContainerRecord existing = byPos.get(record.pos());
        if (existing == null || !existing.sameDataAs(record)) {
            put(record);
            return;
        }
        byPos.put(record.pos(), record);
    }

    /** Removes the record at {@code pos}, if any. Returns what was removed, or null. */
    public ContainerRecord remove(long pos) {
        revision++;
        return unindex(pos);
    }

    /**
     * Drops a position from every index without touching the revision, so a
     * replacement counts as one move rather than two.
     */
    private ContainerRecord unindex(long pos) {
        ContainerRecord existing = byPos.remove(pos);
        if (existing == null) return null;

        Set<Long> chunk = byChunk.get(existing.chunkKey());
        if (chunk != null) {
            chunk.remove(pos);
            if (chunk.isEmpty()) byChunk.remove(existing.chunkKey());
        }
        for (StackEntry entry : existing.contents()) {
            Set<Long> holders = byItem.get(entry.itemId());
            if (holders != null) {
                holders.remove(pos);
                if (holders.isEmpty()) byItem.remove(entry.itemId());
            }
        }
        return existing;
    }

    /** Positions indexed within one chunk. Never null; empty when nothing is indexed there. */
    public Set<Long> positionsInChunk(long chunkKey) {
        Set<Long> positions = byChunk.get(chunkKey);
        return positions == null ? Set.of() : Collections.unmodifiableSet(positions);
    }

    public void removeChunk(long chunkKey) {
        for (Long pos : new ArrayList<>(positionsInChunk(chunkKey))) {
            remove(pos);
        }
    }

    /**
     * Drops every indexed position in a chunk that is not in {@code actual}.
     *
     * <p>This is how a container that no longer exists leaves the index. A live
     * break hook only catches removals that happen while we are watching; a
     * chest broken while the mod was disabled, the server ran without it, or
     * the world was edited externally is only ever caught here, when the chunk
     * is next seen. Callers pass the positions that genuinely still hold a
     * tracked container right now.
     *
     * @return how many stale records were dropped
     */
    public int reconcileChunk(long chunkKey, Set<Long> actual) {
        Set<Long> indexed = byChunk.get(chunkKey);
        if (indexed == null || indexed.isEmpty()) return 0;

        List<Long> stale = new ArrayList<>();
        for (Long pos : indexed) {
            if (!actual.contains(pos)) stale.add(pos);
        }
        for (Long pos : stale) remove(pos);
        return stale.size();
    }

    public void clear() {
        revision++;
        byPos.clear();
        byChunk.clear();
        byItem.clear();
    }

    // --- Querying -----------------------------------------------------------

    /**
     * A query's set filters, turned into something that can be tested without
     * allocating.
     *
     * <p>Built once per query and consulted once per stack. The sets on
     * {@link IndexQuery} are {@code Set<Integer>}, so asking them costs an
     * {@link Integer} box every time - which on a sixty-thousand-container
     * world with a dozen stacks each is close to a million allocations for one
     * keystroke. That measured as the single largest cost in the search path.
     */
    private static final class Plan {
        /** Item ids to match, or null for no item filter. */
        final long[] items;
        /** The same ids as a list, for driving the inverted index. */
        final int[] itemIds;
        final long[] types;
        final long[] excludedTypes;
        /** Per-stack details to match, or null for no detail filter. */
        final long[] details;
        final boolean hasOrigins;

        Plan(IndexQuery query) {
            this.items = Bits.of(query.itemIds());
            this.types = Bits.of(query.typeIds());
            this.excludedTypes = Bits.of(query.excludedTypeIds());
            this.details = Bits.of(query.detailIds());
            this.hasOrigins = !query.origins().isEmpty();

            if (items == null) {
                this.itemIds = null;
            } else {
                this.itemIds = new int[query.itemIds().size()];
                int i = 0;
                for (Integer id : query.itemIds()) this.itemIds[i++] = id == null ? -1 : id;
            }
        }
    }

    /** What a walk hands back: a record that passed every filter, and how far away it is. */
    private interface Visitor {
        void accept(ContainerRecord record, double distanceSq);
    }

    /**
     * Runs a query and returns matches ranked nearest-first.
     *
     * <p>Candidate selection matters more than filtering here: an item filter
     * goes through the inverted index, a bounded radius walks only the chunks in
     * range, and only an unbounded query with no item filter falls back to a
     * full scan.
     */
    public List<SearchResult> query(IndexQuery query) {
        Plan plan = new Plan(query);
        return query.limit() > 0 ? nearest(query, plan) : all(query, plan);
    }

    /**
     * The nearest {@code limit} matches, without collecting the rest.
     *
     * <p>This is what clicking a row asks, and the answer is almost always
     * "the nearest sixty-four" out of a great many. Collecting every match and
     * sorting it was the whole cost: an item people keep everywhere - cobble,
     * or in the benchmark a diamond in every chest - meant sixty thousand
     * result objects, sixty thousand match lists, and a sort over all of them,
     * to then throw away all but sixty-four. That measured at forty-six
     * milliseconds, on the click.
     *
     * <p>Instead a heap of at most {@code limit} entries keeps the nearest seen
     * so far, ordered <em>farthest-first</em> so the one to drop is the one on
     * top. A candidate further away than the worst kept one is rejected on a
     * single comparison, before its matching stacks are collected at all -
     * which is where the allocations were.
     */
    private List<SearchResult> nearest(IndexQuery query, Plan plan) {
        int limit = query.limit();
        PriorityQueue<SearchResult> kept = new PriorityQueue<>(
                Math.min(limit, 64), (a, b) -> Double.compare(b.distanceSq(), a.distanceSq()));

        walk(query, plan, (record, distSq) -> {
            // Full: anything not nearer than the worst kept cannot make it in,
            // and asking that costs one comparison rather than a walk of the
            // container's contents.
            if (kept.size() == limit && distSq >= kept.peek().distanceSq()) return;
            if (!hasMatch(record, query, plan)) return;

            kept.add(new SearchResult(record, distSq, collectMatches(record, query, plan)));
            if (kept.size() > limit) kept.poll();
        });

        SearchResult[] results = kept.toArray(new SearchResult[0]);
        java.util.Arrays.sort(results, (a, b) -> Double.compare(a.distanceSq(), b.distanceSq()));
        return List.of(results);
    }

    /** Every match, ranked nearest-first, for the callers that want the lot. */
    private List<SearchResult> all(IndexQuery query, Plan plan) {
        List<SearchResult> results = new ArrayList<>();

        walk(query, plan, (record, distSq) -> {
            // A filter that matched nothing in this container is not a hit, even
            // though the inverted index nominated it (nesting can be excluded,
            // and a detail filter is only answerable stack by stack).
            if (!hasMatch(record, query, plan)) return;
            results.add(new SearchResult(record, distSq, collectMatches(record, query, plan)));
        });

        results.sort((a, b) -> Double.compare(a.distanceSq(), b.distanceSq()));
        // Nobody else holds this list, so a view of it is as immutable as a copy
        // and does not walk the whole result set a second time.
        return Collections.unmodifiableList(results);
    }

    /**
     * Whether this container holds anything the query asked for.
     *
     * <p>The same test {@link #collectMatches} makes, without building the list
     * - so a container that is about to be rejected costs nothing but the walk
     * of its stacks.
     */
    private static boolean hasMatch(ContainerRecord record, IndexQuery query, Plan plan) {
        if (plan.items == null && plan.details == null) return true;

        for (StackEntry entry : record.contents()) {
            if (!query.includeNested() && entry.isNested()) continue;
            if (plan.items != null && !Bits.test(plan.items, entry.itemId())) continue;
            if (!carriesWantedDetail(entry, plan)) continue;
            return true;
        }
        return false;
    }

    /**
     * Visits every record matching the query's non-item filters, once each.
     *
     * <p>Three ways in, picked by which one reads the fewest records:
     *
     * <ul>
     *   <li>An item filter narrow enough to be worth it goes through the
     *       inverted index.
     *   <li>A bounded radius walks only the chunks that could reach it.
     *   <li>Anything else walks the whole dimension.
     * </ul>
     *
     * <p>The first is not automatic. A short search term - "e" - matches most
     * of the palette, and the union of those items' holder lists is both bigger
     * than the index and has to be deduplicated into a set before it can be
     * used. Comparing the summed holder counts against the index size first
     * costs one lookup per item and turns the worst case back into a plain
     * scan, which visits every record exactly once and allocates nothing.
     */
    private void walk(IndexQuery query, Plan plan, Visitor visitor) {
        if (plan.items != null) {
            long estimate = 0;
            for (int itemId : plan.itemIds) {
                Set<Long> holders = byItem.get(itemId);
                if (holders != null) estimate += holders.size();
            }
            // Nothing indexed holds any of them, so there is nothing to walk.
            if (estimate == 0) return;
            if (estimate < byPos.size()) {
                walkHolders(query, plan, visitor);
                return;
            }
        } else if (query.hasDistanceLimit()) {
            if (walkChunkRadius(query, plan, visitor)) return;
        }

        // Straight down the primary store. Iterating the values reaches the
        // records directly, where going via the keys would pay for a hash
        // lookup per candidate to find what it was already standing on.
        for (ContainerRecord record : byPos.values()) {
            offer(record, query, plan, visitor);
        }
    }

    /** Walks the containers holding any of the wanted items. */
    private void walkHolders(IndexQuery query, Plan plan, Visitor visitor) {
        // One item needs no deduplication: a container appears in one holder
        // list at most once. That is the case behind every "where is this"
        // click, so it is worth not allocating a set for.
        if (plan.itemIds.length == 1) {
            Set<Long> holders = byItem.get(plan.itemIds[0]);
            if (holders == null) return;
            for (Long pos : holders) {
                ContainerRecord record = byPos.get(pos);
                if (record != null) offer(record, query, plan, visitor);
            }
            return;
        }

        Set<Long> seen = new HashSet<>();
        for (int itemId : plan.itemIds) {
            Set<Long> holders = byItem.get(itemId);
            if (holders == null) continue;
            for (Long pos : holders) {
                // A container holding two of the wanted items must be visited
                // once, or a summary counts everything in it twice.
                if (!seen.add(pos)) continue;
                ContainerRecord record = byPos.get(pos);
                if (record != null) offer(record, query, plan, visitor);
            }
        }
    }

    /**
     * Walks the chunks a radius could possibly touch.
     *
     * @return false when the square covers more chunks than are indexed at all,
     *         in which case the caller should just scan - iterating a huge empty
     *         square costs more than reading every record there is
     */
    private boolean walkChunkRadius(IndexQuery query, Plan plan, Visitor visitor) {
        int centerChunkX = BlockKey.x(query.center()) >> 4;
        int centerChunkZ = BlockKey.z(query.center()) >> 4;
        int chunkRadius = (int) Math.ceil(query.maxDistance() / 16.0) + 1;

        long chunkSquare = (2L * chunkRadius + 1) * (2L * chunkRadius + 1);
        if (chunkSquare > byChunk.size()) return false;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                Set<Long> positions = byChunk.get(BlockKey.chunkKey(cx, cz));
                if (positions == null) continue;
                for (Long pos : positions) {
                    ContainerRecord record = byPos.get(pos);
                    if (record != null) offer(record, query, plan, visitor);
                }
            }
        }
        return true;
    }

    /** Applies the filters and the distance limit, then hands the record on. */
    private void offer(ContainerRecord record, IndexQuery query, Plan plan, Visitor visitor) {
        if (!matchesFilters(record, query, plan)) return;

        double distSq = BlockKey.distanceSq(query.center(), record.pos());
        if (query.hasDistanceLimit() && distSq > query.maxDistanceSq()) return;

        visitor.accept(record, distSq);
    }

    private boolean matchesFilters(ContainerRecord record, IndexQuery query, Plan plan) {
        if (plan.hasOrigins && !query.origins().contains(record.origin())) return false;
        if (plan.types != null && !Bits.test(plan.types, record.typeId())) return false;
        if (plan.excludedTypes != null && Bits.test(plan.excludedTypes, record.typeId())) return false;
        if (query.unlootedOnly() && !record.unlooted()) return false;
        if (query.knownContentsOnly() && !record.contentsKnown()) return false;
        // An owner-restricted query must not fall back to "show everything" for
        // records whose owner was never observed - a permission tier that leaks
        // on missing data is not a permission tier.
        if (query.owner() != null && !query.owner().equals(record.owner())) return false;
        return true;
    }

    private List<StackEntry> collectMatches(ContainerRecord record, IndexQuery query, Plan plan) {
        if (plan.items == null && plan.details == null) return List.of();

        List<StackEntry> matches = new ArrayList<>();
        for (StackEntry entry : record.contents()) {
            if (!query.includeNested() && entry.isNested()) continue;
            if (plan.items != null && !Bits.test(plan.items, entry.itemId())) continue;
            if (!carriesWantedDetail(entry, plan)) continue;
            matches.add(entry);
        }
        return matches;
    }

    /**
     * Whether a stack carries one of the details the query asked for.
     *
     * <p>True when nothing was asked. A stack with no details at all - which is
     * almost every stack in a world - fails any detail filter immediately,
     * which is what makes "everything enchanted with mending" cheap: the test
     * is an empty-list check for all but a handful of stacks.
     */
    private static boolean carriesWantedDetail(StackEntry entry, Plan plan) {
        if (plan.details == null) return true;
        if (entry.details().isEmpty()) return false;
        for (int detail : entry.details()) {
            if (Bits.test(plan.details, detail)) return true;
        }
        return false;
    }

    /**
     * One item, totalled across every container that matched.
     *
     * @param itemId         palette id of the item
     * @param totalCount     how many exist in total
     * @param containerCount how many containers hold at least one
     * @param nestedCount    how many of those are inside a shulker box rather
     *                       than loose in the container, which is the
     *                       difference between "you have 900 of these" and
     *                       "you have 900 of these and cannot see any of them"
     * @param nearestDistSq  squared distance to the closest of those containers
     */
    public record ItemSummary(int itemId, int totalCount, int containerCount,
                              int nestedCount, double nearestDistSq)
            implements Comparable<ItemSummary> {
        @Override
        public int compareTo(ItemSummary other) {
            // Most plentiful first, ties broken by proximity, so both "what do I
            // have a lot of" and "where is the nearest one" read naturally.
            int byCount = Integer.compare(other.totalCount, totalCount);
            return byCount != 0 ? byCount : Double.compare(nearestDistSq, other.nearestDistSq);
        }
    }

    /**
     * Running totals per item, held in arrays indexed by palette id.
     *
     * <p>The obvious shape is a {@code Map<Integer, int[]>} plus a
     * {@code Set<Integer>} per container to count each one once. That was the
     * shape, and between them they boxed an {@link Integer} for every stack in
     * every container the query touched, plus a {@link Double} for every
     * distance comparison. Palette ids are small and dense, so arrays hold the
     * same information with no allocation per stack at all - one allocation per
     * query, sized by the largest id in use.
     */
    private static final class Totals {
        private final int[] count;
        private final int[] containers;
        private final int[] nested;
        private final double[] nearest;

        /**
         * Which container last contributed each item, as a sequence number.
         *
         * <p>This is what counts "two stacks of cobblestone in one chest is one
         * container" without a per-container set: the id is stamped with the
         * container's number, and a second stack of the same item in the same
         * container finds its own stamp already there.
         */
        private final int[] lastContainer;

        private final boolean[] touched;
        private final int[] order;
        private int touchedCount;
        private int containerSeq;

        Totals(int capacity) {
            this.count = new int[capacity];
            this.containers = new int[capacity];
            this.nested = new int[capacity];
            this.nearest = new double[capacity];
            this.lastContainer = new int[capacity];
            this.touched = new boolean[capacity];
            this.order = new int[capacity];
        }

        void add(ContainerRecord record, double distanceSq, IndexQuery query, Plan plan) {
            // A container whose contents we cannot know must not inflate a total.
            if (!record.contentsKnown()) return;

            containerSeq++;
            for (StackEntry entry : record.contents()) {
                if (!query.includeNested() && entry.isNested()) continue;

                int id = entry.itemId();
                if (id < 0 || id >= count.length) continue;
                if (plan.items != null && !Bits.test(plan.items, id)) continue;
                // The grid's counts and the list of places behind them have to
                // be answering the same question.
                if (!carriesWantedDetail(entry, plan)) continue;

                if (!touched[id]) {
                    touched[id] = true;
                    order[touchedCount++] = id;
                    nearest[id] = distanceSq;
                } else if (distanceSq < nearest[id]) {
                    nearest[id] = distanceSq;
                }

                count[id] += entry.count();
                if (entry.isNested()) nested[id] += entry.count();
                // Several stacks of one item in one chest is still one container.
                if (lastContainer[id] != containerSeq) {
                    lastContainer[id] = containerSeq;
                    containers[id]++;
                }
            }
        }

        List<ItemSummary> build() {
            // Ascending id first so that items tied on both count and distance
            // come out in a stable order rather than in whatever order the walk
            // happened to reach them - the sort below is stable, so this is
            // what breaks the tie.
            Arrays.sort(order, 0, touchedCount);

            List<ItemSummary> summaries = new ArrayList<>(touchedCount);
            for (int i = 0; i < touchedCount; i++) {
                int id = order[i];
                summaries.add(new ItemSummary(id, count[id], containers[id], nested[id], nearest[id]));
            }
            Collections.sort(summaries);
            return summaries;
        }
    }

    /**
     * Totals matching containers' contents per item.
     *
     * <p>This backs the item-first view. Someone asking "where is my redstone"
     * wants one row saying they have 2,304 across four containers - not four
     * container rows to add up by hand.
     *
     * <p>Deliberately does not go through {@link #query}, which would rank every
     * container in the dimension by distance and build a {@link SearchResult}
     * for each - and then throw the ordering away, because a summary is sorted
     * by quantity. On a large world that sort was most of the cost of every
     * keystroke.
     */
    public List<ItemSummary> summarise(IndexQuery query) {
        if (cachedSummary != null
                && cachedSummaryRevision == revision
                && query.equals(cachedSummaryQuery)) {
            return cachedSummary;
        }
        // Unmodifiable, because it is handed to every caller that asks the
        // same question until the index moves: one of them mutating it would
        // change what the others are told.
        List<ItemSummary> summary = Collections.unmodifiableList(computeSummary(query));

        cachedSummaryQuery = query;
        cachedSummaryRevision = revision;
        cachedSummary = summary;
        return summary;
    }

    private List<ItemSummary> computeSummary(IndexQuery query) {
        if (maxItemId < 0) return List.of();

        Plan plan = new Plan(query);
        Totals totals = new Totals(maxItemId + 1);

        if (query.limit() > 0) {
            // A limit means "the nearest so many containers", which needs them
            // ranked - so this rare path goes the long way round on purpose,
            // rather than keeping a second definition of what a limit means.
            for (SearchResult result : query(query)) {
                totals.add(result.container(), result.distanceSq(), query, plan);
            }
        } else {
            walk(query, plan, (record, distSq) -> totals.add(record, distSq, query, plan));
        }

        return totals.build();
    }

    /** Diagnostic counts for {@code /chestindex stats}. */
    public Stats stats() {
        int known = 0;
        int unlooted = 0;
        Map<Origin, Integer> byOrigin = new HashMap<>();
        for (ContainerRecord record : byPos.values()) {
            if (record.contentsKnown()) known++;
            if (record.unlooted()) unlooted++;
            byOrigin.merge(record.origin(), 1, Integer::sum);
        }
        return new Stats(byPos.size(), known, unlooted, byChunk.size(), byItem.size(), Map.copyOf(byOrigin));
    }

    public record Stats(
            int containers,
            int withKnownContents,
            int unlooted,
            int chunks,
            int distinctItems,
            Map<Origin, Integer> byOrigin
    ) {}
}
