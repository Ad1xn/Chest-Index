package dev.adrian.chestindex.core.highlight;

/**
 * How far a marker has drifted from its resting colour, at a moment in time.
 *
 * <p>The markers breathe: every one that is not the nearest sits at its resting
 * colour, swells towards the accent colour, and returns. The nearest does not -
 * it holds the accent colour outright, which is what tells it apart from the
 * rest without needing a second shape or a label.
 *
 * <p>Two things this is careful about, both of which were wrong when it was a
 * straight triangle wave:
 *
 * <ul>
 *   <li><b>No corners.</b> A linear ramp turns round instantly at each end, and
 *       the eye reads that instant as a blink however slow the ramp was. A
 *       raised cosine arrives at both ends with zero slope, so the colour
 *       settles into yellow and into purple rather than bouncing off them.
 *   <li><b>It rests at the resting colour.</b> An even oscillation spends as
 *       much time purple as yellow, which does not read as yellow that swells -
 *       it reads as two colours alternating, and then there is no resting
 *       colour at all. Biasing the curve keeps the markers yellow most of the
 *       cycle and lets the purple pass through.
 * </ul>
 *
 * <p>Pure arithmetic with no game types, so the curve can be checked against
 * its own claims rather than eyeballed in-game.
 */
public final class HighlightPulse {

    private HighlightPulse() {}

    /** One breath, in milliseconds. Slow enough to read as a swell, not a flash. */
    public static final long PERIOD_MS = 2600L;

    /**
     * How hard the curve is pushed back towards the resting colour.
     *
     * <p>Above one, so the low end is stretched and the high end is brief. At
     * three the marker is within a quarter of the way to the accent for very
     * nearly six tenths of every cycle, and still reaches the accent outright
     * at the top of each one - the swell is shortened, not cut off.
     */
    private static final double REST_BIAS = 3.0;

    /**
     * How far towards the accent colour a marker is, 0 to 1.
     *
     * @param nowMs any monotonically increasing clock
     */
    public static float at(long nowMs) {
        return at(nowMs, PERIOD_MS);
    }

    /** As {@link #at(long)}, with the period named - which is what the tests vary. */
    public static float at(long nowMs, long periodMs) {
        if (periodMs <= 0) return 0.0f;

        // floorMod, not %, so a negative clock does not fold the curve over.
        double phase = Math.floorMod(nowMs, periodMs) / (double) periodMs;

        // Zero slope at both ends: 0 at phase 0, 1 at phase 0.5, 0 again at 1.
        double wave = (1.0 - Math.cos(phase * 2.0 * Math.PI)) / 2.0;

        return (float) Math.pow(wave, REST_BIAS);
    }

    /**
     * Fades one colour into another, in place.
     *
     * <p>Writes into a caller-owned array because this runs per marker per
     * frame, and a three-float allocation there is a few thousand a second of
     * garbage to say something about a colour.
     *
     * @param rest   the colour at {@code amount} 0
     * @param accent the colour at {@code amount} 1
     * @param into   where the result goes; may be neither input
     */
    public static void mix(float[] rest, float[] accent, float amount, float[] into) {
        float t = amount < 0.0f ? 0.0f : Math.min(1.0f, amount);
        for (int i = 0; i < 3; i++) {
            into[i] = rest[i] + (accent[i] - rest[i]) * t;
        }
    }
}
