package com.blinkvoice.visual.debug;

import java.util.Locale;

public final class BlinkTraceFormatter {
    private BlinkTraceFormatter() {
    }

    public static String formatFrame(
            String sessionId,
            int frameIndex,
            long frameTimeMs,
            long deltaTimeMs,
            boolean hasFace,
            float leftEla,
            float rightEla,
            boolean leftClosed,
            boolean rightClosed,
            String phase,
            String reason,
            long closedDurationMs,
            long pendingBlinkElapsedMs,
            String event,
            long inferenceMs
    ) {
        float averageEla = (leftEla + rightEla) / 2f;
        return String.format(Locale.US,
                "BVTRACE,frame,%s,%d,%d,%d,%d,%.3f,%.3f,%.3f,%d,%d,%s,%s,%d,%d,%s,%d",
                safe(sessionId),
                frameIndex,
                frameTimeMs,
                deltaTimeMs,
                hasFace ? 1 : 0,
                leftEla,
                rightEla,
                averageEla,
                leftClosed ? 1 : 0,
                rightClosed ? 1 : 0,
                safe(phase),
                safe(reason),
                closedDurationMs,
                pendingBlinkElapsedMs,
                safe(event),
                inferenceMs
        );
    }

    public static String formatMarker(
            String sessionId,
            int stepIndex,
            String marker,
            String action,
            String note
    ) {
        return "BVTRACE,mark,"
                + safe(sessionId)
                + ","
                + stepIndex
                + ","
                + safe(marker)
                + ","
                + safe(action)
                + ","
                + safe(note);
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        return value.replace(',', '_').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
