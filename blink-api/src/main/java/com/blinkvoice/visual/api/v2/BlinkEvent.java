package com.blinkvoice.visual.api.v2;

/** Immutable classified blink event. */
public final class BlinkEvent {
    private final BlinkEventType type;
    private final long startTimeMs;
    private final long endTimeMs;
    private final double confidence;

    public BlinkEvent(BlinkEventType type, long startTimeMs, long endTimeMs, double confidence) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (startTimeMs < 0L || endTimeMs < startTimeMs) {
            throw new IllegalArgumentException("invalid event timestamps");
        }
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)
                || confidence < 0d || confidence > 1d) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
        this.type = type;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.confidence = confidence;
    }

    public BlinkEventType getType() {
        return type;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getEndTimeMs() {
        return endTimeMs;
    }

    public long getDurationMs() {
        return endTimeMs - startTimeMs;
    }

    public double getConfidence() {
        return confidence;
    }
}
