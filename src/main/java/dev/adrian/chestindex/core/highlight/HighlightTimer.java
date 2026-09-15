package dev.adrian.chestindex.core.highlight;

public final class HighlightTimer {

    private static final double MOVEMENT_EPSILON = 0.75;

    private final long baseDurationMs;
    private final long recedingGraceMs;
    private final long sampleIntervalMs;

    private boolean active;
    private double bestDistance;
    private double lastSampledDistance;
    private long lastSampleAt;
    private long expiresAt;

    public HighlightTimer(long baseDurationMs, long recedingGraceMs, long sampleIntervalMs) {
        this.baseDurationMs = baseDurationMs;
        this.recedingGraceMs = recedingGraceMs;
        this.sampleIntervalMs = sampleIntervalMs;
    }

    public static HighlightTimer defaults() {
        return new HighlightTimer(45_000L, 10_000L, 1_000L);
    }

    public void start(double distance, long nowMs) {
        active = true;
        bestDistance = distance;
        lastSampledDistance = distance;
        lastSampleAt = nowMs;
        expiresAt = nowMs + baseDurationMs;
    }

    public void clear() {
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public long remainingMs(long nowMs) {
        return active ? Math.max(0L, expiresAt - nowMs) : 0L;
    }

    public boolean update(double distance, long nowMs) {
        if (!active) return false;

        if (nowMs - lastSampleAt >= sampleIntervalMs) {
            double delta = lastSampledDistance - distance;
            if (distance < bestDistance - MOVEMENT_EPSILON) {
                bestDistance = distance;
                expiresAt = nowMs + baseDurationMs;
            } else if (delta < -MOVEMENT_EPSILON) {
                expiresAt = Math.min(expiresAt, nowMs + recedingGraceMs);
            }
            lastSampledDistance = distance;
            lastSampleAt = nowMs;
        }

        if (nowMs >= expiresAt) {
            active = false;
            return false;
        }
        return true;
    }
}
