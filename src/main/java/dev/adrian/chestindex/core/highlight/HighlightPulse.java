package dev.adrian.chestindex.core.highlight;

public final class HighlightPulse {

    private HighlightPulse() {}

    public static final long PERIOD_MS = 2600L;

    private static final double REST_BIAS = 3.0;

    public static float at(long nowMs) {
        return at(nowMs, PERIOD_MS);
    }

    public static float at(long nowMs, long periodMs) {
        if (periodMs <= 0) return 0.0f;

        double phase = Math.floorMod(nowMs, periodMs) / (double) periodMs;

        double wave = (1.0 - Math.cos(phase * 2.0 * Math.PI)) / 2.0;

        return (float) Math.pow(wave, REST_BIAS);
    }

    public static void mix(float[] rest, float[] accent, float amount, float[] into) {
        float t = amount < 0.0f ? 0.0f : Math.min(1.0f, amount);
        for (int i = 0; i < 3; i++) {
            into[i] = rest[i] + (accent[i] - rest[i]) * t;
        }
    }
}
