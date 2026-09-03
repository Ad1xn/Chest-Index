package dev.adrian.chesttracker.core.index;

import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackEntry;
import dev.adrian.chesttracker.core.util.BlockKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorldIndex {

    private final Map<Long, ContainerRecord> byPos = new HashMap<>();
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();
    private final Map<Integer, Set<Long>> byItem = new HashMap<>();

    private final int dimensionId;

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
        remove(record.pos());

        byPos.put(record.pos(), record);
        byChunk.computeIfAbsent(record.chunkKey(), k -> new HashSet<>()).add(record.pos());
        for (StackEntry entry : record.contents()) {
            byItem.computeIfAbsent(entry.itemId(), k -> new HashSet<>()).add(record.pos());
        }
    }

    public ContainerRecord remove(long pos) {
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
        byPos.clear();
        byChunk.clear();
        byItem.clear();
    }

    public List<SearchResult> query(IndexQuery query) {
        Collection<Long> candidates = selectCandidates(query);

        List<SearchResult> results = new ArrayList<>();
        double maxDistSq = query.maxDistanceSq();

        for (Long pos : candidates) {
            ContainerRecord record = byPos.get(pos);
            if (record == null) continue;
            if (!matchesFilters(record, query)) continue;

            double distSq = BlockKey.distanceSq(query.center(), record.pos());
            if (query.hasDistanceLimit() && distSq > maxDistSq) continue;

            List<StackEntry> matches = collectMatches(record, query);
            if (!query.itemIds().isEmpty() && matches.isEmpty()) continue;

            results.add(new SearchResult(record, distSq, matches));
        }

        results.sort((a, b) -> Double.compare(a.distanceSq(), b.distanceSq()));
        if (query.limit() > 0 && results.size() > query.limit()) {
            return List.copyOf(results.subList(0, query.limit()));
        }
        return List.copyOf(results);
    }

    private Collection<Long> selectCandidates(IndexQuery query) {
        if (!query.itemIds().isEmpty()) {
            Set<Long> union = new HashSet<>();
            for (Integer itemId : query.itemIds()) {
                Set<Long> holders = byItem.get(itemId);
                if (holders != null) union.addAll(holders);
            }
            return union;
        }

        if (query.hasDistanceLimit()) {
            return positionsWithinChunkRadius(query);
        }

        return new ArrayList<>(byPos.keySet());
    }

    private Collection<Long> positionsWithinChunkRadius(IndexQuery query) {
        int centerChunkX = BlockKey.x(query.center()) >> 4;
        int centerChunkZ = BlockKey.z(query.center()) >> 4;
        int chunkRadius = (int) Math.ceil(query.maxDistance() / 16.0) + 1;

        long chunkSquare = (2L * chunkRadius + 1) * (2L * chunkRadius + 1);
        if (chunkSquare > byChunk.size()) {
            return new ArrayList<>(byPos.keySet());
        }

        List<Long> candidates = new ArrayList<>();
        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                Set<Long> positions = byChunk.get(BlockKey.chunkKey(cx, cz));
                if (positions != null) candidates.addAll(positions);
            }
        }
        return candidates;
    }

    private boolean matchesFilters(ContainerRecord record, IndexQuery query) {
        if (!query.origins().isEmpty() && !query.origins().contains(record.origin())) return false;
        if (!query.typeIds().isEmpty() && !query.typeIds().contains(record.typeId())) return false;
        if (query.unlootedOnly() && !record.unlooted()) return false;
        if (query.knownContentsOnly() && !record.contentsKnown()) return false;
        return true;
    }

    private List<StackEntry> collectMatches(ContainerRecord record, IndexQuery query) {
        if (query.itemIds().isEmpty()) return List.of();

        List<StackEntry> matches = new ArrayList<>();
        for (StackEntry entry : record.contents()) {
            if (!query.includeNested() && entry.isNested()) continue;
            if (query.itemIds().contains(entry.itemId())) matches.add(entry);
        }
        return matches;
    }

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
