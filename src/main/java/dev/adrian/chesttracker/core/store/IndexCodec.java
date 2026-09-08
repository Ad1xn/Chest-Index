package dev.adrian.chesttracker.core.store;

import dev.adrian.chesttracker.core.index.WorldIndex;
import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackEntry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class IndexCodec {

    private static final int MAGIC = 0x43544958;

    public static final int FORMAT_VERSION = 2;

    public static final int OLDEST_READABLE_VERSION = 1;

    private static final int FLAG_UNLOOTED = 1 << 2;
    private static final int FLAG_CONTENTS_KNOWN = 1 << 3;
    private static final int FLAG_HAS_OWNER = 1 << 4;
    private static final int FLAG_HAS_NAME = 1 << 5;
    private static final int ORIGIN_MASK = 0b11;

    private static final Origin[] ORIGINS = Origin.values();

    private IndexCodec() {}

    public record Snapshot(StringPalette palette, WorldIndex index) {}

    public record Frozen(List<String> paletteEntries, int dimensionId, List<ContainerRecord> records) {

        public Frozen {
            paletteEntries = List.copyOf(paletteEntries);
            records = List.copyOf(records);
        }

        public static Frozen of(StringPalette palette, WorldIndex index) {
            return new Frozen(palette.entries(), index.dimensionId(), List.copyOf(index.all()));
        }
    }

    public static void write(Path file, StringPalette palette, WorldIndex index) throws IOException {
        write(file, Frozen.of(palette, index));
    }

    public static void write(Path file, Frozen frozen) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            write(out, frozen);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void write(OutputStream rawOut, StringPalette palette, WorldIndex index) throws IOException {
        write(rawOut, Frozen.of(palette, index));
    }

    private static final int BUFFER = 64 * 1024;

    private static GZIPOutputStream fastGzip(OutputStream out) throws IOException {
        GZIPOutputStream gzip = new GZIPOutputStream(out, BUFFER) {
            {
                def.setLevel(java.util.zip.Deflater.BEST_SPEED);
            }
        };
        return gzip;
    }

    public static void write(OutputStream rawOut, Frozen frozen) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(fastGzip(rawOut), BUFFER))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeInt(frozen.dimensionId());

            List<String> entries = frozen.paletteEntries();
            writeVarInt(out, entries.size());
            for (String entry : entries) out.writeUTF(entry);

            writeVarInt(out, frozen.records().size());
            for (ContainerRecord record : frozen.records()) writeRecord(out, record);
        }
    }

    public static Snapshot read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        }
    }

    public static Snapshot read(InputStream rawIn) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(rawIn, BUFFER), BUFFER))) {

            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Not a ChestTracker index (magic 0x" + Integer.toHexString(magic) + ")");
            }
            int version = in.readInt();
            if (version < OLDEST_READABLE_VERSION || version > FORMAT_VERSION) {
                throw new IOException("Unsupported index format version " + version
                        + " (this build reads " + OLDEST_READABLE_VERSION
                        + " to " + FORMAT_VERSION + ")");
            }
            int dimensionId = in.readInt();

            int paletteSize = readVarInt(in);
            List<String> entries = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) entries.add(in.readUTF());
            StringPalette palette = StringPalette.fromEntries(entries);

            WorldIndex index = new WorldIndex(dimensionId);
            int count = readVarInt(in);
            for (int i = 0; i < count; i++) index.put(readRecord(in, version));

            return new Snapshot(palette, index);
        } catch (EOFException truncated) {
            throw new IOException("Index file is truncated", truncated);
        }
    }

    private static void writeRecord(DataOutputStream out, ContainerRecord record) throws IOException {
        out.writeLong(record.pos());
        writeVarInt(out, record.dimensionId());
        writeVarInt(out, record.typeId());

        int flags = record.origin().ordinal() & ORIGIN_MASK;
        if (record.unlooted()) flags |= FLAG_UNLOOTED;
        if (record.contentsKnown()) flags |= FLAG_CONTENTS_KNOWN;
        if (record.owner() != null) flags |= FLAG_HAS_OWNER;
        if (record.customName() != null) flags |= FLAG_HAS_NAME;
        out.writeByte(flags);

        if (record.owner() != null) {
            out.writeLong(record.owner().getMostSignificantBits());
            out.writeLong(record.owner().getLeastSignificantBits());
        }
        if (record.customName() != null) out.writeUTF(record.customName());
        writeVarLong(out, record.lastSeenTick());

        writeVarInt(out, record.contents().size());
        for (StackEntry entry : record.contents()) {
            writeVarInt(out, entry.itemId());
            writeVarInt(out, entry.count());
            writeVarInt(out, entry.depth());
            if (entry.customName() != null) {
                out.writeByte(1);
                out.writeUTF(entry.customName());
            } else {
                out.writeByte(0);
            }
            writeVarInt(out, entry.details().size());
            for (int detail : entry.details()) writeVarInt(out, detail);
        }
    }

    private static ContainerRecord readRecord(DataInputStream in, int version) throws IOException {
        long pos = in.readLong();
        int dimensionId = readVarInt(in);
        int typeId = readVarInt(in);

        int flags = in.readUnsignedByte();
        Origin origin = ORIGINS[flags & ORIGIN_MASK];
        boolean unlooted = (flags & FLAG_UNLOOTED) != 0;
        boolean contentsKnown = (flags & FLAG_CONTENTS_KNOWN) != 0;

        UUID owner = null;
        if ((flags & FLAG_HAS_OWNER) != 0) {
            owner = new UUID(in.readLong(), in.readLong());
        }
        String customName = (flags & FLAG_HAS_NAME) != 0 ? in.readUTF() : null;
        long lastSeenTick = readVarLong(in);

        int contentCount = readVarInt(in);
        List<StackEntry> contents = new ArrayList<>(contentCount);
        for (int i = 0; i < contentCount; i++) {
            int itemId = readVarInt(in);
            int count = readVarInt(in);
            int depth = readVarInt(in);
            String entryName = in.readUnsignedByte() == 1 ? in.readUTF() : null;

            List<Integer> details = List.of();
            if (version >= 2) {
                int detailCount = readVarInt(in);
                if (detailCount > 0) {
                    details = new ArrayList<>(detailCount);
                    for (int d = 0; d < detailCount; d++) details.add(readVarInt(in));
                }
            }
            contents.add(new StackEntry(itemId, count, depth, entryName, details));
        }

        return new ContainerRecord(pos, dimensionId, typeId, origin, owner,
                unlooted, contentsKnown, customName, lastSeenTick, contents);
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int zigzag = (value << 1) ^ (value >> 31);
        writeVarLong(out, Integer.toUnsignedLong(zigzag));
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int zigzag = (int) readVarLong(in);
        return (zigzag >>> 1) ^ -(zigzag & 1);
    }

    static void writeVarLong(DataOutputStream out, long value) throws IOException {
        long remaining = value;
        while ((remaining & ~0x7FL) != 0) {
            out.writeByte((int) (remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.writeByte((int) remaining);
    }

    static long readVarLong(DataInputStream in) throws IOException {
        long result = 0;
        int shift = 0;
        while (true) {
            if (shift > 63) throw new IOException("VarLong is too long / file is corrupt");
            int b = in.readUnsignedByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
        }
    }
}
