package com.blinkvoice.visual.api;

public final class BlinkCaptureResult {
    private final BlinkEventType eventType;
    private final long startTimeMs;
    private final long endTimeMs;
    private final long durationMs;
    private final float confidence;

    public BlinkCaptureResult(
            BlinkEventType eventType,
            long startTimeMs,
            long endTimeMs,
            long durationMs,
            float confidence
    ) {
        this.eventType = eventType;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.durationMs = durationMs;
        this.confidence = confidence;
    }

    public BlinkEventType getEventType() {
        return eventType;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getEndTimeMs() {
        return endTimeMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public float getConfidence() {
        return confidence;
    }
}
