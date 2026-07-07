package com.example.eyeblinkdetect;

import androidx.annotation.Nullable;
import com.blinkvoice.visual.api.BlinkCaptureOutcome;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;

public final class SdkBlinkResultFormatter {
    /**
     * 禁止创建格式化器实例，所有格式化逻辑都通过静态方法使用。
     */
    private SdkBlinkResultFormatter() {
    }

    /**
     * 将成功识别的 SDK 结果转换成 demo 页面展示文本。
     */
    public static String format(@Nullable BlinkCaptureResult result) {
        if (result == null) {
            return "SDK result: canceled";
        }

        return "SDK result: "
                + labelFor(result.getEventType())
                + ", duration="
                + result.getDurationMs()
                + "ms";
    }

    /**
     * 将 SDK 成功事件或取消结果统一转换成 demo 页面展示文本。
     */
    public static String format(BlinkCaptureOutcome outcome) {
        if (outcome == null || !outcome.isSuccess()) {
            String reason = outcome != null ? outcome.getCancelReason() : null;
            if (reason == null || reason.trim().isEmpty()) {
                return "SDK result: canceled";
            }
            return "SDK result: canceled (" + reason + ")";
        }
        return format(outcome.getResult());
    }

    /**
     * 将 SDK 事件枚举映射成 demo 页面使用的中文标签。
     */
    private static String labelFor(BlinkEventType eventType) {
        switch (eventType) {
            case SINGLE_BLINK:
                return "单次眨眼";
            case DOUBLE_BLINK:
                return "快速眨两下";
            case LONG_CLOSE:
                return "闭眼";
            default:
                return eventType.name();
        }
    }
}
