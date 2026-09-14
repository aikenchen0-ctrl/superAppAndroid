package com.blinkvoice.visual.api;

/**
 * 连续检测启动链路中的一个分段耗时点。
 */
public final class BlinkVoiceStartupTiming {
    public static final String STAGE_START = "start";
    public static final String STAGE_DETECTOR_READY = "detector_ready";
    public static final String STAGE_CAMERA_PROVIDER_READY = "camera_provider_ready";
    public static final String STAGE_CAMERA_BOUND = "camera_bound";
    public static final String STAGE_FIRST_FRAME = "first_frame";
    public static final String STAGE_FIRST_RESULT = "first_result";

    private final String stage;
    private final long elapsedMs;
    private final String detail;

    public BlinkVoiceStartupTiming(String stage, long elapsedMs, String detail) {
        this.stage = stage != null ? stage : "";
        this.elapsedMs = Math.max(0L, elapsedMs);
        this.detail = detail != null ? detail : "";
    }

    public String getStage() {
        return stage;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public String getDetail() {
        return detail;
    }
}
