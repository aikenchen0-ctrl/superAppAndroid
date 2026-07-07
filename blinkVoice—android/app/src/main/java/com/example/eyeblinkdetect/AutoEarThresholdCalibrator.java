package com.example.eyeblinkdetect;

import java.util.Locale;

public final class AutoEarThresholdCalibrator {
    private static final float DEFAULT_CLOSE_THRESHOLD = 0.22f;
    private static final float DEFAULT_OPEN_THRESHOLD = 0.25f;
    private static final float MIN_VALID_EAR = 0.05f;
    private static final float MAX_VALID_EAR = 0.60f;

    private float openBaseline = 0f;
    private float closedMinimum = Float.MAX_VALUE;
    private boolean hasOpenBaseline = false;
    private boolean hasClosedSample = false;

    public void addSample(float averageEar, boolean closedByDemoThreshold) {
        if (averageEar < MIN_VALID_EAR || averageEar > MAX_VALID_EAR) {
            return;
        }

        if (closedByDemoThreshold) {
            closedMinimum = Math.min(closedMinimum, averageEar);
            hasClosedSample = true;
        } else {
            openBaseline = Math.max(openBaseline, averageEar);
            hasOpenBaseline = true;
        }
    }

    public Recommendation getRecommendation() {
        if (!hasOpenBaseline) {
            return new Recommendation(
                    DEFAULT_CLOSE_THRESHOLD,
                    DEFAULT_OPEN_THRESHOLD,
                    0f,
                    0f,
                    false,
                    false,
                    "DEFAULT"
            );
        }

        if (!hasClosedSample || closedMinimum >= openBaseline) {
            float closeThreshold = clamp(openBaseline * 0.75f, 0.10f, openBaseline - 0.015f);
            float openThreshold = clamp(openBaseline * 0.85f, closeThreshold + 0.015f, 0.40f);
            return new Recommendation(
                    closeThreshold,
                    openThreshold,
                    openBaseline,
                    hasClosedSample ? closedMinimum : 0f,
                    true,
                    hasClosedSample,
                    "OPEN_ONLY"
            );
        }

        float gap = openBaseline - closedMinimum;
        float closeThreshold = closedMinimum + gap * 0.55f;
        float openThreshold = Math.min(openBaseline - 0.005f, closeThreshold + 0.025f);
        if (openThreshold <= closeThreshold) {
            openThreshold = closeThreshold + 0.015f;
        }
        return new Recommendation(
                closeThreshold,
                openThreshold,
                openBaseline,
                closedMinimum,
                true,
                true,
                "OPEN_CLOSED_GAP"
        );
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    public static final class Recommendation {
        private final float closeThreshold;
        private final float openThreshold;
        private final float openBaseline;
        private final float closedMinimum;
        private final boolean hasOpenBaseline;
        private final boolean hasClosedSample;
        private final String mode;

        Recommendation(
                float closeThreshold,
                float openThreshold,
                float openBaseline,
                float closedMinimum,
                boolean hasOpenBaseline,
                boolean hasClosedSample,
                String mode
        ) {
            this.closeThreshold = closeThreshold;
            this.openThreshold = openThreshold;
            this.openBaseline = openBaseline;
            this.closedMinimum = closedMinimum;
            this.hasOpenBaseline = hasOpenBaseline;
            this.hasClosedSample = hasClosedSample;
            this.mode = mode;
        }

        public float getCloseThreshold() {
            return closeThreshold;
        }

        public float getOpenThreshold() {
            return openThreshold;
        }

        public float getOpenBaseline() {
            return openBaseline;
        }

        public float getClosedMinimum() {
            return closedMinimum;
        }

        public boolean hasOpenBaseline() {
            return hasOpenBaseline;
        }

        public boolean hasClosedSample() {
            return hasClosedSample;
        }

        public String getMode() {
            return mode;
        }

        public String toDisplayText() {
            return String.format(
                    Locale.US,
                    "Auto EAR: %s | open=%.3f closed=%.3f | close<%.3f open>%.3f",
                    mode,
                    openBaseline,
                    closedMinimum,
                    closeThreshold,
                    openThreshold
            );
        }
    }
}
