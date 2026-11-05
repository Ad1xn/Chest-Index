package dev.adrian.chestindex.core.anvil;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public final class RegionFile implements Closeable {
    public static final int SECTOR_BYTES = 4096;
    private static final int CHUNKS_PER_AXIS = 32;
    private static final int HEADER_SECTORS = 2;

    private static final int COMPRESSION_GZIP = 1;
    private static final int COMPRESSION_ZLIB = 2;
    private static final int COMPRESSION_NONE = 3;
    private static final int COMPRESSION_LZ4 = 4;
    private static final int EXTERNAL_FLAG = 0x80;

    private static final Pattern NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private final Path path;
    private final RandomAccessFile file;
    private final int[] locations = new int[CHUNKS_PER_AXIS * CHUNKS_PER_AXIS];
    private final byte[] chunkHeader = new byte[5];
    private final Inflater inflater = new Inflater();
    private byte[] inflated = new byte[1 << 17];

    private RegionFile(Path path, RandomAccessFile file) throws IOException {
        this.path = path;
        this.file = file;
        readHeader();
    }

    public static RegionFile open(Path path) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        try {
            return new RegionFile(path, raf);
        } catch (IOException | RuntimeException e) {
            raf.close();
            throw e;
        }
    }

    private void readHeader() throws IOException {
        long length = file.length();
        if (length == 0) return;
        if (length < SECTOR_BYTES) {
            throw new IOException("Region file shorter than its header: " + path);
        }
        file.seek(0);
        byte[] header = new byte[SECTOR_BYTES];
        file.readFully(header);
        for (int i = 0; i < locations.length; i++) {
            int base = i * 4;
            locations[i] = ((header[base] & 0xFF) << 24)
                         | ((header[base + 1] & 0xFF) << 16)
                         | ((header[base + 2] & 0xFF) << 8)
                         | (header[base + 3] & 0xFF);
        }
    }

    public Path path() {
        return path;
    }

    private static int indexOf(int localX, int localZ) {
        return (localX & 31) + (localZ & 31) * CHUNKS_PER_AXIS;
    }

    public boolean hasChunk(int localX, int localZ) {
        return locations[indexOf(localX, localZ)] != 0;
    }

    public List<int[]> presentChunks() {
        List<int[]> present = new ArrayList<>();
        for (int z = 0; z < CHUNKS_PER_AXIS; z++) {
            for (int x = 0; x < CHUNKS_PER_AXIS; x++) {
                if (locations[indexOf(x, z)] != 0) present.add(new int[]{x, z});
            }
        }
        return present;
    }

    public int chunkCount() {
        int count = 0;
        for (int location : locations) if (location != 0) count++;
        return count;
    }

    public NbtCompound readChunk(int localX, int localZ, Set<String> keepKeys) throws IOException {
        int location = locations[indexOf(localX, localZ)];
        if (location == 0) return null;

        int sectorOffset = location >>> 8;
        int sectorCount = location & 0xFF;
        if (sectorOffset < HEADER_SECTORS || sectorCount <= 0) {
            throw new IOException("Chunk " + localX + "," + localZ + " has a bogus header entry in " + path);
        }

        file.seek((long) sectorOffset * SECTOR_BYTES);
        file.readFully(chunkHeader);
        int declaredLength = ((chunkHeader[0] & 0xFF) << 24) | ((chunkHeader[1] & 0xFF) << 16)
                | ((chunkHeader[2] & 0xFF) << 8) | (chunkHeader[3] & 0xFF);
        int compression = chunkHeader[4] & 0xFF;

        if (declaredLength <= 0) {
            throw new IOException("Chunk " + localX + "," + localZ + " declares length " + declaredLength);
        }

        byte[] payload;
        if ((compression & EXTERNAL_FLAG) != 0) {
            payload = readExternal(localX, localZ);
            compression &= ~EXTERNAL_FLAG;
        } else {
            int payloadLength = declaredLength - 1;
            long available = file.length() - file.getFilePointer();
            if (payloadLength > available) {
                throw new IOException("Chunk " + localX + "," + localZ + " runs past the end of " + path
                        + " (wants " + payloadLength + ", " + available + " left)");
            }
            payload = new byte[payloadLength];
            file.readFully(payload);
        }

        if (compression == COMPRESSION_ZLIB) {
            int length = inflate(payload);
            return NbtReader.readNamedRoot(new NbtBytes(inflated, length), keepKeys);
        }
        return switch (compression) {
            case COMPRESSION_NONE -> NbtReader.readNamedRoot(new NbtBytes(payload, payload.length), keepKeys);
            default -> {
                try (InputStream in = decompress(compression, payload)) {
                    byte[] raw = in.readAllBytes();
                    yield NbtReader.readNamedRoot(new NbtBytes(raw, raw.length), keepKeys);
                }
            }
        };
    }

    private int inflate(byte[] payload) throws IOException {
        inflater.reset();
        inflater.setInput(payload);
        int length = 0;
        try {
            while (!inflater.finished()) {
                if (length == inflated.length) inflated = Arrays.copyOf(inflated, inflated.length * 2);
                int produced = inflater.inflate(inflated, length, inflated.length - length);
                if (produced == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IOException("Chunk data is cut short in " + path);
                }
                length += produced;
            }
        } catch (DataFormatException corrupt) {
            throw new IOException("Chunk data is corrupt in " + path + ": " + corrupt.getMessage());
        }
        return length;
    }

    private byte[] readExternal(int localX, int localZ) throws IOException {
        int[] region = regionCoords();
        int chunkX = region[0] * CHUNKS_PER_AXIS + (localX & 31);
        int chunkZ = region[1] * CHUNKS_PER_AXIS + (localZ & 31);
        Path external = path.resolveSibling("c." + chunkX + "." + chunkZ + ".mcc");
        if (!Files.exists(external)) {
            throw new IOException("Chunk " + chunkX + "," + chunkZ + " is marked external but " + external + " is missing");
        }
        return Files.readAllBytes(external);
    }

    private static InputStream decompress(int compression, byte[] payload) throws IOException {
        ByteArrayInputStream raw = new ByteArrayInputStream(payload);
        return switch (compression) {
            case COMPRESSION_GZIP -> new GZIPInputStream(raw);
            case COMPRESSION_ZLIB -> new InflaterInputStream(raw);
            case COMPRESSION_NONE -> raw;
            case COMPRESSION_LZ4 -> throw new IOException(
                    "Chunk uses LZ4 compression, which needs a codec this build does not bundle. "
                    + "Set region-file-compression to deflate in server.properties, or the chunk will be skipped.");
            default -> throw new IOException("Unknown chunk compression type " + compression);
        };
    }

    public int[] regionCoords() throws IOException {
        int[] coords = parseCoords(path.getFileName().toString());
        if (coords == null) throw new IOException("Not a region filename: " + path.getFileName());
        return coords;
    }

    public static int[] parseCoords(String fileName) {
        Matcher matcher = NAME.matcher(fileName);
        if (!matcher.matches()) return null;
        return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
    }

    public static boolean isRegionFileName(String fileName) {
        return NAME.matcher(fileName).matches();
    }

    @Override
    public void close() throws IOException {
        inflater.end();
        file.close();
    }
}
