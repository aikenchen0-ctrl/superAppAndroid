package com.blinkvoice.visual.events;

public final class BlinkClassifierDebugSnapshot {
    private final String phase;
    private final String lastReason;
    private final String lastEvent;
    private final boolean hasSeenOpenEyes;
    private final boolean closed;
    private final long closedDurationMs;
    private final long pendingBlinkElapsedMs;

    BlinkClassifierDebugSnapshot(
            String phase,
            String lastReason,
            String lastEvent,
            boolean hasSeenOpenEyes,
            boolean closed,
            long closedDurationMs,
            long pendingBlinkElapsedMs
    ) {
        this.phase = phase;
        this.lastReason = lastReason;
        this.lastEvent = lastEvent;
        this.hasSeenOpenEyes = hasSeenOpenEyes;
        this.closed = closed;
        this.closedDurationMs = closedDurationMs;
        this.pendingBlinkElapsedMs = pendingBlinkElapsedMs;
    }

    public String getPhase() {
        return phase;
    }

    public String getLastReason() {
        return lastReason;
    }

    public String getLastEvent() {
        return lastEvent;
    }

    public boolean hasSeenOpenEyes() {
        return hasSeenOpenEyes;
    }

    public boolean isClosed() {
        return closed;
    }

    public long getClosedDurationMs() {
        return closedDurationMs;
    }

    public long getPendingBlinkElapsedMs() {
        return pendingBlinkElapsedMs;
    }
}
