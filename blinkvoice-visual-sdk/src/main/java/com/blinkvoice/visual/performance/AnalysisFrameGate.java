package com.blinkvoice.visual.performance;

/**
 * 控制进入 MediaPipe 的分析帧率，降低持续检测时的图像转换和推理压力。
 */
public final class AnalysisFrameGate {
    private final long minFrameIntervalMs;
    private long lastAcceptedFrameTimeMs = Long.MIN_VALUE;

    public AnalysisFrameGate(int maxAnalysisFps) {
        if (maxAnalysisFps <= 0) {
            minFrameIntervalMs = 0L;
        } else {
            minFrameIntervalMs = Math.max(1L, 1000L / maxAnalysisFps);
        }
    }

    public synchronized boolean shouldAnalyze(long frameTimeMs) {
        if (minFrameIntervalMs <= 0L) {
            lastAcceptedFrameTimeMs = frameTimeMs;
            return true;
        }
        if (lastAcceptedFrameTimeMs == Long.MIN_VALUE
                || frameTimeMs - lastAcceptedFrameTimeMs >= minFrameIntervalMs) {
            lastAcceptedFrameTimeMs = frameTimeMs;
            return true;
        }
        return false;
    }

    public synchronized void reset() {
        lastAcceptedFrameTimeMs = Long.MIN_VALUE;
    }
}
