package dev.adrian.chestindex.server;

import dev.adrian.chestindex.ChestIndex;
import dev.adrian.chestindex.core.index.IndexQuery;
import dev.adrian.chestindex.core.index.SearchResult;
import dev.adrian.chestindex.core.index.WorldIndex;
import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.store.IndexCodec;
import dev.adrian.chestindex.core.store.StringPalette;
import dev.adrian.chestindex.core.util.BuiltEvidence;
import dev.adrian.chestindex.core.util.ShulkerMachines;
import dev.adrian.chestindex.platform.ContainerTypes;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TrackerService {
    private final StringPalette palette = new StringPalette();
    private final Map<String, WorldIndex> byDimension = new HashMap<>();
    private final ContainerTypes containerTypes = new ContainerTypes();
    private final Path storageRoot;

    private final Map<String, AtomicLong> generations = new ConcurrentHashMap<>();

    private final Map<String, LongOpenHashSet> originChecked = new HashMap<>();

    private final Map<String, Set<Long>> legacyGuesses = new HashMap<>();

    public TrackerService(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    public StringPalette palette() {
        return palette;
    }

    public ContainerTypes containerTypes() {
        return containerTypes;
    }

    public WorldIndex index(String dimensionId) {
        return byDimension.computeIfAbsent(dimensionId,
                id -> new WorldIndex(palette.intern(id)));
    }

    public Set<String> dimensions() {
        return Set.copyOf(byDimension.keySet());
    }

    public long generation(String dimensionId) {
        AtomicLong counter = generations.get(dimensionId);
        return counter == null ? 0L : counter.get();
    }

    private void bump(String dimensionId) {
        generations.computeIfAbsent(dimensionId, id -> new AtomicLong()).incrementAndGet();
    }

    public void record(String dimensionId, ContainerRecord container) {
        ContainerRecord previous = index(dimensionId).get(container.pos());
        ContainerRecord merged = container.inheriting(previous);

        if (previous != null && previous.sameDataAs(merged)) {
            index(dimensionId).touch(merged);
            return;
        }

        index(dimensionId).put(merged);
        bump(dimensionId);
    }

    public boolean originChecked(String dimensionId, long pos) {
        LongOpenHashSet checked = originChecked.get(dimensionId);
        return checked != null && checked.contains(pos);
    }

    public void forgetOriginChecks() {
        originChecked.clear();
    }

    public Map<String, Set<Long>> indexedChunks() {
        Map<String, Set<Long>> chunks = new HashMap<>();
        for (Map.Entry<String, WorldIndex> entry : byDimension.entrySet()) {
            Set<Long> keys = new java.util.LinkedHashSet<>();
            for (ContainerRecord record : entry.getValue().all()) keys.add(record.chunkKey());
            if (!keys.isEmpty()) chunks.put(entry.getKey(), keys);
        }
        return chunks;
    }

    public void markOriginChecked(String dimensionId, long pos) {
        originChecked.computeIfAbsent(dimensionId, id -> new LongOpenHashSet()).add(pos);
    }

    public ContainerRecord remove(String dimensionId, long pos) {
        ContainerRecord removed = index(dimensionId).remove(pos);
        LongOpenHashSet checked = originChecked.get(dimensionId);
        if (checked != null) checked.remove(pos);
        if (removed != null) bump(dimensionId);
        return removed;
    }

    public int reconcileChunk(String dimensionId, long chunkKey, Set<Long> actual) {
        int dropped = index(dimensionId).reconcileChunk(chunkKey, actual);
        if (dropped > 0) bump(dimensionId);
        return dropped;
    }

    public List<SearchResult> search(String dimensionId, IndexQuery query) {
        WorldIndex index = byDimension.get(dimensionId);
        return index == null ? List.of() : index.query(query);
    }

    public int totalContainers() {
        int total = 0;
        for (WorldIndex index : byDimension.values()) total += index.size();
        return total;
    }

    private Path fileFor(String dimensionId) {
        String safe = dimensionId.replace(':', '.').replace('/', '.');
        return storageRoot.resolve(safe + ".idx");
    }

    public void load() {
        if (!Files.isDirectory(storageRoot)) return;

        boolean anyOlderThanCurrent = false;
        try (var files = Files.list(storageRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".idx")).toList()) {
                try {
                    IndexCodec.Snapshot snapshot = IndexCodec.read(file);
                    String dimensionId = snapshot.palette().value(snapshot.index().dimensionId());
                    if (dimensionId == null) {
                        ChestIndex.LOG.warn("Skipping {}: its dimension id is missing from the palette", file);
                        continue;
                    }
                    if (snapshot.version() < IndexCodec.OLDEST_KEPT_WITHOUT_RESCAN) anyOlderThanCurrent = true;
                    WorldIndex restored = reindex(snapshot, dimensionId);
                    byDimension.put(dimensionId, restored);
                    if (snapshot.version() < IndexCodec.FIRST_BUILT_EVIDENCE_VERSION) {
                        noteLegacyGuesses(dimensionId, restored, snapshot.version());
                    }
                } catch (IOException e) {
                    ChestIndex.LOG.warn("Could not read index {}: {}", file, e.toString());
                }
            }
        } catch (IOException e) {
            ChestIndex.LOG.warn("Could not list index directory {}: {}", storageRoot, e.toString());
        }

        if (anyOlderThanCurrent) forgetWhatWasScanned();
    }

    private void noteLegacyGuesses(String dimensionId, WorldIndex index, int version) {
        Set<Long> chunks = new java.util.LinkedHashSet<>();
        for (ContainerRecord record : List.copyOf(index.all())) {
            ContainerRecord settled = BuiltEvidence.settle(record, null);
            if (settled != record) index.put(settled);
            if (needsAnotherLook(settled, version)) chunks.add(record.chunkKey());
        }
        if (chunks.isEmpty()) return;
        legacyGuesses.put(dimensionId, chunks);
        ChestIndex.LOG.info("{}: {} chunk(s) hold containers to look at again after an upgrade",
                dimensionId, chunks.size());
    }

    private boolean needsAnotherLook(ContainerRecord record, int version) {
        if (version < IndexCodec.FIRST_BINARY_ORIGIN_VERSION && record.isPlacementGuess()) return true;
        if (record.origin() == Origin.NATURAL && !record.unlooted()) return true;
        return ShulkerMachines.isShulkerBox(palette.value(record.typeId()));
    }

    public Map<String, Set<Long>> takeLegacyGuesses() {
        Map<String, Set<Long>> taken = Map.copyOf(legacyGuesses);
        legacyGuesses.clear();
        return taken;
    }

    private void forgetWhatWasScanned() {
        Path log = scanLogFile();
        try {
            if (!Files.deleteIfExists(log)) return;
            ChestIndex.LOG.info(
                    "Index was written by an older build: reading the world again to correct it.");
        } catch (IOException e) {
            ChestIndex.LOG.warn("Could not clear the scan log {}: {}", log, e.toString());
        }
    }

    private WorldIndex reindex(IndexCodec.Snapshot snapshot, String dimensionId) {
        WorldIndex target = new WorldIndex(palette.intern(dimensionId));
        for (ContainerRecord record : snapshot.index().all()) {
            ContainerRecord remapped = remap(record, snapshot.palette(), dimensionId);
            if (remapped != null) target.put(remapped);
        }
        return target;
    }

    public ContainerRecord remap(ContainerRecord record, StringPalette from, String dimensionId) {
        String typeId = from.value(record.typeId());
        if (typeId == null) return null;

        List<StackEntry> contents = new java.util.ArrayList<>(record.contents().size());
        for (StackEntry entry : record.contents()) {
            String itemId = from.value(entry.itemId());
            if (itemId == null) continue;
            contents.add(new StackEntry(palette.intern(itemId), entry.count(), entry.depth(),
                    entry.customName(), remapDetails(entry.details(), from)));
        }

        return new ContainerRecord(record.pos(), palette.intern(dimensionId), palette.intern(typeId),
                record.origin(), record.owner(), record.unlooted(), record.contentsKnown(),
                record.customName(), record.lastSeenTick(), contents, record.originInferred(), record.inMachine());
    }

    private List<Integer> remapDetails(List<Integer> details, StringPalette from) {
        if (details.isEmpty()) return List.of();
        List<Integer> remapped = new java.util.ArrayList<>(details.size());
        for (int detailId : details) {
            String detail = from.value(detailId);
            if (detail != null) remapped.add(palette.intern(detail));
        }
        return remapped;
    }

    public void clearIndexes() {
        byDimension.clear();
        originChecked.clear();
        generations.values().forEach(java.util.concurrent.atomic.AtomicLong::incrementAndGet);
    }

    public Path storageRoot() {
        return storageRoot;
    }

    public Path scanLogFile() {
        return storageRoot.resolve("scanned-regions.txt");
    }

    public void save() {
        for (Map.Entry<String, WorldIndex> entry : byDimension.entrySet()) {
            try {
                IndexCodec.write(fileFor(entry.getKey()), palette, entry.getValue());
            } catch (IOException e) {
                ChestIndex.LOG.error("Could not save index for {}: {}", entry.getKey(), e.toString());
            }
        }
    }
}
