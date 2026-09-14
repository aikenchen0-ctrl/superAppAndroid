package com.blinkvoice.visual.api.v2;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable, model-independent configuration for a blink session.
 *
 * <p>Thresholds are normalized eye-openness values in the range {@code [0, 1]}.
 * The implementation may derive those values from any detector; the public
 * contract does not expose the detector or its model.</p>
 */
public final class BlinkOptions {
    private final double closedEyeThreshold;
    private final double openEyeThreshold;
    private final long doubleBlinkWindowMs;
    private final long longCloseMinMs;
    private final long minBlinkDurationMs;
    private final long maxBlinkDurationMs;
    private final long noFaceResetMs;
    private final boolean stopOnEvent;
    private final Set<BlinkEventType> eventTypes;

    private BlinkOptions(Builder builder) {
        this.closedEyeThreshold = builder.closedEyeThreshold;
        this.openEyeThreshold = builder.openEyeThreshold;
        this.doubleBlinkWindowMs = builder.doubleBlinkWindowMs;
        this.longCloseMinMs = builder.longCloseMinMs;
        this.minBlinkDurationMs = builder.minBlinkDurationMs;
        this.maxBlinkDurationMs = builder.maxBlinkDurationMs;
        this.noFaceResetMs = builder.noFaceResetMs;
        this.stopOnEvent = builder.stopOnEvent;
        this.eventTypes = Collections.unmodifiableSet(EnumSet.copyOf(builder.eventTypes));
    }

    public double getClosedEyeThreshold() {
        return closedEyeThreshold;
    }

    public double getOpenEyeThreshold() {
        return openEyeThreshold;
    }

    public long getDoubleBlinkWindowMs() {
        return doubleBlinkWindowMs;
    }

    public long getLongCloseMinMs() {
        return longCloseMinMs;
    }

    public long getMinBlinkDurationMs() {
        return minBlinkDurationMs;
    }

    public long getMaxBlinkDurationMs() {
        return maxBlinkDurationMs;
    }

    public long getNoFaceResetMs() {
        return noFaceResetMs;
    }

    public boolean isStopOnEvent() {
        return stopOnEvent;
    }

    public Set<BlinkEventType> getEventTypes() {
        return eventTypes;
    }

    public static final class Builder {
        private double closedEyeThreshold = 0.22d;
        private double openEyeThreshold = 0.25d;
        private long doubleBlinkWindowMs = 650L;
        private long longCloseMinMs = 500L;
        private long minBlinkDurationMs = 30L;
        private long maxBlinkDurationMs = 260L;
        private long noFaceResetMs = 500L;
        private boolean stopOnEvent = true;
        private Set<BlinkEventType> eventTypes = EnumSet.allOf(BlinkEventType.class);

        public Builder setClosedEyeThreshold(double value) {
            this.closedEyeThreshold = value;
            return this;
        }

        public Builder setOpenEyeThreshold(double value) {
            this.openEyeThreshold = value;
            return this;
        }

        public Builder setDoubleBlinkWindowMs(long value) {
            this.doubleBlinkWindowMs = value;
            return this;
        }

        public Builder setLongCloseMinMs(long value) {
            this.longCloseMinMs = value;
            return this;
        }

        public Builder setMinBlinkDurationMs(long value) {
            this.minBlinkDurationMs = value;
            return this;
        }

        public Builder setMaxBlinkDurationMs(long value) {
            this.maxBlinkDurationMs = value;
            return this;
        }

        public Builder setNoFaceResetMs(long value) {
            this.noFaceResetMs = value;
            return this;
        }

        public Builder setStopOnEvent(boolean value) {
            this.stopOnEvent = value;
            return this;
        }

        public Builder setEventTypes(Set<BlinkEventType> values) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("eventTypes must not be empty");
            }
            this.eventTypes = EnumSet.copyOf(values);
            return this;
        }

        public BlinkOptions build() {
            validateThreshold("closedEyeThreshold", closedEyeThreshold);
            validateThreshold("openEyeThreshold", openEyeThreshold);
            if (openEyeThreshold < closedEyeThreshold) {
                throw new IllegalArgumentException("openEyeThreshold must be >= closedEyeThreshold");
            }
            if (doubleBlinkWindowMs <= 0L || longCloseMinMs <= 0L
                    || minBlinkDurationMs < 0L || maxBlinkDurationMs < minBlinkDurationMs
                    || noFaceResetMs < 0L) {
                throw new IllegalArgumentException("invalid timing options");
            }
            return new BlinkOptions(this);
        }

        private static void validateThreshold(String name, double value) {
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0d || value > 1d) {
                throw new IllegalArgumentException(name + " must be in [0, 1]");
            }
        }
    }
}
