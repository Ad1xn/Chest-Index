package dev.adrian.chestindex.core;

import dev.adrian.chestindex.core.util.Bits;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The membership test every stack in the index goes through.
 *
 * <p>A wrong answer here does not crash anything - it silently shows the player
 * items that are not there, or hides ones that are.
 */
class BitsTest {

    @Test
    void anEmptyFilterMeansNoConstraintRatherThanNoMatches() {
        // Null is the "do not test this at all" signal, matching how empty
        // filter sets read on IndexQuery. Returning an empty mask instead would
        // silently exclude everything.
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

        // The palette grows while a query is in flight, so an id larger than
        // anything the mask was built from is an ordinary "no", not a crash.
        assertFalse(Bits.test(mask, 9_000));
        assertFalse(Bits.test(mask, Integer.MAX_VALUE));
    }

    @Test
    void anUninternedIdMatchesNothingAndDoesNotThrow() {
        // -1 is what StringPalette returns for a string it has never seen. A
        // query naming an unknown item must find nothing.
        assertFalse(Bits.test(Bits.of(Set.of(3)), -1));

        long[] onlyUnknown = Bits.of(Set.of(-1));
        assertNotNull(onlyUnknown, "an asked-for filter is still a filter");
        assertFalse(Bits.test(onlyUnknown, -1));
        assertFalse(Bits.test(onlyUnknown, 0));
    }
}
