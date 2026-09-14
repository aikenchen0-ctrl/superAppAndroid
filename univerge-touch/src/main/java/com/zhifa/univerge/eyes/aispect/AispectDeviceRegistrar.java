package com.zhifa.univerge.eyes.aispect;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/** Registers an SDK host so it can be targeted by device-scoped assignments. */
public final class AispectDeviceRegistrar {
    private AispectDeviceRegistrar() {
    }

    public static void register(
            String baseUrl,
            String appId,
            String deviceId,
            String sdkVersion,
            String appVersion,
            String currentModelId,
            String currentModelVersion,
            boolean allowInsecureTransport
    ) throws IOException, JSONException {
        register(
                baseUrl,
                appId,
                deviceId,
                sdkVersion,
                appVersion,
                currentModelId,
                currentModelVersion,
                allowInsecureTransport,
                ""
        );
    }

    public static void register(
            String baseUrl,
            String appId,
            String deviceId,
            String sdkVersion,
            String appVersion,
            String currentModelId,
            String currentModelVersion,
            boolean allowInsecureTransport,
            String deviceName
    ) throws IOException, JSONException {
        registerWithClient(
                new AispectUrlConnectionModelHttpClient(),
                baseUrl,
                appId,
                deviceId,
                sdkVersion,
                appVersion,
                currentModelId,
                currentModelVersion,
                allowInsecureTransport,
                deviceName
        );
    }

    static void registerWithClient(
            AispectModelHttpClient httpClient,
            String baseUrl,
            String appId,
            String deviceId,
            String sdkVersion,
            String appVersion,
            String currentModelId,
            String currentModelVersion,
            boolean allowInsecureTransport
    ) throws IOException, JSONException {
        registerWithClient(
                httpClient,
                baseUrl,
                appId,
                deviceId,
                sdkVersion,
                appVersion,
                currentModelId,
                currentModelVersion,
                allowInsecureTransport,
                ""
        );
    }

    static void registerWithClient(
            AispectModelHttpClient httpClient,
            String baseUrl,
            String appId,
            String deviceId,
            String sdkVersion,
            String appVersion,
            String currentModelId,
            String currentModelVersion,
            boolean allowInsecureTransport,
            String deviceName
    ) throws IOException, JSONException {
        if (httpClient == null) {
            throw new IOException("http client missing");
        }
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl, allowInsecureTransport);
        if (normalizedBaseUrl.isEmpty() || isEmpty(appId) || isEmpty(deviceId) || isEmpty(sdkVersion)) {
            throw new IOException("device registration configuration invalid");
        }
        JSONObject body = new JSONObject()
                .put("platform", "android")
                .put("appId", appId)
                .put("deviceId", deviceId)
                .put("sdkVersion", sdkVersion);
        append(body, "appVersion", appVersion);
        append(body, "deviceName", deviceName);
        append(body, "currentModelId", currentModelId);
        append(body, "currentModelVersion", currentModelVersion);
        httpClient.post(
                normalizedBaseUrl + "/api/v1/devices/register",
                body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                64 * 1024
        );
    }

    private static void append(JSONObject body, String key, String value) throws JSONException {
        if (!isEmpty(value)) {
            body.put(key, value);
        }
    }

    private static String normalizeBaseUrl(String value, boolean allowInsecureTransport) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            URL url = new URL(normalized);
            String protocol = url.getProtocol();
            boolean acceptedProtocol = "https".equalsIgnoreCase(protocol)
                    || (allowInsecureTransport && "http".equalsIgnoreCase(protocol));
            return acceptedProtocol && !isEmpty(url.getHost()) ? normalized : "";
        } catch (MalformedURLException error) {
            return "";
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
