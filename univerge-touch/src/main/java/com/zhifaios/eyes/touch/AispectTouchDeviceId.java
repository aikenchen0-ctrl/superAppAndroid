package com.zhifaios.eyes.touch;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public final class AispectTouchDeviceId {
    private static final String PREFERENCES_NAME = "aispect_touch_sdk";
    private static final String DEVICE_ID_KEY = "device_id";

    private AispectTouchDeviceId() {
    }

    public static synchronized String getOrCreate(Context context) {
        String generated = UUID.randomUUID().toString();
        if (context == null) {
            return generated;
        }
        Context applicationContext = context.getApplicationContext();
        SharedPreferences preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String existing = preferences.getString(DEVICE_ID_KEY, "");
        String resolved = resolve(existing, generated);
        if (!resolved.equals(existing)) {
            preferences.edit().putString(DEVICE_ID_KEY, resolved).apply();
        }
        return resolved;
    }

    static String resolve(String provided, String generated) {
        String value = provided == null ? "" : provided.trim();
        return value.isEmpty() ? generated : value;
    }
}
