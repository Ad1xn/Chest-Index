package dev.adrian.chesttracker.core.index;

import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.StackEntry;

import java.util.List;

public record SearchResult(ContainerRecord container, double distanceSq, List<StackEntry> matches) {

    public SearchResult {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }

    public double distance() {
        return Math.sqrt(distanceSq);
    }

    public int matchedCount() {
        int total = 0;
        for (StackEntry entry : matches) total += entry.count();
        return total;
    }
}
