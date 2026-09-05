package dev.adrian.chesttracker.core;

import dev.adrian.chesttracker.core.store.ScanLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScanLogTest {

    @Test
    void aMarkedFileIsCurrentOnlyWhileItIsUnchanged() {
        ScanLog log = new ScanLog();
        log.mark("r.0.0.mca", 4096, 1000L);

        assertTrue(log.isCurrent("r.0.0.mca", 4096, 1000L));
        assertFalse(log.isCurrent("r.0.0.mca", 8192, 1000L));
        assertFalse(log.isCurrent("r.0.0.mca", 4096, 2000L));
        assertFalse(log.isCurrent("r.1.0.mca", 4096, 1000L));
    }

    @Test
    void anUnknownFileIsNeverCurrent() {
        assertFalse(new ScanLog().isCurrent("r.0.0.mca", 1, 1));
    }

    @Test
    void survivesTheRoundTrip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scanned-regions.txt");
        ScanLog written = new ScanLog();
        written.mark("/worlds/one/region/r.0.0.mca", 4096, 1_700_000_000_000L);
        written.mark("/worlds/one/region/r.-1.-1.mca", 12288, 1_700_000_000_001L);
        written.write(file);

        ScanLog read = ScanLog.read(file);
        assertEquals(2, read.size());
        assertTrue(read.isCurrent("/worlds/one/region/r.0.0.mca", 4096, 1_700_000_000_000L));
        assertTrue(read.isCurrent("/worlds/one/region/r.-1.-1.mca", 12288, 1_700_000_000_001L));
    }

    @Test
    void keysMayContainAnythingAPathCan(@TempDir Path dir) throws IOException {
        String awkward = "/worlds/my world (copy)/DIM-1/region/r.0.0.mca";
        Path file = dir.resolve("log.txt");

        ScanLog written = new ScanLog();
        written.mark(awkward, 7, 8);
        written.write(file);

        assertTrue(ScanLog.read(file).isCurrent(awkward, 7, 8));
    }

    @Test
    void aMissingLogIsSimplyEmpty(@TempDir Path dir) {
        assertEquals(0, ScanLog.read(dir.resolve("absent.txt")).size());
    }

    @Test
    void rubbishInTheLogCostsARescanRatherThanAFailure(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("log.txt");
        Files.writeString(file, String.join("\n",
                "not a log line at all",
                "12\tnotanumber\t/a/b.mca",
                "",
                "4096\t1000\t/worlds/one/region/r.0.0.mca"), StandardCharsets.UTF_8);

        ScanLog log = ScanLog.read(file);
        assertEquals(1, log.size());
        assertTrue(log.isCurrent("/worlds/one/region/r.0.0.mca", 4096, 1000L));
    }

    @Test
    void writingReplacesRatherThanAppends(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("log.txt");

        ScanLog first = new ScanLog();
        first.mark("a.mca", 1, 1);
        first.mark("b.mca", 2, 2);
        first.write(file);

        ScanLog second = new ScanLog();
        second.mark("a.mca", 9, 9);
        second.write(file);

        ScanLog read = ScanLog.read(file);
        assertEquals(1, read.size());
        assertTrue(read.isCurrent("a.mca", 9, 9));
        assertFalse(read.isCurrent("b.mca", 2, 2));
    }

    @Test
    void clearingForgetsEverything() {
        ScanLog log = new ScanLog();
        log.mark("a.mca", 1, 1);
        log.clear();
        assertEquals(0, log.size());
        assertFalse(log.isCurrent("a.mca", 1, 1));
    }

    @Test
    void noTemporaryFileIsLeftBehind(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("log.txt");
        new ScanLog().write(file);
        try (var entries = Files.list(dir)) {
            assertTrue(entries.noneMatch(path -> path.toString().endsWith(".tmp")));
        }
    }
}
