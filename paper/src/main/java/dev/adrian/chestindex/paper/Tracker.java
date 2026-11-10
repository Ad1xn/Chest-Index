package dev.adrian.chestindex.paper;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

final class Tracker {
    static final Set<String> VANILLA_TYPES = Set.of(
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:barrel",
            "minecraft:shulker_box",
            "minecraft:ender_chest",
            "minecraft:hopper",
            "minecraft:dropper",
            "minecraft:dispenser",
            "minecraft:furnace",
            "minecraft:blast_furnace",
            "minecraft:smoker",
            "minecraft:brewing_stand",
            "minecraft:crafter",
            "minecraft:chiseled_bookshelf",
            "minecraft:decorated_pot",
            "minecraft:campfire",
            "minecraft:jukebox",
            "minecraft:lectern");

    private final StringPalette palette = new StringPalette();
    private final Map<String, WorldIndex> byDimension = new HashMap<>();
    private final Set<String> knownTypes = Collections.synchronizedSet(new LinkedHashSet<>(VANILLA_TYPES));
    private final Map<String, AtomicLong> generations = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> originChecked = new HashMap<>();
    private final Map<String, Set<Long>> legacyGuesses = new HashMap<>();
    private final Path storageRoot;
    private final Logger log;

    Tracker(Path storageRoot, Logger log) {
        this.storageRoot = storageRoot;
        this.log = log;
    }

    StringPalette palette() {
        return palette;
    }

    void learnType(String typeId) {
        if (typeId != null) knownTypes.add(typeId);
    }

    Set<String> knownTypes() {
        synchronized (knownTypes) {
            return Set.copyOf(knownTypes);
        }
    }

    WorldIndex index(String dimensionId) {
        return byDimension.computeIfAbsent(dimensionId, id -> new WorldIndex(palette.intern(id)));
    }

    Set<String> dimensions() {
        return Set.copyOf(byDimension.keySet());
    }

    long generation(String dimensionId) {
        AtomicLong counter = generations.get(dimensionId);
        return counter == null ? 0L : counter.get();
    }

    private void bump(String dimensionId) {
        generations.computeIfAbsent(dimensionId, id -> new AtomicLong()).incrementAndGet();
    }

    void record(String dimensionId, ContainerRecord container) {
        ContainerRecord previous = index(dimensionId).get(container.pos());
        ContainerRecord merged = container.inheriting(previous);
        if (previous != null && previous.sameDataAs(merged)) {
            index(dimensionId).touch(merged);
            return;
        }
        index(dimensionId).put(merged);
        bump(dimensionId);
    }

    boolean originChecked(String dimensionId, long pos) {
        Set<Long> checked = originChecked.get(dimensionId);
        return checked != null && checked.contains(pos);
    }

    void markOriginChecked(String dimensionId, long pos) {
        originChecked.computeIfAbsent(dimensionId, id -> new HashSet<>()).add(pos);
    }

    void forgetOriginChecks() {
        originChecked.clear();
    }

    Map<String, Set<Long>> indexedChunks() {
        Map<String, Set<Long>> chunks = new HashMap<>();
        for (Map.Entry<String, WorldIndex> entry : byDimension.entrySet()) {
            Set<Long> keys = new LinkedHashSet<>();
            for (ContainerRecord record : entry.getValue().all()) keys.add(record.chunkKey());
            if (!keys.isEmpty()) chunks.put(entry.getKey(), keys);
        }
        return chunks;
    }

    ContainerRecord remove(String dimensionId, long pos) {
        ContainerRecord removed = index(dimensionId).remove(pos);
        Set<Long> checked = originChecked.get(dimensionId);
        if (checked != null) checked.remove(pos);
        if (removed != null) bump(dimensionId);
        return removed;
    }

    int reconcileChunk(String dimensionId, long chunkKey, Set<Long> actual) {
        int dropped = index(dimensionId).reconcileChunk(chunkKey, actual);
        if (dropped > 0) bump(dimensionId);
        return dropped;
    }

