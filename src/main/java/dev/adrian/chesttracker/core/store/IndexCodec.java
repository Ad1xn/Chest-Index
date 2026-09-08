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

/**
 * Reads and writes a {@link WorldIndex} in a compact binary form.
 *
 * <p>Deliberately not NBT: a large world holds hundreds of thousands of stacks,
 * and NBT's per-tag name and type overhead is pure waste when every record has
 * the same shape. Varints plus a string palette keep the file roughly an order
 * of magnitude smaller and much faster to load.
 *
 * <p>Each file is <b>self-contained</b>: it carries the whole palette followed
 * by the records. That duplicates the palette across the two or three dimension
 * files in a world, costing tens of kilobytes, and buys immunity from a
 * half-written palette in one file corrupting the others. Worth it.
 *
 * <p>Files are written to a temporary sibling and then moved into place, so an
 * interrupted save leaves the previous index intact rather than a truncated one.
 */
public final class IndexCodec {

    /** "CTIX" - ChestTracker IndeX. */
    private static final int MAGIC = 0x43544958;

    /**
     * Bump on any layout change; {@link #read} rejects versions newer than this.
     *
     * <p>Version 2 added what distinguishes one stack from another of the same
     * item - its enchantments, its potion, its lore - without which a search
     * for "mending" has nothing to match against. Version 1 files are still
     * read: every stack simply comes back with none of that, which is exactly
     * true of what was stored, and the next scan fills it in.
     */
    public static final int FORMAT_VERSION = 2;

    /** The oldest layout {@link #read} still understands. */
    public static final int OLDEST_READABLE_VERSION = 1;

    private static final int FLAG_UNLOOTED = 1 << 2;
    private static final int FLAG_CONTENTS_KNOWN = 1 << 3;
    private static final int FLAG_HAS_OWNER = 1 << 4;
    private static final int FLAG_HAS_NAME = 1 << 5;
    private static final int ORIGIN_MASK = 0b11;

    private static final Origin[] ORIGINS = Origin.values();

    private IndexCodec() {}

    /** A palette and the index that references it, as stored in one file. */
    public record Snapshot(StringPalette palette, WorldIndex index) {}

    /**
     * Everything one file needs, detached from the live index it came from.
     *
     * <p>The point is that taking one is cheap and holding one is safe.
     * {@link ContainerRecord} is immutable and the two lists are copies, so a
     * snapshot can be handed to a background thread and written there while the
     * index it was taken from carries on changing. The client index needs
     * exactly that: it lives on the render thread, where a gzip write of a few
     * megabytes is a visible stutter.
     *
     * @param paletteEntries the palette in id order, as {@link StringPalette#entries()}
     * @param dimensionId    palette id of the dimension these records belong to
     * @param records        the records themselves
     */
    public record Frozen(List<String> paletteEntries, int dimensionId, List<ContainerRecord> records) {

        public Frozen {
            paletteEntries = List.copyOf(paletteEntries);
            records = List.copyOf(records);
        }

        /** Takes a snapshot of a live index. Must be called on the thread that owns it. */
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
            // Some filesystems cannot move atomically; a plain replace still
            // beats writing the destination in place.
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void write(OutputStream rawOut, StringPalette palette, WorldIndex index) throws IOException {
        write(rawOut, Frozen.of(palette, index));
    }

    /**
     * Buffer for both directions.
     *
     * <p>Eight kilobytes - the default - means a syscall and a deflate flush
     * for every eight kilobytes of a file that is megabytes long. Sixty-four
     * costs sixty-four kilobytes of heap for the length of one save.
     */
    private static final int BUFFER = 64 * 1024;

    /**
     * A gzip stream tuned for speed rather than size.
     *
     * <p>The default compression level is a reasonable trade for a file being
     * sent somewhere. This one is written to the player's own disk, several
     * times an hour, on a thread somebody is waiting for - and at level one it
     * writes in a fraction of the time for a file about a fifth larger, which
     * on a two megabyte index is a few hundred kilobytes nobody will notice
     * against a world folder measured in gigabytes.
     */
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
            // Below, every record is read at the version the file says it is.
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
            // Almost always zero, and a varint zero is one byte - so the stacks
            // that carry nothing pay one byte each for the ones that do.
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
        // Zig-zag keeps small negative values one byte. The zig-zag of a large
        // positive int overflows back to a negative int, so it must be widened
        // as *unsigned* - sign-extending it would emit ten bytes and read back
        // as a different number.
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
