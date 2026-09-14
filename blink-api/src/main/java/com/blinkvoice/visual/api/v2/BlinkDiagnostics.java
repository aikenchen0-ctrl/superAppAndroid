package com.blinkvoice.visual.api.v2;

/** Immutable, model-neutral diagnostics for one processed frame. */
public final class BlinkDiagnostics {
    private final long timestampMs;
    private final long processingDurationMs;
    private final boolean facePresent;
    private final double faceConfidence;
    private final double leftEyeOpenness;
    private final double rightEyeOpenness;
    private final long droppedFrameCount;

    public BlinkDiagnostics(
            long timestampMs,
            long processingDurationMs,
            boolean facePresent,
            double faceConfidence,
            double leftEyeOpenness,
            double rightEyeOpenness,
            long droppedFrameCount
    ) {
        if (timestampMs < 0L || processingDurationMs < 0L || droppedFrameCount < 0L) {
            throw new IllegalArgumentException("invalid diagnostic timing");
        }
        validateMetric("faceConfidence", faceConfidence);
        if (facePresent) {
            validateMetric("leftEyeOpenness", leftEyeOpenness);
            validateMetric("rightEyeOpenness", rightEyeOpenness);
        }
        this.timestampMs = timestampMs;
        this.processingDurationMs = processingDurationMs;
        this.facePresent = facePresent;
        this.faceConfidence = faceConfidence;
        this.leftEyeOpenness = leftEyeOpenness;
        this.rightEyeOpenness = rightEyeOpenness;
        this.droppedFrameCount = droppedFrameCount;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public long getProcessingDurationMs() {
        return processingDurationMs;
    }

    public boolean isFacePresent() {
        return facePresent;
    }

    public double getFaceConfidence() {
        return faceConfidence;
    }

    public double getLeftEyeOpenness() {
        return leftEyeOpenness;
    }

    public double getRightEyeOpenness() {
        return rightEyeOpenness;
    }

    public long getDroppedFrameCount() {
        return droppedFrameCount;
    }

    private static void validateMetric(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