    List<SearchResult> search(String dimensionId, IndexQuery query) {
        WorldIndex index = byDimension.get(dimensionId);
        return index == null ? List.of() : index.query(query);
    }

    int totalContainers() {
        int total = 0;
        for (WorldIndex index : byDimension.values()) total += index.size();
        return total;
    }

    Path scanLogFile() {
        return storageRoot.resolve("scanned-regions.txt");
    }

    private Path fileFor(String dimensionId) {
        return storageRoot.resolve(dimensionId.replace(':', '.').replace('/', '.') + ".idx");
    }

    void load() {
        if (!Files.isDirectory(storageRoot)) return;

        boolean anyOlderThanCurrent = false;
        try (var files = Files.list(storageRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".idx")).toList()) {
                try {
                    IndexCodec.Snapshot snapshot = IndexCodec.read(file);
                    String dimensionId = snapshot.palette().value(snapshot.index().dimensionId());
                    if (dimensionId == null) {
                        log.warning("Skipping " + file + ": its dimension id is missing from the palette");
                        continue;
                    }
                    if (snapshot.version() < IndexCodec.OLDEST_KEPT_WITHOUT_RESCAN) anyOlderThanCurrent = true;
                    WorldIndex restored = reindex(snapshot, dimensionId);
                    byDimension.put(dimensionId, restored);
                    if (snapshot.version() < IndexCodec.FIRST_BUILT_EVIDENCE_VERSION) {
                        noteLegacyGuesses(dimensionId, restored, snapshot.version());
                    }
                } catch (IOException e) {
                    log.warning("Could not read index " + file + ": " + e);
                }
            }
        } catch (IOException e) {
            log.warning("Could not list index directory " + storageRoot + ": " + e);
        }

        if (anyOlderThanCurrent) forgetWhatWasScanned();
    }

    private void noteLegacyGuesses(String dimensionId, WorldIndex index, int version) {
        Set<Long> chunks = new LinkedHashSet<>();
        for (ContainerRecord record : List.copyOf(index.all())) {
            ContainerRecord settled = BuiltEvidence.settle(record, null);
            if (settled != record) index.put(settled);
            if (needsAnotherLook(settled, version)) chunks.add(record.chunkKey());
        }
        if (!chunks.isEmpty()) legacyGuesses.put(dimensionId, chunks);
    }

    private boolean needsAnotherLook(ContainerRecord record, int version) {
        if (version < IndexCodec.FIRST_BINARY_ORIGIN_VERSION && record.isPlacementGuess()) return true;
        if (record.origin() == Origin.NATURAL && !record.unlooted()) return true;
        return ShulkerMachines.isShulkerBox(palette.value(record.typeId()));
    }

    Map<String, Set<Long>> takeLegacyGuesses() {
        Map<String, Set<Long>> taken = Map.copyOf(legacyGuesses);
        legacyGuesses.clear();
        return taken;
    }

    void forgetWhatWasScanned() {
        try {
            Files.deleteIfExists(scanLogFile());
        } catch (IOException e) {
            log.warning("Could not clear the scan log: " + e);
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

    ContainerRecord remap(ContainerRecord record, StringPalette from, String dimensionId) {
        String typeId = from.value(record.typeId());
        if (typeId == null) return null;

        List<StackEntry> contents = new ArrayList<>(record.contents().size());
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
        List<Integer> remapped = new ArrayList<>(details.size());
        for (int detailId : details) {
            String detail = from.value(detailId);
            if (detail != null) remapped.add(palette.intern(detail));
        }
        return remapped;
    }

    void clearIndexes() {
        byDimension.clear();
        originChecked.clear();
        generations.values().forEach(AtomicLong::incrementAndGet);
    }

    void save() {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            log.severe("Could not create " + storageRoot + ": " + e);
            return;
        }
        for (Map.Entry<String, WorldIndex> entry : byDimension.entrySet()) {
            try {
                IndexCodec.write(fileFor(entry.getKey()), palette, entry.getValue());
            } catch (IOException e) {
                log.severe("Could not save index for " + entry.getKey() + ": " + e);
            }
        }
    }
}
