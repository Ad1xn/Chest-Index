package dev.adrian.chestindex.paper;

import dev.adrian.chestindex.core.anvil.AnvilWorldView;
import dev.adrian.chestindex.core.anvil.ChunkExtractor;
import dev.adrian.chestindex.core.anvil.NbtCompound;
import dev.adrian.chestindex.core.anvil.OriginClassifier;
import dev.adrian.chestindex.core.anvil.OriginResolver;
import dev.adrian.chestindex.core.anvil.RegionFile;
import dev.adrian.chestindex.core.anvil.WorldLayout;
import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.store.ScanLog;
import dev.adrian.chestindex.core.store.StringPalette;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.BuiltEvidence;
import dev.adrian.chestindex.core.util.MonsterRooms;
import dev.adrian.chestindex.core.util.ShulkerMachines;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class RegionScan {
    private static final int APPLY_BUDGET_PER_TICK = 256;
    private static final int QUEUE_LIMIT = 4096;
    private static final long APPLY_SLICE_NANOS = 4_000_000L;
    private static final int LOG_EVERY = 25;

    record Dimension(String dimensionId, Path regionDir) {}

    record Progress(boolean running, int regionsRead, int regionsTotal, int chunksRead,
                    int containersFound, int chunksFailed) {}

    private record Batch(String dimensionId, long chunkKey, List<ContainerRecord> containers, StringPalette palette) {}

    private final Tracker tracker;
    private final Logger log;
    private final Queue<Batch> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueDepth = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicInteger chunksRead = new AtomicInteger();
    private final AtomicInteger regionsRead = new AtomicInteger();
    private final AtomicInteger regionsTotal = new AtomicInteger();
    private final AtomicInteger containersFound = new AtomicInteger();
    private final AtomicInteger chunksFailed = new AtomicInteger();
    private volatile Thread worker;

    RegionScan(Tracker tracker, Logger log) {
        this.tracker = tracker;
        this.log = log;
    }

    boolean isRunning() {
        return running.get();
    }

    Progress progress() {
        return new Progress(running.get(), regionsRead.get(), regionsTotal.get(), chunksRead.get(),
                containersFound.get(), chunksFailed.get());
    }

    void cancel() {
        cancelled.set(true);
        Thread current = worker;
        if (current != null) current.interrupt();
    }

    static Path regionDirOf(Path worldFolder, String dimensionId) {
        try {
            List<WorldLayout.DimensionRegions> found = WorldLayout.discover(worldFolder);
            for (WorldLayout.DimensionRegions dimension : found) {
                if (dimension.dimensionId().equals(dimensionId)) return dimension.regionDir();
            }
            return found.size() == 1 ? found.get(0).regionDir() : null;
        } catch (IOException e) {
            return null;
        }
    }

    boolean start(List<Dimension> dimensions, long tick, Map<String, Set<Long>> refine, boolean scan,
                  Consumer<String> finished) {
        if (!running.compareAndSet(false, true)) return false;

        cancelled.set(false);
        chunksRead.set(0);
        regionsRead.set(0);
        regionsTotal.set(0);
        containersFound.set(0);
        chunksFailed.set(0);

        Thread thread = new Thread(() -> {
            try {
                if (!refine.isEmpty()) refineChunks(dimensions, refine, tick);
                if (scan && !cancelled.get()) {
                    scanWorld(dimensions, tick);
                    finished.accept(summary("scan"));
                } else if (!refine.isEmpty()) {
                    finished.accept(summary("re-check"));
                }
            } catch (Throwable t) {
                log.log(Level.SEVERE, "Region scan failed", t);
            } finally {
                running.set(false);
                worker = null;
            }
        }, "ChestIndex-RegionScan");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        worker = thread;
        thread.start();
        return true;
    }

    private String summary(String what) {
        String line = what + (cancelled.get() ? " cancelled" : " finished") + "  ·  "
                + String.format("%,d", containersFound.get()) + " containers in "
                + String.format("%,d", chunksRead.get()) + " chunks";
        int failed = chunksFailed.get();
        return failed > 0 ? line + "  ·  " + String.format("%,d unreadable", failed) : line;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    private static long modifiedAt(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return -1;
        }
    }

    private void writeLog(ScanLog scanLog, Path file) {
        try {
            Files.createDirectories(file.getParent());
            scanLog.write(file);
        } catch (IOException e) {
            log.warning("Could not write the scan log " + file + ": " + e);
        }
    }

    private void scanWorld(List<Dimension> dimensions, long tick) throws IOException {
        List<Path> allRegions = new ArrayList<>();
        List<String> owners = new ArrayList<>();
        for (Dimension dimension : dimensions) {
            for (Path file : WorldLayout.regionFiles(dimension.regionDir())) {
                allRegions.add(file);
                owners.add(dimension.dimensionId());
            }
        }
        regionsTotal.set(allRegions.size());

        Path logFile = tracker.scanLogFile();
        ScanLog scanLog = ScanLog.read(logFile);

        List<Path> todo = new ArrayList<>();
        List<String> todoOwners = new ArrayList<>();
        for (int i = 0; i < allRegions.size(); i++) {
            Path file = allRegions.get(i);
            if (scanLog.isCurrent(file.toString(), sizeOf(file), modifiedAt(file))) {
                regionsRead.incrementAndGet();
                continue;
            }
            todo.add(file);
            todoOwners.add(owners.get(i));
        }

        log.info("Region scan starting: " + todo.size() + " of " + allRegions.size()
                + " region files need reading across " + dimensions.size() + " dimension(s)");

        Set<String> trackedIds = tracker.knownTypes();
        AtomicInteger next = new AtomicInteger();
        AtomicInteger sinceWrite = new AtomicInteger();
        Runnable work = () -> {
            int i;
            while (!cancelled.get() && (i = next.getAndIncrement()) < todo.size()) {
                Path file = todo.get(i);
                long size = sizeOf(file);
                long modified = modifiedAt(file);
                scanRegion(file, todoOwners.get(i), trackedIds, tick);
                if (cancelled.get()) return;
                regionsRead.incrementAndGet();
                synchronized (scanLog) {
                    scanLog.mark(file.toString(), size, modified);
                    if (sinceWrite.incrementAndGet() >= LOG_EVERY) {
                        sinceWrite.set(0);
                        writeLog(scanLog, logFile);
                    }
                }
            }
        };

        List<Thread> helpers = new ArrayList<>();
        for (int h = 1; h < workerCount(todo.size()); h++) {
            Thread helper = new Thread(work, "ChestIndex-RegionScan-" + h);
            helper.setDaemon(true);
            helper.setPriority(Thread.MIN_PRIORITY);
            helper.start();
            helpers.add(helper);
        }
        work.run();
        for (Thread helper : helpers) {
            try {
                helper.join();
            } catch (InterruptedException interrupted) {
                cancelled.set(true);
                helpers.forEach(Thread::interrupt);
            }
        }

        synchronized (scanLog) {
            writeLog(scanLog, logFile);
        }

        log.info("Region scan " + (cancelled.get() ? "cancelled" : "complete") + ": " + chunksRead.get()
                + " chunks, " + containersFound.get() + " containers, " + chunksFailed.get() + " failed, "
                + (allRegions.size() - todo.size()) + " regions skipped as unchanged");
    }

    private static int workerCount(int regions) {
        int spare = Runtime.getRuntime().availableProcessors() / 2;
        return Math.max(1, Math.min(Math.min(4, spare), regions));
    }

    private void scanRegion(Path file, String dimensionId, Set<String> trackedIds, long tick) {
        int[] region = RegionFile.parseCoords(file.getFileName().toString());
        if (region == null) return;

        StringPalette localPalette = new StringPalette();
        int localDimensionId = localPalette.intern(dimensionId);
        Map<Long, List<ContainerRecord>> byChunk = new LinkedHashMap<>();
        List<ChunkExtractor.StructureBox> regionBoxes = new ArrayList<>();
        List<Long> regionSpawners = new ArrayList<>();

        try (RegionFile regionFile = RegionFile.open(file)) {
            for (int[] local : regionFile.presentChunks()) {
                if (cancelled.get()) return;
                throttle();
                if (cancelled.get()) return;

                int chunkX = region[0] * 32 + local[0];
                int chunkZ = region[1] * 32 + local[1];
                try {
                    NbtCompound chunk = regionFile.readChunk(local[0], local[1], ChunkExtractor.CHUNK_KEYS);
                    if (chunk == null) continue;
                    ChunkExtractor.ChunkContents contents = ChunkExtractor.extract(
                            chunk, trackedIds, localDimensionId, localPalette, tick);
                    regionBoxes.addAll(contents.structureBoxes());
                    regionSpawners.addAll(contents.spawners());
                    byChunk.put(BlockKey.chunkKey(chunkX, chunkZ), contents.containers());
                    chunksRead.incrementAndGet();
                } catch (IOException torn) {
                    chunksFailed.incrementAndGet();
                }
            }
        } catch (IOException e) {
            log.warning("Skipping region " + file.getFileName() + ": " + e);
            return;
        }

        int chestTypeId = localPalette.lookup(MonsterRooms.CHEST_ID);
        try (AnvilWorldView world = new AnvilWorldView(file.getParent())) {
            for (Map.Entry<Long, List<ContainerRecord>> entry : byChunk.entrySet()) {
                List<ContainerRecord> classified = OriginClassifier.classify(entry.getValue(), regionBoxes,
                        regionSpawners, chestTypeId);
                classified = resolveGuesses(classified, chestTypeId, world);
                classified = settle(classified, localPalette, world);
                containersFound.addAndGet(classified.size());
                pending.add(new Batch(dimensionId, entry.getKey(), classified, localPalette));
                queueDepth.incrementAndGet();
            }
        }
    }

    private static List<ContainerRecord> resolveGuesses(List<ContainerRecord> containers, int chestTypeId,
                                                        AnvilWorldView world) {
        List<ContainerRecord> resolved = null;
        for (int i = 0; i < containers.size(); i++) {
            ContainerRecord container = containers.get(i);
            if (!container.isPlacementGuess()) continue;
            ContainerRecord decided = OriginResolver.resolve(container, chestTypeId, world);
            if (decided == container) continue;
            if (resolved == null) resolved = new ArrayList<>(containers);
            resolved.set(i, decided);
        }
        return resolved == null ? containers : resolved;
    }

    private static List<ContainerRecord> settle(List<ContainerRecord> containers, StringPalette palette,
                                                AnvilWorldView world) {
        List<ContainerRecord> settled = null;
        for (int i = 0; i < containers.size(); i++) {
            ContainerRecord container = containers.get(i);
            ContainerRecord decided = BuiltEvidence.settle(container,
                    container.origin() == Origin.NATURAL ? world : null);
            if (ShulkerMachines.isShulkerBox(palette.value(container.typeId()))) {
                decided = decided.inMachine(ShulkerMachines.inMachine(container.pos(), world));
            }
            if (decided == container) continue;
            if (settled == null) settled = new ArrayList<>(containers);
            settled.set(i, decided);
        }
        return settled == null ? containers : settled;
    }

    private void refineChunks(List<Dimension> dimensions, Map<String, Set<Long>> chunksByDimension, long tick) {
        Set<String> trackedIds = tracker.knownTypes();
        for (Map.Entry<String, Set<Long>> wanted : chunksByDimension.entrySet()) {
            Path regionDir = null;
            for (Dimension dimension : dimensions) {
                if (dimension.dimensionId().equals(wanted.getKey())) regionDir = dimension.regionDir();
            }
            if (regionDir == null) continue;

            StringPalette localPalette = new StringPalette();
            int localDimensionId = localPalette.intern(wanted.getKey());
            Map<Long, RegionFile> open = new LinkedHashMap<>();
            try (AnvilWorldView world = new AnvilWorldView(regionDir)) {
                for (long chunkKey : wanted.getValue()) {
                    if (cancelled.get()) return;
                    throttle();

                    int chunkX = BlockKey.chunkX(chunkKey);
                    int chunkZ = BlockKey.chunkZ(chunkKey);
                    Path dir = regionDir;
                    RegionFile region = open.computeIfAbsent(BlockKey.chunkKey(chunkX >> 5, chunkZ >> 5),
                            key -> openRegion(dir, chunkX >> 5, chunkZ >> 5));
                    if (region == null) continue;

                    NbtCompound chunk;
                    try {
                        chunk = region.readChunk(chunkX & 31, chunkZ & 31, ChunkExtractor.CHUNK_KEYS);
                    } catch (IOException torn) {
                        chunksFailed.incrementAndGet();
                        continue;
                    }
                    if (chunk == null) continue;

                    ChunkExtractor.ChunkContents contents = ChunkExtractor.extract(
                            chunk, trackedIds, localDimensionId, localPalette, tick);
                    int chestTypeId = localPalette.lookup(MonsterRooms.CHEST_ID);
                    List<ContainerRecord> classified = OriginClassifier.classify(contents.containers(),
                            contents.structureBoxes(), contents.spawners(), chestTypeId);
                    classified = resolveGuesses(classified, chestTypeId, world);
                    classified = settle(classified, localPalette, world);

                    chunksRead.incrementAndGet();
                    containersFound.addAndGet(classified.size());
                    pending.add(new Batch(wanted.getKey(), chunkKey, classified, localPalette));
                    queueDepth.incrementAndGet();
                }
            } finally {
                for (RegionFile region : open.values()) {
                    if (region == null) continue;
                    try {
                        region.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private RegionFile openRegion(Path regionDir, int regionX, int regionZ) {
        Path file = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
        if (!Files.isRegularFile(file)) return null;
        try {
            return RegionFile.open(file);
        } catch (IOException unreadable) {
            log.warning("Skipping region " + file.getFileName() + ": " + unreadable);
            return null;
        }
    }

    private void throttle() {
        while (queueDepth.get() > QUEUE_LIMIT && !cancelled.get()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                cancelled.set(true);
                return;
            }
        }
    }

    int drain(BiPredicate<String, Long> isChunkLoaded, BiConsumer<String, Long> liveScanChunk,
              boolean indexUnlootedGenerated) {
        int applied = 0;
        long deadline = System.nanoTime() + APPLY_SLICE_NANOS;
        Batch batch;
        while (applied < APPLY_BUDGET_PER_TICK && (batch = pending.poll()) != null) {
            queueDepth.decrementAndGet();
            boolean loaded = isChunkLoaded.test(batch.dimensionId(), batch.chunkKey());
            if (batch.containers().isEmpty()
                    && tracker.index(batch.dimensionId()).positionsInChunk(batch.chunkKey()).isEmpty()
                    && !loaded) {
                if (System.nanoTime() >= deadline) break;
                continue;
            }
            applied++;

            if (loaded) {
                liveScanChunk.accept(batch.dimensionId(), batch.chunkKey());
                continue;
            }

            Set<Long> positions = new HashSet<>();
            for (ContainerRecord record : batch.containers()) {
                ContainerRecord remapped = tracker.remap(record, batch.palette(), batch.dimensionId());
                if (remapped == null) continue;
                if (remapped.origin() == Origin.NATURAL && remapped.unlooted() && !indexUnlootedGenerated) continue;
                tracker.record(batch.dimensionId(), remapped);
                positions.add(remapped.pos());
            }
            tracker.reconcileChunk(batch.dimensionId(), batch.chunkKey(), positions);

            if (System.nanoTime() >= deadline) break;
        }
        return applied;
    }
}
