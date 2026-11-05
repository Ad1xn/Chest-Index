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

public final class WorldIndex {
    private final Map<Long, ContainerRecord> byPos = new HashMap<>();
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();
    private final Map<Integer, Set<Long>> byItem = new HashMap<>();

    private final int dimensionId;

    private long revision;

    private IndexQuery cachedSummaryQuery;
    private long cachedSummaryRevision = -1;
    private List<ItemSummary> cachedSummary;

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

    public void touch(ContainerRecord record) {
        ContainerRecord existing = byPos.get(record.pos());
        if (existing == null || !existing.sameDataAs(record)) {
            put(record);
            return;
        }
        byPos.put(record.pos(), record);
    }

    public ContainerRecord remove(long pos) {
        revision++;
        return unindex(pos);
    }

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

    public Set<Long> positionsInChunk(long chunkKey) {
        Set<Long> positions = byChunk.get(chunkKey);
        return positions == null ? Set.of() : Collections.unmodifiableSet(positions);
    }

    public void removeChunk(long chunkKey) {
        for (Long pos : new ArrayList<>(positionsInChunk(chunkKey))) {
            remove(pos);
        }
    }

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

    private static final class Plan {
        final long[] items;
        final int[] itemIds;
        final long[] types;
        final long[] excludedTypes;
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

    private interface Visitor {
        void accept(ContainerRecord record, double distanceSq);
    }

    public List<SearchResult> query(IndexQuery query) {
        Plan plan = new Plan(query);
        return query.limit() > 0 ? nearest(query, plan) : all(query, plan);
    }

    private List<SearchResult> nearest(IndexQuery query, Plan plan) {
        int limit = query.limit();
        PriorityQueue<SearchResult> kept = new PriorityQueue<>(
                Math.min(limit, 64), (a, b) -> Double.compare(b.distanceSq(), a.distanceSq()));

        walk(query, plan, (record, distSq) -> {
            if (kept.size() == limit && distSq >= kept.peek().distanceSq()) return;
            if (!hasMatch(record, query, plan)) return;

            kept.add(new SearchResult(record, distSq, collectMatches(record, query, plan)));
            if (kept.size() > limit) kept.poll();
        });

        SearchResult[] results = kept.toArray(new SearchResult[0]);
        java.util.Arrays.sort(results, (a, b) -> Double.compare(a.distanceSq(), b.distanceSq()));
        return List.of(results);
    }

    private List<SearchResult> all(IndexQuery query, Plan plan) {
        List<SearchResult> results = new ArrayList<>();

        walk(query, plan, (record, distSq) -> {
            if (!hasMatch(record, query, plan)) return;
            results.add(new SearchResult(record, distSq, collectMatches(record, query, plan)));
        });

        results.sort((a, b) -> Double.compare(a.distanceSq(), b.distanceSq()));
        return Collections.unmodifiableList(results);
    }

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

    private void walk(IndexQuery query, Plan plan, Visitor visitor) {
        if (plan.items != null) {
            long estimate = 0;
            for (int itemId : plan.itemIds) {
                Set<Long> holders = byItem.get(itemId);
                if (holders != null) estimate += holders.size();
            }
            if (estimate == 0) return;
            if (estimate < byPos.size()) {
                walkHolders(query, plan, visitor);
                return;
            }
        } else if (query.hasDistanceLimit()) {
            if (walkChunkRadius(query, plan, visitor)) return;
        }

        for (ContainerRecord record : byPos.values()) {
            offer(record, query, plan, visitor);
        }
    }

    private void walkHolders(IndexQuery query, Plan plan, Visitor visitor) {
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
                if (!seen.add(pos)) continue;
                ContainerRecord record = byPos.get(pos);
                if (record != null) offer(record, query, plan, visitor);
            }
        }
    }

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
        if (query.hideMachineParts() && record.inMachine()) return false;
        if (query.knownContentsOnly() && !record.contentsKnown()) return false;
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

    private static boolean carriesWantedDetail(StackEntry entry, Plan plan) {
        if (plan.details == null) return true;
        if (entry.details().isEmpty()) return false;
        for (int detail : entry.details()) {
            if (Bits.test(plan.details, detail)) return true;
        }
        return false;
    }

    public record ItemSummary(int itemId, int totalCount, int containerCount,
                              int nestedCount, double nearestDistSq)
            implements Comparable<ItemSummary> {
        @Override
        public int compareTo(ItemSummary other) {
            int byCount = Integer.compare(other.totalCount, totalCount);
            return byCount != 0 ? byCount : Double.compare(nearestDistSq, other.nearestDistSq);
        }
    }

    private static final class Totals {
        private final int[] count;
        private final int[] containers;
        private final int[] nested;
        private final double[] nearest;

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
            if (!record.contentsKnown()) return;

            containerSeq++;
            for (StackEntry entry : record.contents()) {
                if (!query.includeNested() && entry.isNested()) continue;

                int id = entry.itemId();
                if (id < 0 || id >= count.length) continue;
                if (plan.items != null && !Bits.test(plan.items, id)) continue;
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
                if (lastContainer[id] != containerSeq) {
                    lastContainer[id] = containerSeq;
                    containers[id]++;
                }
            }
        }

        List<ItemSummary> build() {
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

    public List<ItemSummary> summarise(IndexQuery query) {
        if (cachedSummary != null
                && cachedSummaryRevision == revision
                && query.equals(cachedSummaryQuery)) {
            return cachedSummary;
        }
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
            for (SearchResult result : query(query)) {
                totals.add(result.container(), result.distanceSq(), query, plan);
            }
        } else {
            walk(query, plan, (record, distSq) -> totals.add(record, distSq, query, plan));
        }

        return totals.build();
    }

    public Stats stats() {
        int known = 0;
        int unlooted = 0;
        int placementGuesses = 0;
        Map<Origin, Integer> byOrigin = new HashMap<>();
        for (ContainerRecord record : byPos.values()) {
            if (record.contentsKnown()) known++;
            if (record.unlooted()) unlooted++;
            if (record.isPlacementGuess()) placementGuesses++;
            byOrigin.merge(record.origin(), 1, Integer::sum);
        }
        return new Stats(byPos.size(), known, unlooted, byChunk.size(), byItem.size(), Map.copyOf(byOrigin),
                placementGuesses);
    }

    public record Stats(
            int containers,
            int withKnownContents,
            int unlooted,
            int chunks,
            int distinctItems,
            Map<Origin, Integer> byOrigin,
            int placementGuesses
    ) {}
}
