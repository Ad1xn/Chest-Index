package dev.adrian.chesttracker.core;

import dev.adrian.chesttracker.core.util.Hsv;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The colour picker's arithmetic.
 *
 * <p>Worth pinning down because every failure mode here looks like a colour.
 * A hue a few degrees out, a sector boundary that wraps the wrong way, a
 * round-trip that drifts a shade darker every time the screen is reopened -
 * none of them look like bugs, they look like the colour you picked.
 */
class HsvTest {

    private static void assertRoundTrips(int rgb) {
        float[] hsv = Hsv.toHsv(rgb);
        assertEquals(rgb, Hsv.toRgb(hsv[0], hsv[1], hsv[2]),
                () -> "round trip lost " + String.format("#%06X", rgb));
    }

    @Test
    void everyPrimaryAndCornerSurvivesARoundTrip() {
        for (int rgb : new int[] {
                0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF,
                0xFFFF00, 0x00FFFF, 0xFF00FF, 0x808080, 0x7F7F7F}) {
            assertRoundTrips(rgb);
        }
    }

    @Test
    void theShippedDefaultsSurviveARoundTrip() {
        // These are what the config ships with, so they are what the picker
        // shows the first time it is opened. If they drifted, the marker colour
        // would change simply for having looked at it.
        assertRoundTrips(0xFF2BD0);
        assertRoundTrips(0xB478FF);
    }

    @Test
    void roundTripsAcrossTheWholeCube() {
        // Every sector, including the boundaries between them, which is where
        // an off-by-one in the sector arithmetic hides.
        int checked = 0;
        for (int r = 0; r <= 255; r += 17) {
            for (int g = 0; g <= 255; g += 17) {
                for (int b = 0; b <= 255; b += 17) {
                    assertRoundTrips((r << 16) | (g << 8) | b);
                    checked++;
                }
            }
        }
        assertEquals(16 * 16 * 16, checked);
    }

    @Test
    void hueWrapsRatherThanFallingOffTheEnd() {
        // The strip runs to exactly 1.0 at its right-hand end, and floating
        // point lands a hair either side of it. Both must be red, not black -
        // an unhandled sector 6 would index past the switch.
        assertEquals(0xFF0000, Hsv.toRgb(0.0f, 1.0f, 1.0f));
        assertEquals(0xFF0000, Hsv.toRgb(1.0f, 1.0f, 1.0f));
        assertEquals(0xFF0000, Hsv.toRgb(0.99999f, 1.0f, 1.0f));
        assertEquals(0xFF0000, Hsv.toRgb(2.0f, 1.0f, 1.0f));
        // Negative hues arrive from nothing in the UI, but wrapping is the only
        // sensible reading and a crash is not.
        assertEquals(0xFF0000, Hsv.toRgb(-1.0f, 1.0f, 1.0f));
    }

    @Test
    void greyHasNoHueAndBlackHasNoSaturation() {
        float[] grey = Hsv.toHsv(0x808080);
        assertEquals(0.0f, grey[1], 1e-6, "grey is unsaturated whatever its hue");

        float[] black = Hsv.toHsv(0x000000);
        assertEquals(0.0f, black[1], 1e-6);
        assertEquals(0.0f, black[2], 1e-6);
    }

    @Test
    void outOfRangeSaturationAndValueAreClampedRatherThanWrapped() {
        // The square's cursor is clamped before it gets here, but clamping in
        // one place only is how a value of 1.0000001 turns into black.
        assertEquals(0xFFFFFF, Hsv.toRgb(0.5f, -0.5f, 2.0f));
        assertEquals(0x000000, Hsv.toRgb(0.5f, 1.0f, -1.0f));
    }
}
