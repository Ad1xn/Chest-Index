package dev.adrian.chestindex.core;

import dev.adrian.chestindex.core.highlight.HighlightPulse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
class HighlightPulseTest {
    private static final long PERIOD = 1000L;

    @Test
    void restsAtCycleStart() {
        assertEquals(0.0f, HighlightPulse.at(0, PERIOD), 1.0e-6f);
        assertEquals(0.0f, HighlightPulse.at(PERIOD, PERIOD), 1.0e-6f);
        assertEquals(0.0f, HighlightPulse.at(PERIOD * 7, PERIOD), 1.0e-6f);
    }

    @Test
    void reachesAccentMidCycle() {
        assertEquals(1.0f, HighlightPulse.at(PERIOD / 2, PERIOD), 1.0e-6f);
    }

    @Test
    void staysInRange() {
        for (long at = 0; at < PERIOD * 3; at += 7) {
            float value = HighlightPulse.at(at, PERIOD);
            assertTrue(value >= 0.0f && value <= 1.0f, "out of range at " + at + ": " + value);
        }
    }

    @Test
    void hasNoCorners() {
        float atEnd = Math.abs(HighlightPulse.at(2, PERIOD) - HighlightPulse.at(0, PERIOD));
        float atQuarter = Math.abs(HighlightPulse.at(PERIOD / 4 + 2, PERIOD)
                - HighlightPulse.at(PERIOD / 4, PERIOD));

        assertTrue(atEnd < atQuarter);
    }

    @Test
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
    void handlesNegativeClock() {
        float value = HighlightPulse.at(-PERIOD / 4, PERIOD);
        assertTrue(value >= 0.0f && value <= 1.0f, "out of range: " + value);
        assertEquals(HighlightPulse.at(PERIOD * 3 / 4, PERIOD), value, 1.0e-6f);
    }

    @Test
    void zeroPeriodHoldsStill() {
        assertEquals(0.0f, HighlightPulse.at(1234, 0), 1.0e-6f);
    }

    @Test
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
