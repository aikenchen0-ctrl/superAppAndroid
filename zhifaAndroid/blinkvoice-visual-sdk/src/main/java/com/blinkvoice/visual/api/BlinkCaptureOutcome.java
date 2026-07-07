package com.blinkvoice.visual.api;

import androidx.annotation.Nullable;

public final class BlinkCaptureOutcome {
    private final BlinkCaptureResult result;
    private final String cancelReason;

    private BlinkCaptureOutcome(@Nullable BlinkCaptureResult result, @Nullable String cancelReason) {
        this.result = result;
        this.cancelReason = cancelReason;
    }

    public static BlinkCaptureOutcome success(BlinkCaptureResult result) {
        return new BlinkCaptureOutcome(result, null);
    }

    public static BlinkCaptureOutcome canceled(@Nullable String cancelReason) {
        return new BlinkCaptureOutcome(null, cancelReason);
    }

    @Nullable
    public BlinkCaptureResult getResult() {
        return result;
    }

    @Nullable
    public String getCancelReason() {
        return cancelReason;
    }

    public boolean isSuccess() {
        return result != null;
    }
}
