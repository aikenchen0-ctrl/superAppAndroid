package com.blinkvoice.visual.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import com.blinkvoice.visual.ui.BlinkCaptureActivity;
import java.util.EnumSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class BlinkVoiceCaptureTest {
    @Test
    public void createIntentTargetsCaptureActivityAndCarriesOptions() {
        BlinkCaptureOptions options = new BlinkCaptureOptions.Builder()
                .setEarCloseThreshold(0.20f)
                .setEarOpenThreshold(0.24f)
                .setDoubleBlinkWindowMs(420L)
                .setLongCloseMinMs(900L)
                .setAutoFinishOnEvent(false)
                .setDebugLoggingEnabled(true)
                .setDebugOverlayEnabled(false)
                .setEventTypes(EnumSet.of(BlinkEventType.DOUBLE_BLINK))
                .build();

        Intent intent = BlinkVoiceCapture.createIntent(RuntimeEnvironment.getApplication(), options);

        assertEquals(BlinkCaptureActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(0.20f, intent.getFloatExtra(BlinkCaptureActivity.EXTRA_EAR_CLOSE_THRESHOLD, 0f), 0.0001f);
        assertEquals(0.24f, intent.getFloatExtra(BlinkCaptureActivity.EXTRA_EAR_OPEN_THRESHOLD, 0f), 0.0001f);
        assertEquals(420L, intent.getLongExtra(BlinkCaptureActivity.EXTRA_DOUBLE_BLINK_WINDOW_MS, 0L));
        assertEquals(900L, intent.getLongExtra(BlinkCaptureActivity.EXTRA_LONG_CLOSE_MIN_MS, 0L));
        assertEquals(false, intent.getBooleanExtra(BlinkCaptureActivity.EXTRA_AUTO_FINISH_ON_EVENT, true));
        assertEquals(true, intent.getBooleanExtra(BlinkCaptureActivity.EXTRA_DEBUG_LOGGING_ENABLED, false));
        assertEquals(false, intent.getBooleanExtra(BlinkCaptureActivity.EXTRA_DEBUG_OVERLAY_ENABLED, true));
        assertEquals(
                BlinkEventType.DOUBLE_BLINK.name(),
                intent.getStringArrayListExtra(BlinkCaptureActivity.EXTRA_EVENT_TYPES).get(0)
        );
    }

    @Test
    public void debugLoggingIsDisabledByDefault() {
        BlinkCaptureOptions options = new BlinkCaptureOptions.Builder().build();

        assertFalse(options.isDebugLoggingEnabled());
    }

    @Test
    public void debugOverlayIsEnabledByDefaultForDeviceDiagnosis() {
        BlinkCaptureOptions options = new BlinkCaptureOptions.Builder().build();

        assertTrue(options.isDebugOverlayEnabled());
    }

    @Test
    public void parseResultReturnsCaptureResultWhenActivitySucceeds() {
        Intent data = new Intent()
                .putExtra(BlinkCaptureActivity.RESULT_EVENT_TYPE, BlinkEventType.LONG_CLOSE.name())
                .putExtra(BlinkCaptureActivity.RESULT_START_TIME_MS, 100L)
                .putExtra(BlinkCaptureActivity.RESULT_END_TIME_MS, 900L)
                .putExtra(BlinkCaptureActivity.RESULT_DURATION_MS, 800L)
                .putExtra(BlinkCaptureActivity.RESULT_CONFIDENCE, 0.9f);

        BlinkCaptureResult result = BlinkVoiceCapture.parseResult(Activity.RESULT_OK, data);

        assertEquals(BlinkEventType.LONG_CLOSE, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(900L, result.getEndTimeMs());
        assertEquals(800L, result.getDurationMs());
        assertEquals(0.9f, result.getConfidence(), 0.0001f);
    }

    @Test
    public void parseResultReturnsNullForCanceledActivity() {
        assertNull(BlinkVoiceCapture.parseResult(Activity.RESULT_CANCELED, new Intent()));
    }

    @Test
    public void parseOutcomeReturnsCancelReason() {
        Intent data = new Intent()
                .putExtra(BlinkCaptureActivity.RESULT_CANCEL_REASON, "Camera unavailable");

        BlinkCaptureOutcome outcome = BlinkVoiceCapture.parseOutcome(Activity.RESULT_CANCELED, data);

        assertFalse(outcome.isSuccess());
        assertEquals("Camera unavailable", outcome.getCancelReason());
    }
}
