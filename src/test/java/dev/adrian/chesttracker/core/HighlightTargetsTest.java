package dev.adrian.chesttracker.core;

import dev.adrian.chesttracker.core.highlight.HighlightTargets;
import dev.adrian.chesttracker.core.util.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HighlightTargetsTest {

    private static long at(int x, int y, int z) {
        return BlockKey.pack(x, y, z);
    }

    @Test
    void picksTheClosestOfSeveral() {
        List<Long> targets = List.of(at(100, 64, 0), at(10, 64, 0), at(50, 64, 0));
        assertEquals(at(10, 64, 0), HighlightTargets.nearest(targets, 0, 64, 0));
    }

    @Test
    void handsGuidanceOverAsThePlayerMoves() {
        long near = at(0, 64, 0);
        long far = at(200, 64, 0);
        List<Long> targets = List.of(near, far);

        assertEquals(near, HighlightTargets.nearest(targets, 5, 64, 0));
        assertEquals(far, HighlightTargets.nearest(targets, 150, 64, 0));
    }

    @Test
    void countsHeightNotJustGroundDistance() {
        List<Long> overhead = List.of(at(0, 130, 0), at(80, 64, 0));
        assertEquals(at(0, 130, 0), HighlightTargets.nearest(overhead, 0, 64, 0));

        List<Long> tooHigh = List.of(at(0, 200, 0), at(80, 64, 0));
        assertEquals(at(80, 64, 0), HighlightTargets.nearest(tooHigh, 0, 64, 0));
    }

    @Test
    void aTieKeepsTheEarlierTarget() {
        long first = at(-10, 64, 0);
        long second = at(10, 64, 0);
        assertEquals(first, HighlightTargets.nearest(List.of(first, second), 0, 64, 0));
        assertEquals(first, HighlightTargets.nearest(List.of(first, second), 0, 64, 0));
    }

    @Test
    void aSingleTargetIsItsOwnNearest() {
        assertEquals(at(7, 8, 9), HighlightTargets.nearest(List.of(at(7, 8, 9)), 0, 0, 0));
    }

    @Test
    void emptyAndNullSelectionsDoNotThrow() {
        assertEquals(0L, HighlightTargets.nearest(List.of(), 0, 64, 0));
        assertEquals(0L, HighlightTargets.nearest(null, 0, 64, 0));
    }

    @Test
    void worksWithNegativeCoordinates() {
        List<Long> targets = List.of(at(-980, 63, -930), at(-12, 64, -8));
        assertEquals(at(-980, 63, -930), HighlightTargets.nearest(targets, -1000, 64, -940));
        assertEquals(at(-12, 64, -8), HighlightTargets.nearest(targets, 0, 64, 0));
    }
}
