package com.blinkvoice.visual.ui;

import androidx.annotation.Nullable;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import java.util.Locale;

final class ContinuousCaptureStatusFormatter {
    private ContinuousCaptureStatusFormatter() {
    }

    static String format(@Nullable BlinkCaptureResult result, int totalCount, long eventElapsedMs) {
        String action = result == null ? "--" : labelFor(result.getEventType());
        String eventTime = result == null ? "--" : String.format(Locale.US, "%.3fs", eventElapsedMs / 1000f);
        String duration = result == null ? "--" : result.getDurationMs() + "ms";
        return "最后动作：" + action
                + "\n测试总次数：" + totalCount
                + "\n事件时间：" + eventTime
                + "\n动作耗时：" + duration;
    }

    private static String labelFor(BlinkEventType eventType) {
        switch (eventType) {
            case SINGLE_BLINK:
                return "单眨眼";
            case DOUBLE_BLINK:
                return "双眨眼";
            case LONG_CLOSE:
                return "长闭眼";
            default:
                return eventType.name();
        }
    }
}
