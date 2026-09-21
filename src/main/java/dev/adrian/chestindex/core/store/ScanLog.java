package dev.adrian.chestindex.core.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class ScanLog {
    public record Mark(long size, long modifiedAt) {}

    private final Map<String, Mark> marks = new HashMap<>();

    public boolean isCurrent(String key, long size, long modifiedAt) {
        Mark mark = marks.get(key);
        return mark != null && mark.size() == size && mark.modifiedAt() == modifiedAt;
    }

    public void mark(String key, long size, long modifiedAt) {
        marks.put(key, new Mark(size, modifiedAt));
    }

    public void forget(String key) {
        marks.remove(key);
    }

    public void clear() {
        marks.clear();
    }

    public int size() {
        return marks.size();
    }

    public static ScanLog read(Path file) {
        ScanLog log = new ScanLog();
        if (!Files.isRegularFile(file)) return log;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                int firstTab = line.indexOf('\t');
                int secondTab = firstTab < 0 ? -1 : line.indexOf('\t', firstTab + 1);
                if (secondTab < 0) continue;
                try {
                    long size = Long.parseLong(line.substring(0, firstTab));
                    long modifiedAt = Long.parseLong(line.substring(firstTab + 1, secondTab));
                    log.mark(line.substring(secondTab + 1), size, modifiedAt);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException | UncheckedIOException e) {
            return new ScanLog();
        }
        return log;
    }

    public void write(Path file) throws IOException {
        StringBuilder out = new StringBuilder(marks.size() * 48);
        for (Map.Entry<String, Mark> entry : marks.entrySet()) {
            out.append(entry.getValue().size()).append('\t')
                    .append(entry.getValue().modifiedAt()).append('\t')
                    .append(entry.getKey()).append('\n');
        }

        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, out.toString(), StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
