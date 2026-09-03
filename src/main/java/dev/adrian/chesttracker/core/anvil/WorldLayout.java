package dev.adrian.chesttracker.core.anvil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class WorldLayout {

    public static final String OVERWORLD = "minecraft:overworld";
    public static final String THE_NETHER = "minecraft:the_nether";
    public static final String THE_END = "minecraft:the_end";

    private WorldLayout() {}

    public record DimensionRegions(String dimensionId, Path regionDir) {}

    public static List<DimensionRegions> discover(Path worldRoot) throws IOException {
        Map<String, DimensionRegions> found = new LinkedHashMap<>();

        addIfRegionDir(found, OVERWORLD, worldRoot.resolve("region"));
        addIfRegionDir(found, THE_NETHER, worldRoot.resolve("DIM-1").resolve("region"));
        addIfRegionDir(found, THE_END, worldRoot.resolve("DIM1").resolve("region"));

        Path dimensionsRoot = worldRoot.resolve("dimensions");
        if (Files.isDirectory(dimensionsRoot)) {
            try (Stream<Path> namespaces = Files.list(dimensionsRoot)) {
                for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                    try (Stream<Path> paths = Files.list(namespace)) {
                        for (Path dimension : paths.filter(Files::isDirectory).toList()) {
                            String id = namespace.getFileName() + ":" + dimension.getFileName();
                            addIfRegionDir(found, id, dimension.resolve("region"));
                        }
                    }
                }
            }
        }

        return List.copyOf(found.values());
    }

    private static void addIfRegionDir(Map<String, DimensionRegions> found, String id, Path regionDir) {
        if (!found.containsKey(id) && Files.isDirectory(regionDir)) {
            found.put(id, new DimensionRegions(id, regionDir));
        }
    }

    public static Path regionDirFor(Path worldRoot, String dimensionId) throws IOException {
        for (DimensionRegions dimension : discover(worldRoot)) {
            if (dimension.dimensionId().equals(dimensionId)) return dimension.regionDir();
        }
        return null;
    }

    public static List<Path> regionFiles(Path regionDir) throws IOException {
        if (!Files.isDirectory(regionDir)) return List.of();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> entries = Files.list(regionDir)) {
            for (Path entry : entries.toList()) {
                if (Files.isRegularFile(entry) && RegionFile.isRegionFileName(entry.getFileName().toString())) {
                    files.add(entry);
                }
            }
        }
        files.sort(Path::compareTo);
        return files;
    }

    public static boolean isWorldRoot(Path candidate) throws IOException {
        if (!Files.isDirectory(candidate)) return false;
        return Files.isRegularFile(candidate.resolve("level.dat")) || !discover(candidate).isEmpty();
    }
}
