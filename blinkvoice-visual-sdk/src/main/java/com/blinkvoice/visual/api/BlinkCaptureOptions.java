package com.blinkvoice.visual.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 保存动作识别规则参数，宿主 App 通过这些参数控制 ELA 闭眼阈值和事件判定时间窗口。
 */
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
    private final int maxAnalysisFps;

    /**
     * 从 Builder 读取最终参数，生成一次不可变的识别配置。
     */
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
        this.maxAnalysisFps = builder.maxAnalysisFps;
    }

    /**
     * 返回闭眼阈值，平均 ELA 低于该角度时开始认为眼睛闭合。
     */
    public float getEarCloseThreshold() {
        return earCloseThreshold;
    }

    /** ELA 命名别名；保留 Ear 命名以兼容早期 SDK 调用方。 */
    public float getElaCloseThreshold() {
        return earCloseThreshold;
    }

    /**
     * 返回睁眼阈值，平均 ELA 高于该角度时认为眼睛重新睁开。
     */
    public float getEarOpenThreshold() {
        return earOpenThreshold;
    }

    /** ELA 命名别名；保留 Ear 命名以兼容早期 SDK 调用方。 */
    public float getElaOpenThreshold() {
        return earOpenThreshold;
    }

    /**
     * 返回双眨总窗口，第一次闭眼开始到第二次睁眼结束必须落在该时间内。
     */
    public long getDoubleBlinkWindowMs() {
        return doubleBlinkWindowMs;
    }

    /**
     * 返回长闭眼最小时长，连续闭眼达到该值后立即输出长闭眼事件。
     */
    public long getLongCloseMinMs() {
        return longCloseMinMs;
    }

    /**
     * 返回短眨最小时长，低于该值的闭眼片段会被认为是抖动。
     */
    public long getMinShortBlinkMs() {
        return minShortBlinkMs;
    }

    /**
     * 返回短眨最大时长，高于该值且未达到长闭眼阈值时会进入灰区。
     */
    public long getMaxShortBlinkMs() {
        return maxShortBlinkMs;
    }

    /**
     * 返回无人脸重置时长，超过该值会清空状态机中间态。
     */
    public long getNoFaceResetMs() {
        return noFaceResetMs;
    }

    /**
     * 返回识别到目标事件后是否自动结束本次 SDK 调起流程。
     */
    public boolean isAutoFinishOnEvent() {
        return autoFinishOnEvent;
    }

    /**
     * 返回本次调起允许输出的动作事件类型集合。
     */
    public Set<BlinkEventType> getEventTypes() {
        return eventTypes;
    }

    /**
     * 返回是否开启动作检测链路调试日志。
     */
    public boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled;
    }

    /**
     * 返回是否在相机页面展示动作检测调试浮层。
     */
    public boolean isDebugOverlayEnabled() {
        return debugOverlayEnabled;
    }

    /**
     * 返回 SDK 分析帧率上限。0 表示不做 SDK 侧帧率限制。
     */
    public int getMaxAnalysisFps() {
        return maxAnalysisFps;
    }

    /**
     * 逐项设置动作识别参数，并在 build 时生成不可变配置。
     */
    public static final class Builder {
        private float earCloseThreshold = 10f;
        private float earOpenThreshold = 14f;
        private long doubleBlinkWindowMs = 650L;
        private long longCloseMinMs = 275L;
        private long minShortBlinkMs = 30L;
        private long maxShortBlinkMs = 250L;
        private long noFaceResetMs = 500L;
        private boolean autoFinishOnEvent = true;
        private Set<BlinkEventType> eventTypes = EnumSet.allOf(BlinkEventType.class);
        // 调试日志默认关闭，避免 SDK 正常接入时刷屏或影响相机帧处理性能。
        private boolean debugLoggingEnabled = false;
        // 手机端诊断面板默认开启，方便真机测试时直接看关键识别链路。
        private boolean debugOverlayEnabled = true;
        // 眨眼动作不需要满帧率推理，默认限制到 20fps 以降低发热和 Bitmap 转换压力。
        private int maxAnalysisFps = 20;

        /**
         * 设置闭眼阈值，平均 ELA 低于该角度时开始认为眼睛闭合。
         */
        public Builder setEarCloseThreshold(float earCloseThreshold) {
            this.earCloseThreshold = earCloseThreshold;
            return this;
        }

        public Builder setElaCloseThreshold(float elaCloseThreshold) {
            return setEarCloseThreshold(elaCloseThreshold);
        }

        /**
         * 设置睁眼阈值，平均 ELA 高于该角度时认为眼睛重新睁开。
         */
        public Builder setEarOpenThreshold(float earOpenThreshold) {
            this.earOpenThreshold = earOpenThreshold;
            return this;
        }

        public Builder setElaOpenThreshold(float elaOpenThreshold) {
            return setEarOpenThreshold(elaOpenThreshold);
        }

        /**
         * 设置双眨总窗口，第一次闭眼开始到第二次睁眼结束必须落在该时间内。
         */
        public Builder setDoubleBlinkWindowMs(long doubleBlinkWindowMs) {
            this.doubleBlinkWindowMs = doubleBlinkWindowMs;
            return this;
        }

        /**
         * 设置长闭眼最小时长，连续闭眼达到该值后立即输出长闭眼事件。
         */
        public Builder setLongCloseMinMs(long longCloseMinMs) {
            this.longCloseMinMs = longCloseMinMs;
            return this;
        }

        /**
         * 设置短眨最小时长，低于该值的闭眼片段会被认为是抖动。
         */
        public Builder setMinShortBlinkMs(long minShortBlinkMs) {
            this.minShortBlinkMs = minShortBlinkMs;
            return this;
        }

        /**
         * 设置短眨最大时长，高于该值且未达到长闭眼阈值时会进入灰区。
         */
        public Builder setMaxShortBlinkMs(long maxShortBlinkMs) {
            this.maxShortBlinkMs = maxShortBlinkMs;
            return this;
        }

        /**
         * 设置无人脸重置时长，超过该值会清空状态机中间态。
         */
        public Builder setNoFaceResetMs(long noFaceResetMs) {
            this.noFaceResetMs = noFaceResetMs;
            return this;
        }

        /**
         * 设置识别到目标事件后是否自动结束本次 SDK 调起流程。
         */
        public Builder setAutoFinishOnEvent(boolean autoFinishOnEvent) {
            this.autoFinishOnEvent = autoFinishOnEvent;
            return this;
        }

        /**
         * 设置本次调起允许输出的动作事件类型，空集合会回退为全部事件。
         */
        public Builder setEventTypes(Set<BlinkEventType> eventTypes) {
            if (eventTypes == null || eventTypes.isEmpty()) {
                this.eventTypes = EnumSet.allOf(BlinkEventType.class);
            } else {
                this.eventTypes = EnumSet.copyOf(eventTypes);
            }
            return this;
        }

        /**
         * 设置是否开启动作检测链路调试日志。
         */
        public Builder setDebugLoggingEnabled(boolean debugLoggingEnabled) {
            this.debugLoggingEnabled = debugLoggingEnabled;
            return this;
        }

        /**
         * 设置是否在相机页面展示动作检测调试浮层。
         */
        public Builder setDebugOverlayEnabled(boolean debugOverlayEnabled) {
            this.debugOverlayEnabled = debugOverlayEnabled;
            return this;
        }

        /**
         * 设置 SDK 分析帧率上限。传 0 或负数表示不做 SDK 侧帧率限制。
         */
        public Builder setMaxAnalysisFps(int maxAnalysisFps) {
            this.maxAnalysisFps = Math.max(0, maxAnalysisFps);
            return this;
        }

        /**
         * 生成最终用于本次 SDK 调起的动作识别配置。
         */
        public BlinkCaptureOptions build() {
            validateElaThreshold("earCloseThreshold", earCloseThreshold);
            validateElaThreshold("earOpenThreshold", earOpenThreshold);
            if (earOpenThreshold < earCloseThreshold) {
                throw new IllegalArgumentException("earOpenThreshold must be >= earCloseThreshold");
            }
            if (doubleBlinkWindowMs <= 0L || longCloseMinMs <= 0L
                    || minShortBlinkMs < 0L || maxShortBlinkMs < minShortBlinkMs
                    || noFaceResetMs < 0L) {
                throw new IllegalArgumentException("invalid timing options");
            }
            return new BlinkCaptureOptions(this);
        }

        private static void validateElaThreshold(String name, float value) {
            if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f || value > 180f) {
                throw new IllegalArgumentException(name + " must be finite and in [0, 180]");
            }
        }
    }
}
