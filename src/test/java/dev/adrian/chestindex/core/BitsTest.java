package dev.adrian.chestindex.core;

import dev.adrian.chestindex.core.util.Bits;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BitsTest {

    @Test
    void anEmptyFilterMeansNoConstraintRatherThanNoMatches() {
        assertNull(Bits.of(Set.of()));
        assertNull(Bits.of(null));
    }

    @Test
    void holdsTheIdsItWasGiven() {
        long[] mask = Bits.of(Set.of(0, 1, 63, 64, 65, 4095));

        for (int id : new int[] {0, 1, 63, 64, 65, 4095}) {
            assertTrue(Bits.test(mask, id), "id " + id + " was set");
        }
        for (int id : new int[] {2, 62, 66, 4094}) {
            assertFalse(Bits.test(mask, id), "id " + id + " was never set");
        }
    }

    @Test
    void idsPastTheEndAreAbsentRatherThanOutOfBounds() {
        long[] mask = Bits.of(Set.of(3));

        assertFalse(Bits.test(mask, 9_000));
        assertFalse(Bits.test(mask, Integer.MAX_VALUE));
    }

    @Test
    void anUninternedIdMatchesNothingAndDoesNotThrow() {
        assertFalse(Bits.test(Bits.of(Set.of(3)), -1));

        long[] onlyUnknown = Bits.of(Set.of(-1));
        assertNotNull(onlyUnknown);
        assertFalse(Bits.test(onlyUnknown, -1));
        assertFalse(Bits.test(onlyUnknown, 0));
    }
}
