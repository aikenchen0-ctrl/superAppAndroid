package com.blinkvoice.visual.debug;

import android.util.Log;

public final class BlinkDebugLogger {
    private static final String TAG = "BlinkVoiceDebug";
    private static final String PREFIX = "[BVDBG] ";

    private BlinkDebugLogger() {
    }

    public static void log(boolean enabled, String message) {
        if (!enabled) {
            return;
        }
        Log.d(TAG, PREFIX + message);
    }

    public static void warn(boolean enabled, String message) {
        if (!enabled) {
            return;
        }
        Log.w(TAG, PREFIX + message);
    }
}
