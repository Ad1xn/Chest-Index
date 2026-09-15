package dev.adrian.chestindex.core;

import dev.adrian.chestindex.core.highlight.HighlightPulse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The colour pulse on the world markers")
class HighlightPulseTest {

    private static final long PERIOD = 1000L;

    @Test
    @DisplayName("rests at the resting colour at the start of a cycle")
    void restsAtCycleStart() {
        assertEquals(0.0f, HighlightPulse.at(0, PERIOD), 1.0e-6f);
        assertEquals(0.0f, HighlightPulse.at(PERIOD, PERIOD), 1.0e-6f);
        assertEquals(0.0f, HighlightPulse.at(PERIOD * 7, PERIOD), 1.0e-6f);
    }

    @Test
    @DisplayName("reaches the accent colour exactly once per cycle")
    void reachesAccentMidCycle() {
        assertEquals(1.0f, HighlightPulse.at(PERIOD / 2, PERIOD), 1.0e-6f);
    }

    @Test
    @DisplayName("stays inside the two colours it is mixing")
    void staysInRange() {
        for (long at = 0; at < PERIOD * 3; at += 7) {
            float value = HighlightPulse.at(at, PERIOD);
            assertTrue(value >= 0.0f && value <= 1.0f, "out of range at " + at + ": " + value);
        }
    }

    @Test
    @DisplayName("turns round smoothly rather than cornering, which is what reads as a blink")
    void hasNoCorners() {
        // A triangle wave's slope jumps by twice the ramp rate at each end. The
        // test of "no blink" is that the largest change between two adjacent
        // steps never spikes: on a raised cosine the step is smallest exactly
        // where a triangle wave's would be discontinuous.
        float atEnd = Math.abs(HighlightPulse.at(2, PERIOD) - HighlightPulse.at(0, PERIOD));
        float atQuarter = Math.abs(HighlightPulse.at(PERIOD / 4 + 2, PERIOD)
                - HighlightPulse.at(PERIOD / 4, PERIOD));

        assertTrue(atEnd < atQuarter,
                "the curve should be flattest where it turns round, not steepest");
    }

    @Test
    @DisplayName("spends most of the cycle near the resting colour")
    void restsMostOfTheTime() {
        int nearRest = 0;
        int samples = 0;
        for (long at = 0; at < PERIOD; at += 1) {
            if (HighlightPulse.at(at, PERIOD) < 0.25f) nearRest++;
            samples++;
        }
        assertTrue(nearRest > samples / 2,
                "expected the markers to read as yellow that swells, not as two "
                        + "colours alternating; near rest for " + nearRest + " of " + samples);
    }

    @Test
    @DisplayName("does not fold over on a clock that runs backwards")
    void handlesNegativeClock() {
        float value = HighlightPulse.at(-PERIOD / 4, PERIOD);
        assertTrue(value >= 0.0f && value <= 1.0f, "out of range: " + value);
        assertEquals(HighlightPulse.at(PERIOD * 3 / 4, PERIOD), value, 1.0e-6f);
    }

    @Test
    @DisplayName("a zero period holds still instead of dividing by it")
    void zeroPeriodHoldsStill() {
        assertEquals(0.0f, HighlightPulse.at(1234, 0), 1.0e-6f);
    }

    @Test
    @DisplayName("mixes between the two colours and clamps past either end")
    void mixesColours() {
        float[] rest = {1.0f, 0.0f, 0.0f};
        float[] accent = {0.0f, 0.0f, 1.0f};
        float[] into = new float[3];

        HighlightPulse.mix(rest, accent, 0.0f, into);
        assertArrayNear(new float[] {1.0f, 0.0f, 0.0f}, into);

        HighlightPulse.mix(rest, accent, 1.0f, into);
        assertArrayNear(new float[] {0.0f, 0.0f, 1.0f}, into);

        HighlightPulse.mix(rest, accent, 0.5f, into);
        assertArrayNear(new float[] {0.5f, 0.0f, 0.5f}, into);

        HighlightPulse.mix(rest, accent, 4.0f, into);
        assertArrayNear(new float[] {0.0f, 0.0f, 1.0f}, into);

        HighlightPulse.mix(rest, accent, -4.0f, into);
        assertArrayNear(new float[] {1.0f, 0.0f, 0.0f}, into);
    }

    private static void assertArrayNear(float[] expected, float[] actual) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 1.0e-6f, "component " + i);
        }
    }
}
