package dev.adrian.chesttracker.server.scan;

import dev.adrian.chesttracker.ChestTracker;
import dev.adrian.chesttracker.core.store.ScanLog;
import dev.adrian.chesttracker.core.anvil.ChunkExtractor;
import dev.adrian.chesttracker.core.anvil.NbtCompound;
import dev.adrian.chesttracker.core.anvil.OriginClassifier;
import dev.adrian.chesttracker.core.anvil.RegionFile;
import dev.adrian.chesttracker.core.anvil.WorldLayout;
import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.store.StringPalette;
import dev.adrian.chesttracker.core.util.BlockKey;
import dev.adrian.chesttracker.server.TrackerService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class RegionScanner {

    private static final int APPLY_BUDGET_PER_TICK = 64;

    private static final long TICK_TIME_BUDGET_NANOS = 45_000_000L;

    private final TrackerService tracker;
    private final Queue<Batch> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    private final AtomicInteger chunksRead = new AtomicInteger();
    private final AtomicInteger regionsRead = new AtomicInteger();
    private final AtomicInteger regionsTotal = new AtomicInteger();
    private final AtomicInteger containersFound = new AtomicInteger();
    private final AtomicInteger chunksSkippedLoaded = new AtomicInteger();
    private final AtomicInteger chunksFailed = new AtomicInteger();

    private volatile Thread worker;

    public RegionScanner(TrackerService tracker) {
        this.tracker = tracker;
    }

    private record Batch(String dimensionId, long chunkKey, List<ContainerRecord> containers, StringPalette palette) {}

    public record Progress(boolean running, int regionsRead, int regionsTotal, int chunksRead,
                           int containersFound, int chunksSkippedLoaded, int chunksFailed) {
        public boolean isComplete() {
            return !running && regionsTotal > 0 && regionsRead >= regionsTotal;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public Progress progress() {
        return new Progress(running.get(), regionsRead.get(), regionsTotal.get(), chunksRead.get(),
                containersFound.get(), chunksSkippedLoaded.get(), chunksFailed.get());
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

    private static void writeLog(ScanLog log, Path file) {
        try {
            log.write(file);
        } catch (IOException e) {
            ChestTracker.LOG.warn("Could not write the scan log {}: {}", file, e.toString());
        }
    }

    public void forgetScanned(Path logFile) {
        ScanLog log = new ScanLog();
        writeLog(log, logFile);
    }

    public void cancel() {
        cancelled.set(true);
        Thread current = worker;
        if (current != null) current.interrupt();
    }

    public boolean start(Path worldRoot, long tick) {
        if (!running.compareAndSet(false, true)) return false;

        cancelled.set(false);
        chunksRead.set(0);
        regionsRead.set(0);
        regionsTotal.set(0);
        containersFound.set(0);
        chunksSkippedLoaded.set(0);
        chunksFailed.set(0);

        Thread thread = new Thread(() -> {
            try {
                scanWorld(worldRoot, tick);
            } catch (Throwable t) {
                ChestTracker.LOG.error("Region scan failed", t);
            } finally {
                running.set(false);
                worker = null;
            }
        }, "ChestTracker-RegionScan");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        worker = thread;
        thread.start();
        return true;
    }

    private static final int LOG_EVERY = 25;

    private void scanWorld(Path worldRoot, long tick) throws IOException {
        List<WorldLayout.DimensionRegions> dimensions = WorldLayout.discover(worldRoot);
        if (dimensions.isEmpty()) {
            ChestTracker.LOG.warn("No region directories found under {}", worldRoot);
            return;
        }

        List<Path> allRegions = new ArrayList<>();
        List<String> owners = new ArrayList<>();
        for (WorldLayout.DimensionRegions dimension : dimensions) {
            for (Path file : WorldLayout.regionFiles(dimension.regionDir())) {
                allRegions.add(file);
                owners.add(dimension.dimensionId());
            }
        }
        regionsTotal.set(allRegions.size());

        Path logFile = tracker.scanLogFile();
        ScanLog log = ScanLog.read(logFile);

        List<Path> pending = new ArrayList<>();
        List<String> pendingOwners = new ArrayList<>();
        for (int i = 0; i < allRegions.size(); i++) {
            Path file = allRegions.get(i);
            if (log.isCurrent(file.toString(), sizeOf(file), modifiedAt(file))) {
                regionsRead.incrementAndGet();
                continue;
            }
            pending.add(file);
            pendingOwners.add(owners.get(i));
        }

        ChestTracker.LOG.info("Region scan starting: {} of {} region files need reading "
                        + "across {} dimension(s)",
                pending.size(), allRegions.size(), dimensions.size());

        Set<String> trackedIds = tracker.containerTypes().known();

        int sinceWrite = 0;
        for (int i = 0; i < pending.size() && !cancelled.get(); i++) {
            Path file = pending.get(i);
            long size = sizeOf(file);
            long modifiedAt = modifiedAt(file);

            scanRegion(file, pendingOwners.get(i), trackedIds, tick);
            regionsRead.incrementAndGet();

            log.mark(file.toString(), size, modifiedAt);
            if (++sinceWrite >= LOG_EVERY) {
                sinceWrite = 0;
                writeLog(log, logFile);
            }
        }

        writeLog(log, logFile);

        ChestTracker.LOG.info("Region scan {}: {} chunks, {} containers, {} failed, "
                        + "{} regions skipped as unchanged",
                cancelled.get() ? "cancelled" : "complete",
                chunksRead.get(), containersFound.get(), chunksFailed.get(),
                allRegions.size() - pending.size());
    }

    private void scanRegion(Path file, String dimensionId, Set<String> trackedIds, long tick) {
        int[] region = RegionFile.parseCoords(file.getFileName().toString());
        if (region == null) return;

        StringPalette localPalette = new StringPalette();
        int localDimensionId = localPalette.intern(dimensionId);

        Map<Long, List<ContainerRecord>> byChunk = new LinkedHashMap<>();
        List<ChunkExtractor.StructureBox> regionBoxes = new ArrayList<>();

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
                    byChunk.put(BlockKey.chunkKey(chunkX, chunkZ), contents.containers());
                    chunksRead.incrementAndGet();
                } catch (IOException torn) {
                    chunksFailed.incrementAndGet();
                }
            }
        } catch (IOException e) {
            ChestTracker.LOG.warn("Skipping region {}: {}", file.getFileName(), e.toString());
            return;
        }

        for (Map.Entry<Long, List<ContainerRecord>> entry : byChunk.entrySet()) {
            List<ContainerRecord> classified = OriginClassifier.classify(entry.getValue(), regionBoxes);
            containersFound.addAndGet(classified.size());
            pending.add(new Batch(dimensionId, entry.getKey(), classified, localPalette));
        }
    }

    private void throttle() {
        while (pending.size() > 512 && !cancelled.get()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                cancelled.set(true);
                return;
            }
        }
    }

    public int drain(java.util.function.BiPredicate<String, Long> isChunkLoaded,
                     java.util.function.BiConsumer<String, Long> liveScanChunk,
                     long tickTimeNanos) {
        if (tickTimeNanos > TICK_TIME_BUDGET_NANOS) return 0;

        int applied = 0;
        Batch batch;
        while (applied < APPLY_BUDGET_PER_TICK && (batch = pending.poll()) != null) {
            applied++;

            if (isChunkLoaded.test(batch.dimensionId(), batch.chunkKey())) {
                liveScanChunk.accept(batch.dimensionId(), batch.chunkKey());
                chunksSkippedLoaded.incrementAndGet();
                continue;
            }

            java.util.Set<Long> positions = new java.util.HashSet<>();
            for (ContainerRecord record : batch.containers()) {
                ContainerRecord remapped = tracker.remap(record, batch.palette(), batch.dimensionId());
                if (remapped == null) continue;
                tracker.record(batch.dimensionId(), remapped);
                positions.add(remapped.pos());
            }
            tracker.reconcileChunk(batch.dimensionId(), batch.chunkKey(), positions);
        }
        return applied;
    }

    public int queuedBatches() {
        return pending.size();
    }
}
