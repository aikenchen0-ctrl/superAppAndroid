package com.blinkvoice.visual.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class BlinkCaptureOptions {
    private final float earCloseThreshold;
    private final float earOpenThreshold;
    private final long doubleBlinkWindowMs;
    private final long longCloseMinMs;
    private final long minShortBlinkMs;
    private final long maxShortBlinkMs;
    private final long noFaceResetMs;
    private final boolean autoFinishOnEvent;
    private final Set<BlinkEventType> eventTypes;
    private final boolean debugLoggingEnabled;
    private final boolean debugOverlayEnabled;

    private BlinkCaptureOptions(Builder builder) {
        this.earCloseThreshold = builder.earCloseThreshold;
        this.earOpenThreshold = builder.earOpenThreshold;
        this.doubleBlinkWindowMs = builder.doubleBlinkWindowMs;
        this.longCloseMinMs = builder.longCloseMinMs;
        this.minShortBlinkMs = builder.minShortBlinkMs;
        this.maxShortBlinkMs = builder.maxShortBlinkMs;
        this.noFaceResetMs = builder.noFaceResetMs;
        this.autoFinishOnEvent = builder.autoFinishOnEvent;
        this.eventTypes = Collections.unmodifiableSet(EnumSet.copyOf(builder.eventTypes));
        this.debugLoggingEnabled = builder.debugLoggingEnabled;
        this.debugOverlayEnabled = builder.debugOverlayEnabled;
    }

    public float getEarCloseThreshold() {
        return earCloseThreshold;
    }

    public float getEarOpenThreshold() {
        return earOpenThreshold;
    }

    public long getDoubleBlinkWindowMs() {
        return doubleBlinkWindowMs;
    }

    public long getLongCloseMinMs() {
        return longCloseMinMs;
    }

    public long getMinShortBlinkMs() {
        return minShortBlinkMs;
    }

    public long getMaxShortBlinkMs() {
        return maxShortBlinkMs;
    }

    public long getNoFaceResetMs() {
        return noFaceResetMs;
    }

    public boolean isAutoFinishOnEvent() {
        return autoFinishOnEvent;
    }

    public Set<BlinkEventType> getEventTypes() {
        return eventTypes;
    }

    public boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled;
    }

    public boolean isDebugOverlayEnabled() {
        return debugOverlayEnabled;
    }

    public static final class Builder {
        private float earCloseThreshold = 0.22f;
        private float earOpenThreshold = 0.25f;
        private long doubleBlinkWindowMs = 650L;
        private long longCloseMinMs = 500L;
        private long minShortBlinkMs = 30L;
        private long maxShortBlinkMs = 260L;
        private long noFaceResetMs = 500L;
        private boolean autoFinishOnEvent = true;
        private Set<BlinkEventType> eventTypes = EnumSet.allOf(BlinkEventType.class);
        // 调试日志默认关闭，避免 SDK 正常接入时刷屏或影响相机帧处理性能。
        private boolean debugLoggingEnabled = false;
        // 手机端诊断面板默认开启，方便真机测试时直接看关键识别链路。
        private boolean debugOverlayEnabled = true;

        public Builder setEarCloseThreshold(float earCloseThreshold) {
            this.earCloseThreshold = earCloseThreshold;
            return this;
        }

        public Builder setEarOpenThreshold(float earOpenThreshold) {
            this.earOpenThreshold = earOpenThreshold;
            return this;
        }

        public Builder setDoubleBlinkWindowMs(long doubleBlinkWindowMs) {
            this.doubleBlinkWindowMs = doubleBlinkWindowMs;
            return this;
        }

        public Builder setLongCloseMinMs(long longCloseMinMs) {
            this.longCloseMinMs = longCloseMinMs;
            return this;
        }

        public Builder setMinShortBlinkMs(long minShortBlinkMs) {
            this.minShortBlinkMs = minShortBlinkMs;
            return this;
        }

        public Builder setMaxShortBlinkMs(long maxShortBlinkMs) {
            this.maxShortBlinkMs = maxShortBlinkMs;
            return this;
        }

        public Builder setNoFaceResetMs(long noFaceResetMs) {
            this.noFaceResetMs = noFaceResetMs;
            return this;
        }

        public Builder setAutoFinishOnEvent(boolean autoFinishOnEvent) {
            this.autoFinishOnEvent = autoFinishOnEvent;
            return this;
        }

        public Builder setEventTypes(Set<BlinkEventType> eventTypes) {
            if (eventTypes == null || eventTypes.isEmpty()) {
                this.eventTypes = EnumSet.allOf(BlinkEventType.class);
            } else {
                this.eventTypes = EnumSet.copyOf(eventTypes);
            }
            return this;
        }

        public Builder setDebugLoggingEnabled(boolean debugLoggingEnabled) {
            this.debugLoggingEnabled = debugLoggingEnabled;
            return this;
        }

        public Builder setDebugOverlayEnabled(boolean debugOverlayEnabled) {
            this.debugOverlayEnabled = debugOverlayEnabled;
            return this;
        }

        public BlinkCaptureOptions build() {
            return new BlinkCaptureOptions(this);
        }
    }
}
