package dev.adrian.chestindex.core.util;

/**
 * Conversion between packed {@code 0xRRGGBB} and hue/saturation/value.
 *
 * <p>Lives in {@code core} because it is arithmetic with a lot of corners and
 * no game in it: the sector wraparound, grey having no hue, and black having no
 * hue <em>or</em> saturation are all easy to get subtly wrong and impossible to
 * notice by looking at a colour picker, where being a few degrees out just
 * looks like a colour.
 *
 * <p>Written out rather than borrowed. {@code java.awt.Color} is not on the
 * client's module path, and Minecraft has no equivalent that is named the same
 * on both target versions.
 */
public final class Hsv {

    private Hsv() {}

    /**
     * Hue/saturation/value to packed {@code 0xRRGGBB}.
     *
     * @param h hue, wrapped into [0,1) so 1.0 is red again rather than an error
     * @param s saturation in [0,1]
     * @param v value in [0,1]
     */
    public static int toRgb(float h, float s, float v) {
        s = clamp01(s);
        v = clamp01(v);

        float chroma = v * s;
        float sector = (h - (float) Math.floor(h)) * 6.0f;
        float second = chroma * (1 - Math.abs(sector % 2 - 1));
        float base = v - chroma;

        float r;
        float g;
        float b;
        switch ((int) sector) {
            case 0 -> { r = chroma; g = second; b = 0; }
            case 1 -> { r = second; g = chroma; b = 0; }
            case 2 -> { r = 0; g = chroma; b = second; }
            case 3 -> { r = 0; g = second; b = chroma; }
            case 4 -> { r = second; g = 0; b = chroma; }
            // Also catches sector 6, which floating point can produce for a hue
            // a hair under 1.0.
            default -> { r = chroma; g = 0; b = second; }
        }
        return (round255(r + base) << 16) | (round255(g + base) << 8) | round255(b + base);
    }

    /**
     * Packed {@code 0xRRGGBB} to {@code {hue, saturation, value}}.
     *
     * <p>Grey reports a hue of zero because it genuinely has none - the caller
     * decides whether to keep the hue it already had, which is what a picker
     * should do when the player drags into the white edge.
     */
    public static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float chroma = max - min;

        float h;
        if (chroma == 0) {
            h = 0;
        } else if (max == r) {
            h = ((g - b) / chroma % 6) / 6.0f;
        } else if (max == g) {
            h = ((b - r) / chroma + 2) / 6.0f;
        } else {
            h = ((r - g) / chroma + 4) / 6.0f;
        }
        if (h < 0) h += 1.0f;

        return new float[] {h, max == 0 ? 0 : chroma / max, max};
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static int round255(float component) {
        int scaled = Math.round(component * 255.0f);
        return scaled < 0 ? 0 : Math.min(scaled, 255);
    }
}
