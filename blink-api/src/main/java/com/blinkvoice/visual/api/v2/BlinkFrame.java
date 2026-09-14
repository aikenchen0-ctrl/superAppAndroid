package com.blinkvoice.visual.api.v2;

/**
 * A model-neutral observation submitted to a {@link BlinkSession}.
 *
 * <p>The host owns frame acquisition and must submit frames in timestamp order
 * from one serialized producer. Timestamps use a single monotonic time base
 * chosen by the host. No image buffer or platform object crosses this boundary.</p>
 */
public final class BlinkFrame {
    private final long timestampMs;
    private final boolean facePresent;
    private final double leftEyeOpenness;
    private final double rightEyeOpenness;
    private final double faceConfidence;

    public BlinkFrame(
            long timestampMs,
            boolean facePresent,
            double leftEyeOpenness,
            double rightEyeOpenness,
            double faceConfidence
    ) {
        if (timestampMs < 0L) {
            throw new IllegalArgumentException("timestampMs must be non-negative");
        }
        validateMetric("faceConfidence", faceConfidence);
        if (facePresent) {
            validateMetric("leftEyeOpenness", leftEyeOpenness);
            validateMetric("rightEyeOpenness", rightEyeOpenness);
        }
        this.timestampMs = timestampMs;
        this.facePresent = facePresent;
        this.leftEyeOpenness = leftEyeOpenness;
        this.rightEyeOpenness = rightEyeOpenness;
        this.faceConfidence = faceConfidence;
    }

    public static BlinkFrame noFace(long timestampMs) {
        return new BlinkFrame(timestampMs, false, Double.NaN, Double.NaN, 0d);
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public boolean isFacePresent() {
        return facePresent;
    }

    public double getLeftEyeOpenness() {
        return leftEyeOpenness;
    }

    public double getRightEyeOpenness() {
        return rightEyeOpenness;
    }

    public double getFaceConfidence() {
        return faceConfidence;
    }

    private static void validateMetric(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
