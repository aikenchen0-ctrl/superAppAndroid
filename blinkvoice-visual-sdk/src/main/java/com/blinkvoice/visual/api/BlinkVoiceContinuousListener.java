package com.blinkvoice.visual.api;

/**
 * 宿主 App 接入连续眨眼检测时使用的回调接口。
 */
public interface BlinkVoiceContinuousListener {
    /**
     * 启动链路分段耗时回调，用于诊断模型、CameraX 和首帧推理各阶段耗时。
     */
    default void onStartupTiming(BlinkVoiceStartupTiming timing) {
    }

    /**
     * 每帧识别后回调实时人脸、眼睛和状态机信息。
     */
    default void onFrame(BlinkVoiceFrame frame) {
    }

    /**
     * 检测到单眨、双眨或长闭眼事件时回调。
     */
    default void onEvent(BlinkCaptureResult result) {
    }

    /**
     * 模型初始化、相机绑定或推理过程中出现错误时回调。
     */
    default void onError(String error) {
    }
}
