package com.blinkvoice.visual.ui;

import static org.junit.Assert.assertEquals;

import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import org.junit.Test;

public class ContinuousCaptureStatusFormatterTest {
    @Test
    public void formatsInitialContinuousStatus() {
        String text = ContinuousCaptureStatusFormatter.format(null, 0, 0L);

        assertEquals("最后动作：--\n测试总次数：0\n事件时间：--\n动作耗时：--", text);
    }

    @Test
    public void formatsLastDoubleBlinkInChineseWithCountAndTime() {
        BlinkCaptureResult result = new BlinkCaptureResult(
                BlinkEventType.DOUBLE_BLINK,
                1000L,
                1270L,
                270L,
                1f
        );

        String text = ContinuousCaptureStatusFormatter.format(result, 3, 12_345L);

        assertEquals("最后动作：双眨眼\n测试总次数：3\n事件时间：12.345s\n动作耗时：270ms", text);
    }
}
