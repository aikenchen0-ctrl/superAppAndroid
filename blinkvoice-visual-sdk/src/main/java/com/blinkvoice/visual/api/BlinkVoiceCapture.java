package com.blinkvoice.visual.api;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.blinkvoice.visual.ui.BlinkCaptureActivity;
import java.util.ArrayList;

public final class BlinkVoiceCapture {
    private BlinkVoiceCapture() {
    }

    public static Intent createIntent(Context context) {
        return createIntent(context, new BlinkCaptureOptions.Builder().build());
    }

    public static Intent createIntent(Context context, BlinkCaptureOptions options) {
        BlinkCaptureOptions safeOptions = options != null
                ? options
                : new BlinkCaptureOptions.Builder().build();
        ArrayList<String> eventTypeNames = new ArrayList<>();
        for (BlinkEventType eventType : safeOptions.getEventTypes()) {
            eventTypeNames.add(eventType.name());
        }

        Intent intent = new Intent(context, BlinkCaptureActivity.class);
        intent.putExtra(BlinkCaptureActivity.EXTRA_EAR_CLOSE_THRESHOLD, safeOptions.getEarCloseThreshold());
        intent.putExtra(BlinkCaptureActivity.EXTRA_EAR_OPEN_THRESHOLD, safeOptions.getEarOpenThreshold());
        intent.putExtra(BlinkCaptureActivity.EXTRA_DOUBLE_BLINK_WINDOW_MS, safeOptions.getDoubleBlinkWindowMs());
        intent.putExtra(BlinkCaptureActivity.EXTRA_LONG_CLOSE_MIN_MS, safeOptions.getLongCloseMinMs());
        intent.putExtra(BlinkCaptureActivity.EXTRA_MIN_SHORT_BLINK_MS, safeOptions.getMinShortBlinkMs());
        intent.putExtra(BlinkCaptureActivity.EXTRA_MAX_SHORT_BLINK_MS, safeOptions.getMaxShortBlinkMs());
        intent.putExtra(BlinkCaptureActivity.EXTRA_NO_FACE_RESET_MS, safeOptions.getNoFaceResetMs());
        intent.putExtra(BlinkCaptureActivity.EXTRA_AUTO_FINISH_ON_EVENT, safeOptions.isAutoFinishOnEvent());
        intent.putExtra(BlinkCaptureActivity.EXTRA_DEBUG_LOGGING_ENABLED, safeOptions.isDebugLoggingEnabled());
        intent.putExtra(BlinkCaptureActivity.EXTRA_DEBUG_OVERLAY_ENABLED, safeOptions.isDebugOverlayEnabled());
        intent.putStringArrayListExtra(BlinkCaptureActivity.EXTRA_EVENT_TYPES, eventTypeNames);
        return intent;
    }

    public static BlinkCaptureResult parseResult(int resultCode, Intent data) {
        return parseOutcome(resultCode, data).getResult();
    }

    public static BlinkCaptureOutcome parseOutcome(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            String cancelReason = data != null
                    ? data.getStringExtra(BlinkCaptureActivity.RESULT_CANCEL_REASON)
                    : null;
            return BlinkCaptureOutcome.canceled(cancelReason);
        }

        String eventTypeName = data.getStringExtra(BlinkCaptureActivity.RESULT_EVENT_TYPE);
        if (eventTypeName == null) {
            return BlinkCaptureOutcome.canceled(data.getStringExtra(BlinkCaptureActivity.RESULT_CANCEL_REASON));
        }

        BlinkEventType eventType;
        try {
            eventType = BlinkEventType.valueOf(eventTypeName);
        } catch (IllegalArgumentException ignored) {
            return BlinkCaptureOutcome.canceled(data.getStringExtra(BlinkCaptureActivity.RESULT_CANCEL_REASON));
        }

        return BlinkCaptureOutcome.success(new BlinkCaptureResult(
                eventType,
                data.getLongExtra(BlinkCaptureActivity.RESULT_START_TIME_MS, 0L),
                data.getLongExtra(BlinkCaptureActivity.RESULT_END_TIME_MS, 0L),
                data.getLongExtra(BlinkCaptureActivity.RESULT_DURATION_MS, 0L),
                data.getFloatExtra(BlinkCaptureActivity.RESULT_CONFIDENCE, 0f)
        ));
    }
}
