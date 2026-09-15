package dev.adrian.chestindex.core;

import dev.adrian.chestindex.core.index.WorldIndex;
import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.store.IndexCodec;
import dev.adrian.chestindex.core.store.StringPalette;
import dev.adrian.chestindex.core.util.BlockKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The index is persisted between sessions, so a codec bug loses a player's
 * whole search history silently. Every field gets a round-trip assertion.
 */
class IndexCodecTest {

    private static StringPalette palette() {
        StringPalette palette = new StringPalette();
        palette.intern("minecraft:overworld");
        palette.intern("minecraft:chest");
        palette.intern("minecraft:diamond");
        return palette;
    }

    @Test
    void roundTripsTheDetailsThatTellTwoStacksApart() throws IOException {
        StringPalette palette = palette();
        int mending = palette.intern("ench:minecraft:mending");
        int lore = palette.intern("lore:for the nether trip");

        ContainerRecord original = new ContainerRecord(
                BlockKey.pack(8, 64, 8), 0, 1, Origin.PLAYER_PLACED, null,
                false, true, null, 1L,
                List.of(new StackEntry(2, 1, 0, null, List.of(mending, lore)),
                        // The plain stack beside it must come back plain.
                        new StackEntry(2, 12, 0, null)));

        WorldIndex index = new WorldIndex(0);
        index.put(original);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IndexCodec.write(out, palette, index);
        IndexCodec.Snapshot snapshot = IndexCodec.read(new ByteArrayInputStream(out.toByteArray()));

        ContainerRecord restored = snapshot.index().get(original.pos());
        assertNotNull(restored);
        assertEquals(List.of(mending, lore), restored.contents().get(0).details());
        assertTrue(restored.contents().get(1).details().isEmpty());
        assertEquals(original, restored);
    }

