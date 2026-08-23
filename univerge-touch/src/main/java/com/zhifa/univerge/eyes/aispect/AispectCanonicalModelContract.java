package com.zhifa.univerge.eyes.aispect;

import java.util.Arrays;

final class AispectCanonicalModelContract {
    static final String WINDOW_MODE = "release";
    static final int CAPTURE_DELAY_MS = 0;
    private static final int[] FRAME_INDICES = new int[]{-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final String[] FEATURE_NAMES = new String[]{
            "x_norm", "y_norm",
            "userAcceleration.x", "userAcceleration.y", "userAcceleration.z",
            "rotationRate.x", "rotationRate.y", "rotationRate.z",
            "gravity.x", "gravity.y", "gravity.z",
            "time_since_touch_down", "time_since_anchor",
            "contactPatch.cov_11", "contactPatch.cov_12", "contactPatch.cov_22",
            "contactPatch.sigmaX", "contactPatch.sigmaY",
            "touchNormalization.rawMajorPx", "touchNormalization.rawMinorPx",
            "touchNormalization.areaValue"
    };
    private static final String[] LABEL_ORDER = new String[]{
            "thumb_light", "thumb_heavy", "index_light", "index_heavy"
    };

    private AispectCanonicalModelContract() {
    }

    static boolean matches(String windowMode, int captureDelayMs, int[] frameIndices, String[] featureNames) {
        return WINDOW_MODE.equals(windowMode)
                && captureDelayMs == CAPTURE_DELAY_MS
                && Arrays.equals(FRAME_INDICES, frameIndices)
                && Arrays.equals(FEATURE_NAMES, featureNames);
    }

    static boolean matchesLabels(String[] labelOrder) {
        return Arrays.equals(LABEL_ORDER, labelOrder);
    }

    static int[] frameIndices() {
        return FRAME_INDICES.clone();
    }

    static String[] featureNames() {
        return FEATURE_NAMES.clone();
    }

    static String[] labelOrder() {
        return LABEL_ORDER.clone();
    }
}
