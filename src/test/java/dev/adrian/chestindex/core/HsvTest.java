package dev.adrian.chestindex.core;

import dev.adrian.chestindex.core.util.Hsv;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        assertRoundTrips(0xFF2BD0);
        assertRoundTrips(0xB478FF);
    }

    @Test
    void roundTripsAcrossTheWholeCube() {
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
        assertEquals(0xFF0000, Hsv.toRgb(0.0f, 1.0f, 1.0f));
        assertEquals(0xFF0000, Hsv.toRgb(1.0f, 1.0f, 1.0f));
        assertEquals(0xFF0000, Hsv.toRgb(0.99999f, 1.0f, 1.0f));
        assertEquals(0xFF0000, Hsv.toRgb(2.0f, 1.0f, 1.0f));
        assertEquals(0xFF0000, Hsv.toRgb(-1.0f, 1.0f, 1.0f));
    }

    @Test
    void greyHasNoHueAndBlackHasNoSaturation() {
        float[] grey = Hsv.toHsv(0x808080);
        assertEquals(0.0f, grey[1], 1e-6);

        float[] black = Hsv.toHsv(0x000000);
        assertEquals(0.0f, black[1], 1e-6);
        assertEquals(0.0f, black[2], 1e-6);
    }

    @Test
    void outOfRangeSaturationAndValueAreClampedRatherThanWrapped() {
        assertEquals(0xFFFFFF, Hsv.toRgb(0.5f, -0.5f, 2.0f));
        assertEquals(0x000000, Hsv.toRgb(0.5f, 1.0f, -1.0f));
    }
}
