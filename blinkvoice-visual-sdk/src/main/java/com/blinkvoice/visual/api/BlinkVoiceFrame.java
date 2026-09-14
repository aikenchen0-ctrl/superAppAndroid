package com.blinkvoice.visual.api;

/**
 * 连续检测接口每一帧输出的实时状态。
 */
public final class BlinkVoiceFrame {
    private final boolean hasFace;
    private final boolean closed;
    private final float leftEla;
    private final float rightEla;
    private final long inferenceMs;
    private final int imageWidth;
    private final int imageHeight;
    private final int rotationDegrees;
    private final String phase;
    private final String lastReason;
    private final String lastEvent;
    private final long closedDurationMs;
    private final long pendingElapsedMs;

    public BlinkVoiceFrame(
            boolean hasFace,
            boolean closed,
            float leftEla,
            float rightEla,
            long inferenceMs,
            int imageWidth,
            int imageHeight,
            int rotationDegrees,
            String phase,
            String lastReason,
            String lastEvent,
            long closedDurationMs,
            long pendingElapsedMs
    ) {
        this.hasFace = hasFace;
        this.closed = closed;
        this.leftEla = leftEla;
        this.rightEla = rightEla;
        this.inferenceMs = inferenceMs;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.rotationDegrees = rotationDegrees;
        this.phase = phase;
        this.lastReason = lastReason;
        this.lastEvent = lastEvent;
        this.closedDurationMs = closedDurationMs;
        this.pendingElapsedMs = pendingElapsedMs;
    }

    public boolean hasFace() {
        return hasFace;
    }

    public boolean isClosed() {
        return closed;
    }

    public float getLeftEla() {
        return leftEla;
    }

    public float getRightEla() {
        return rightEla;
    }

    public float getAverageEla() {
        return (leftEla + rightEla) / 2f;
    }

    public long getInferenceMs() {
        return inferenceMs;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public int getRotationDegrees() {
        return rotationDegrees;
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

    public long getClosedDurationMs() {
        return closedDurationMs;
    }

    public long getPendingElapsedMs() {
        return pendingElapsedMs;
    }
}
