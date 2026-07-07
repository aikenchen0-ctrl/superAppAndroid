package com.blinkvoice.visual.api;

import android.content.Context;
import android.content.Intent;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;

public final class BlinkOutcomeContract extends ActivityResultContract<BlinkCaptureOptions, BlinkCaptureOutcome> {
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, BlinkCaptureOptions input) {
        return BlinkVoiceCapture.createIntent(context, input);
    }

    @NonNull
    @Override
    public BlinkCaptureOutcome parseResult(int resultCode, Intent intent) {
        return BlinkVoiceCapture.parseOutcome(resultCode, intent);
    }
}
