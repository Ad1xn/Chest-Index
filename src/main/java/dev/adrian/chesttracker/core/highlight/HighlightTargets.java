package dev.adrian.chesttracker.core.highlight;

import dev.adrian.chesttracker.core.util.BlockKey;

import java.util.List;

public final class HighlightTargets {

    private HighlightTargets() {}

    public static long nearest(List<Long> targets, int x, int y, int z) {
        if (targets == null || targets.isEmpty()) return 0L;

        long from = BlockKey.pack(x, y, z);
        long best = targets.get(0);
        double bestDistance = BlockKey.distanceSq(from, best);

        for (int i = 1; i < targets.size(); i++) {
            long candidate = targets.get(i);
            double distance = BlockKey.distanceSq(from, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
