package com.blinkvoice.visual.api;

import android.content.Context;
import android.content.Intent;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class BlinkResultContract extends ActivityResultContract<BlinkCaptureOptions, BlinkCaptureResult> {
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, BlinkCaptureOptions input) {
        return BlinkVoiceCapture.createIntent(context, input);
    }

    @Nullable
    @Override
    public BlinkCaptureResult parseResult(int resultCode, @Nullable Intent intent) {
        return BlinkVoiceCapture.parseResult(resultCode, intent);
    }
}
