package com.blinkvoice.visual.api.v2;

/** Immutable session state snapshot. */
public final class BlinkState {
    public enum Phase {
        IDLE,
        STARTING,
        RUNNING,
        WAITING_FOR_FACE,
        READY,
        EYES_CLOSED,
        STOPPING,
        STOPPED,
        ERROR,
        CLOSED
    }

    private final Phase phase;
    private final long timestampMs;
    private final boolean facePresent;
    private final BlinkEvent lastEvent;
    private final BlinkError lastError;

    public BlinkState(
            Phase phase,
            long timestampMs,
            boolean facePresent,
            BlinkEvent lastEvent,
            BlinkError lastError
    ) {
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }
        if (timestampMs < 0L) {
            throw new IllegalArgumentException("timestampMs must be non-negative");
        }
        this.phase = phase;
        this.timestampMs = timestampMs;
        this.facePresent = facePresent;
        this.lastEvent = lastEvent;
        this.lastError = lastError;
    }

    public static BlinkState idle() {
        return new BlinkState(Phase.IDLE, 0L, false, null, null);
    }

    public Phase getPhase() {
        return phase;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public boolean isFacePresent() {
        return facePresent;
    }

    public BlinkEvent getLastEvent() {
        return lastEvent;
    }

    public BlinkError getLastError() {
        return lastError;
    }
}
