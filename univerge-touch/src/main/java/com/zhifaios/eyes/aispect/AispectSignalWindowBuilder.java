package com.zhifaios.eyes.aispect;

import java.util.ArrayList;
import java.util.List;

public final class AispectSignalWindowBuilder {
    static final int MIN_FRAME_INDEX = -5;
    static final int MAX_FRAME_INDEX = 15;
    private static final double DEFAULT_FALLBACK_SAMPLE_RATE_HZ = 120.0;

    public static final class Config {
        public int preFrames = 5;
        public int postFrames = 9;
        public double fallbackSampleRateHz = 120.0;
    }

    public static final class Window {
        public final AispectModels.ImpactFrame[] frames;
        public final int firstFrameIndex;
        public final double anchorTimestampSeconds;
        public final double sampleRateHz;
        public final double maxAbsDelta;
        public final int availableFrameCount;

        Window(
                AispectModels.ImpactFrame[] frames,
                int firstFrameIndex,
                double anchorTimestampSeconds,
                double sampleRateHz,
                double maxAbsDelta,
                int availableFrameCount
        ) {
            this.frames = frames;
            this.firstFrameIndex = firstFrameIndex;
            this.anchorTimestampSeconds = anchorTimestampSeconds;
            this.sampleRateHz = sampleRateHz;
            this.maxAbsDelta = maxAbsDelta;
            this.availableFrameCount = availableFrameCount;
        }
    }

    private final Config config = new Config();

    public Config config() {
        return config;
    }

    public Window build(List<AispectModels.ImpactFrame> sourceFrames, double anchorTimestampSeconds, double sampleRateHz) {
        int preFrames = Math.min(5, Math.max(0, config.preFrames));
        int postFrames = Math.min(15, Math.max(0, config.postFrames));
        int total = preFrames + postFrames + 1;
        AispectModels.ImpactFrame[] frames = new AispectModels.ImpactFrame[total];
        for (int i = 0; i < total; i++) {
            frames[i] = AispectModels.ImpactFrame.zero();
        }
        double safeRate = safeSampleRate(sampleRateHz);
        if (sourceFrames == null || sourceFrames.isEmpty()) {
            return new Window(frames, -preFrames, anchorTimestampSeconds, safeRate, 0, 0);
        }

        int referenceIndex = nearestFrameIndex(sourceFrames, anchorTimestampSeconds);
        double referenceTimestamp = sourceFrames.get(referenceIndex).timestampSeconds;
        double maxAbsDelta = 0;
        int availableFrameCount = 0;
        for (int offset = -preFrames; offset <= postFrames; offset++) {
            int absoluteIndex = referenceIndex + offset;
            int destinationIndex = offset + preFrames;
            if (absoluteIndex >= 0 && absoluteIndex < sourceFrames.size()) {
                AispectModels.ImpactFrame source = sourceFrames.get(absoluteIndex);
                frames[destinationIndex] = withAnchorTime(source, source.timestampSeconds - referenceTimestamp);
                availableFrameCount += 1;
            } else {
                double relativeTimestamp = offset / safeRate;
                frames[destinationIndex] = new AispectModels.ImpactFrame(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, relativeTimestamp, relativeTimestamp, referenceTimestamp + relativeTimestamp);
            }
            maxAbsDelta = Math.max(maxAbsDelta, Math.abs(frames[destinationIndex].delta));
        }
        return new Window(frames, -preFrames, anchorTimestampSeconds, safeRate, maxAbsDelta, availableFrameCount);
    }

    public List<AispectModels.ImpactFrame> copyWindowFrames(Window window) {
        ArrayList<AispectModels.ImpactFrame> copy = new ArrayList<>();
        for (AispectModels.ImpactFrame frame : window.frames) {
            copy.add(frame);
        }
        return copy;
    }

    private static int nearestFrameIndex(List<AispectModels.ImpactFrame> frames, double anchorTimestampSeconds) {
        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < frames.size(); i++) {
            double distance = Math.abs(frames.get(i).timestampSeconds - anchorTimestampSeconds);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static AispectModels.ImpactFrame withAnchorTime(AispectModels.ImpactFrame source, double timeSinceAnchor) {
        return new AispectModels.ImpactFrame(
                source.delta,
                source.x,
                source.y,
                source.z,
                source.rotationRateX,
                source.rotationRateY,
                source.rotationRateZ,
                source.gravityX,
                source.gravityY,
                source.gravityZ,
                source.xNorm,
                source.yNorm,
                source.timeSinceTouchDown,
                timeSinceAnchor,
                source.timestampSeconds,
                source.sensorTimestampElapsedRealtimeNanos,
                source.receivedElapsedRealtimeNanos
        );
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private double safeSampleRate(double sampleRateHz) {
        if (isFinite(sampleRateHz) && sampleRateHz > 0) {
            return sampleRateHz;
        }
        if (isFinite(config.fallbackSampleRateHz) && config.fallbackSampleRateHz > 0) {
            return config.fallbackSampleRateHz;
        }
        return DEFAULT_FALLBACK_SAMPLE_RATE_HZ;
    }

    private static boolean isValidFrame(AispectModels.ImpactFrame frame) {
        return frame != null
                && isFinite(frame.delta)
                && isFinite(frame.x)
                && isFinite(frame.y)
                && isFinite(frame.z)
                && isFinite(frame.rotationRateX)
                && isFinite(frame.rotationRateY)
                && isFinite(frame.rotationRateZ)
                && isFinite(frame.gravityX)
                && isFinite(frame.gravityY)
                && isFinite(frame.gravityZ)
                && isFinite(frame.xNorm)
                && isFinite(frame.yNorm)
                && isFinite(frame.timeSinceTouchDown)
                && isFinite(frame.timeSinceAnchor)
                && isFinite(frame.timestampSeconds);
    }
}
