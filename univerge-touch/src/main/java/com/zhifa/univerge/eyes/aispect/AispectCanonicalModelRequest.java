package com.zhifa.univerge.eyes.aispect;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class AispectCanonicalModelRequest {
    private AispectCanonicalModelRequest() {
    }

    public static String build(
            String baseUrl,
            String appId,
            String deviceId,
            String sdkVersion,
            String appVersion,
            String currentModelId,
            String currentModelVersion
    ) {
        return build(
                baseUrl,
                appId,
                deviceId,
                sdkVersion,
                appVersion,
                currentModelId,
                currentModelVersion,
                false
        );
    }

    public static String build(
            String baseUrl,
            String appId,
            String deviceId,
            String sdkVersion,
            String appVersion,
            String currentModelId,
            String currentModelVersion,
            boolean allowInsecureTransport
    ) {
        if (isEmpty(appId) || isEmpty(deviceId) || isEmpty(sdkVersion)) {
            return "";
        }
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl, allowInsecureTransport);
        if (normalizedBaseUrl.isEmpty()) {
            return "";
        }
        StringBuilder output = new StringBuilder(normalizedBaseUrl)
                .append("/api/v1/model-assignment")
                .append("?appId=").append(encode(appId))
                .append("&deviceId=").append(encode(deviceId))
                .append("&sdkVersion=").append(encode(sdkVersion))
                .append("&platform=android");
        appendOptional(output, "currentModelId", currentModelId);
        appendOptional(output, "currentModelVersion", currentModelVersion);
        appendOptional(output, "appVersion", appVersion);
        return output.toString();
    }

    private static void appendOptional(StringBuilder output, String name, String value) {
        if (!isEmpty(value)) {
            output.append("&").append(name).append("=").append(encode(value));
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

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException error) {
            return "";
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