    @Test
    void readsAVersionOneFileAndSaysTheStacksHaveNoDetails() throws IOException {
        // The migration, which is the whole reason the version was bumped: an
        // index written before details existed has to load, not be thrown away
        // and rescanned from nothing.
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.util.zip.GZIPOutputStream(raw))) {
            out.writeInt(0x43544958);     // CTIX
            out.writeInt(1);              // the old version
            out.writeInt(0);              // dimension palette id

            List<String> entries = palette().entries();
            writeVarInt(out, entries.size());
            for (String entry : entries) out.writeUTF(entry);

            writeVarInt(out, 1);          // one record
            out.writeLong(BlockKey.pack(1, 2, 3));
            writeVarInt(out, 0);          // dimension
            writeVarInt(out, 1);          // type
            out.writeByte(Origin.PLAYER_PLACED.ordinal() | (1 << 3));  // contents known
            writeVarLong(out, 99L);       // last seen
            writeVarInt(out, 1);          // one stack
            writeVarInt(out, 2);          // item
            writeVarInt(out, 5);          // count
            writeVarInt(out, 0);          // depth
            out.writeByte(0);             // no custom name, and no details after it
        }

        IndexCodec.Snapshot snapshot = IndexCodec.read(new ByteArrayInputStream(raw.toByteArray()));
        ContainerRecord restored = snapshot.index().get(BlockKey.pack(1, 2, 3));

        assertNotNull(restored);
        assertEquals(5, restored.contents().get(0).count());
        assertTrue(restored.contents().get(0).details().isEmpty());
    }

    /** Zig-zag varint, as the codec writes them; see IndexCodec. */
    private static void writeVarInt(java.io.DataOutputStream out, int value) throws IOException {
        writeVarLong(out, Integer.toUnsignedLong((value << 1) ^ (value >> 31)));
    }

    private static void writeVarLong(java.io.DataOutputStream out, long value) throws IOException {
        long remaining = value;
        while ((remaining & ~0x7FL) != 0) {
            out.writeByte((int) (remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.writeByte((int) remaining);
    }

    @Test
    void roundTripsAFullyPopulatedRecord() throws IOException {
        UUID owner = UUID.randomUUID();
        ContainerRecord original = new ContainerRecord(
                BlockKey.pack(-1234, -59, 5678), 0, 1, Origin.PLAYER_PLACED, owner,
                true, true, "Ender Storage", 123456789L,
                List.of(new StackEntry(2, 64, 0, "Spare Pickaxe"), new StackEntry(2, 1, 2, null)));

        WorldIndex index = new WorldIndex(0);
        index.put(original);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IndexCodec.write(out, palette(), index);
        IndexCodec.Snapshot snapshot = IndexCodec.read(new ByteArrayInputStream(out.toByteArray()));

        ContainerRecord restored = snapshot.index().get(original.pos());
        assertNotNull(restored);
        assertEquals(original, restored);
        assertEquals(owner, restored.owner());
        assertEquals("Ender Storage", restored.customName());
        assertEquals("Spare Pickaxe", restored.contents().get(0).customName());
        assertEquals(2, restored.contents().get(1).depth());
    }

    @Test
    void roundTripsRecordsWithNoOwnerOrNames() throws IOException {
        ContainerRecord original = ContainerRecord.locationOnly(
                BlockKey.pack(0, 64, 0), 0, 1, Origin.UNKNOWN, 42L);

        WorldIndex index = new WorldIndex(0);
        index.put(original);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IndexCodec.write(out, palette(), index);
        IndexCodec.Snapshot snapshot = IndexCodec.read(new ByteArrayInputStream(out.toByteArray()));

        ContainerRecord restored = snapshot.index().get(original.pos());
        assertEquals(original, restored);
        assertNull(restored.owner());
        assertFalse(restored.contentsKnown(), "location-only must not come back claiming an empty container");
    }

    @Test
    void roundTripsThePalette() throws IOException {
        StringPalette original = palette();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IndexCodec.write(out, original, new WorldIndex(0));

        StringPalette restored = IndexCodec.read(new ByteArrayInputStream(out.toByteArray())).palette();

        assertEquals(original.size(), restored.size());
        for (int id = 0; id < original.size(); id++) {
            assertEquals(original.value(id), restored.value(id));
        }
    }

    @Test
    void roundTripsManyRecordsAndRebuildsTheInvertedIndex() throws IOException {
        WorldIndex index = new WorldIndex(0);
        for (int i = 0; i < 5000; i++) {
            index.put(new ContainerRecord(BlockKey.pack(i, 64, -i), 0, 1, Origin.NATURAL,
                    null, false, true, null, i, List.of(new StackEntry(2, i % 64 + 1))));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IndexCodec.write(out, palette(), index);
        WorldIndex restored = IndexCodec.read(new ByteArrayInputStream(out.toByteArray())).index();

        assertEquals(5000, restored.size());
        assertEquals(5000, restored.query(
                dev.adrian.chestindex.core.index.IndexQuery.builder().item(2).build()).size(),
                "loading must rebuild the inverted index, not just the primary map");
    }

    @Test
    void varintsSurviveExtremeValues() throws IOException {
        // Zig-zag of a large positive int overflows to a negative int; if that is
        // sign-extended on the way out the value comes back wrong.
        for (int value : new int[]{0, 1, -1, 127, 128, -128, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            ContainerRecord record = new ContainerRecord(BlockKey.pack(0, 0, 0), value, value,
                    Origin.UNKNOWN, null, false, true, null, Long.MAX_VALUE,
                    List.of(new StackEntry(value, Math.abs(value % 1000), 0)));
            WorldIndex index = new WorldIndex(value);
            index.put(record);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IndexCodec.write(out, palette(), index);
            WorldIndex restored = IndexCodec.read(new ByteArrayInputStream(out.toByteArray())).index();

            ContainerRecord back = restored.get(record.pos());
            assertEquals(value, back.dimensionId(), "dimensionId " + value);
            assertEquals(value, back.typeId(), "typeId " + value);
            assertEquals(value, back.contents().get(0).itemId(), "itemId " + value);
            assertEquals(Long.MAX_VALUE, back.lastSeenTick());
        }
    }

    @Test
    void writesAndReadsThroughTheFilesystem(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested").resolve("overworld.idx");
        WorldIndex index = new WorldIndex(7);
        index.put(ContainerRecord.locationOnly(BlockKey.pack(1, 2, 3), 7, 1, Origin.NATURAL, 9L));

        IndexCodec.write(file, palette(), index);

        assertTrue(Files.exists(file));
        assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")),
                "the temporary file must not be left behind");
        assertEquals(7, IndexCodec.read(file).index().dimensionId());
    }

    @Test
    void rejectsAFileThatIsNotAnIndex() {
        byte[] garbage = new byte[64];
        assertThrows(IOException.class, () -> IndexCodec.read(new ByteArrayInputStream(garbage)));
    }

    @Test
    void rejectsATruncatedFile() throws IOException {
        WorldIndex index = new WorldIndex(0);
        index.put(ContainerRecord.locationOnly(BlockKey.pack(1, 2, 3), 0, 1, Origin.NATURAL, 9L));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IndexCodec.write(out, palette(), index);

        byte[] truncated = new byte[out.size() / 2];
        System.arraycopy(out.toByteArray(), 0, truncated, 0, truncated.length);

        assertThrows(IOException.class, () -> IndexCodec.read(new ByteArrayInputStream(truncated)));
    }

    /**
     * A snapshot must say exactly what the live index said.
     *
     * <p>This is what lets the client-side index be written on a background
     * thread: it is taken on the thread that owns the index and is immutable
     * afterwards, so the writer cannot see a half-applied change. A snapshot
     * that dropped or reordered anything would corrupt saves in a way that only
     * showed up on the next session.
     */
    @Test
    void aFrozenSnapshotWritesTheSameFileAsTheLiveIndex() throws IOException {
        WorldIndex index = new WorldIndex(0);
        index.put(new ContainerRecord(BlockKey.pack(1, 64, 2), 0, 1, Origin.NATURAL, null,
                true, false, null, 7L, List.of()));
        index.put(new ContainerRecord(BlockKey.pack(-9, 12, 300), 0, 1, Origin.PLAYER_PLACED,
                UUID.randomUUID(), false, true, "Loot", 9L, List.of(new StackEntry(2, 12, 1, null))));

        ByteArrayOutputStream live = new ByteArrayOutputStream();
        IndexCodec.write(live, palette(), index);

        IndexCodec.Frozen frozen = IndexCodec.Frozen.of(palette(), index);
        ByteArrayOutputStream snapshotted = new ByteArrayOutputStream();
        IndexCodec.write(snapshotted, frozen);

        IndexCodec.Snapshot fromLive = IndexCodec.read(new ByteArrayInputStream(live.toByteArray()));
        IndexCodec.Snapshot fromFrozen = IndexCodec.read(new ByteArrayInputStream(snapshotted.toByteArray()));

        assertEquals(fromLive.index().size(), fromFrozen.index().size());
        for (ContainerRecord record : fromLive.index().all()) {
            assertEquals(record, fromFrozen.index().get(record.pos()),
                    "a snapshot must not alter what is written");
        }
    }

    /**
     * Taking a snapshot must detach it, or writing on another thread would
     * race the index it came from - which is the only reason it exists.
     */
    @Test
    void aFrozenSnapshotDoesNotSeeLaterChanges() {
        WorldIndex index = new WorldIndex(0);
        index.put(new ContainerRecord(BlockKey.pack(0, 0, 0), 0, 1, Origin.UNKNOWN, null,
                false, true, null, 1L, List.of()));

        IndexCodec.Frozen frozen = IndexCodec.Frozen.of(palette(), index);
        index.put(new ContainerRecord(BlockKey.pack(5, 5, 5), 0, 1, Origin.UNKNOWN, null,
                false, true, null, 2L, List.of()));

        assertEquals(1, frozen.records().size(),
                "a container added after the snapshot must not appear in it");
    }
}
