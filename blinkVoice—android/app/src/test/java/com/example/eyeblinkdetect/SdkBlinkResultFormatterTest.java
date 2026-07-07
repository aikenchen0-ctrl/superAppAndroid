package com.example.eyeblinkdetect;

import static org.junit.Assert.assertEquals;

import com.blinkvoice.visual.api.BlinkCaptureOutcome;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import org.junit.Test;

public class SdkBlinkResultFormatterTest {
    @Test
    public void formatsDoubleBlinkResultForDemoScreen() {
        BlinkCaptureResult result = new BlinkCaptureResult(
                BlinkEventType.DOUBLE_BLINK,
                100L,
                370L,
                270L,
                1.0f
        );

        assertEquals(
                "SDK result: 快速眨两下, duration=270ms",
                SdkBlinkResultFormatter.format(result)
        );
    }

    @Test
    public void formatsCanceledResultForDemoScreen() {
        assertEquals("SDK result: canceled", SdkBlinkResultFormatter.format((BlinkCaptureResult) null));
    }

    @Test
    public void formatsCanceledOutcomeReasonForDemoScreen() {
        BlinkCaptureOutcome outcome = BlinkCaptureOutcome.canceled("Camera unavailable");

        assertEquals("SDK result: canceled (Camera unavailable)", SdkBlinkResultFormatter.format(outcome));
    }
}
