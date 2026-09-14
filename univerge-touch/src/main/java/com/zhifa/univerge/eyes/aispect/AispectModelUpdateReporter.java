package com.zhifa.univerge.eyes.aispect;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class AispectModelUpdateReporter {
    private static final Set<String> REPORTED = new HashSet<>();

    private AispectModelUpdateReporter() {
    }

    public static void report(
            String baseUrl,
            String appId,
            String deviceId,
            String sdkVersion,
            String appVersion,
            String fromModelId,
            String toModelId,
            String toModelVersion,
            boolean success,
            String error
    ) {
        String signature = appId + "|" + deviceId + "|" + toModelId + "|" + toModelVersion + "|" + success;
        synchronized (REPORTED) {
            if (REPORTED.contains(signature)) {
                return;
            }
        }
        try {
            JSONObject payload = new JSONObject()
                    .put("appId", appId)
                    .put("platform", "android")
                    .put("deviceId", deviceId)
                    .put("fromModelId", fromModelId)
                    .put("toModelId", toModelId)
                    .put("status", success ? "success" : "failed")
                    .put("sdkVersion", sdkVersion)
                    .put("appVersion", appVersion)
                    .put("error", success ? JSONObject.NULL : error);
            new AispectUrlConnectionModelHttpClient().post(
                    baseUrl.replaceAll("/+$", "") + "/api/v1/model-update-events",
                    payload.toString().getBytes(StandardCharsets.UTF_8),
                    64 * 1024
            );
            synchronized (REPORTED) {
                REPORTED.add(signature);
            }
        } catch (Exception ignored) {
            // 上报失败不得影响本地模型状态。
        }
    }
}
