package com.zhifa.univerge.eyes.aispect;

final class AispectInputQuality {
    enum Reason {
        NONE(""),
        MULTI_POINTER("multi_pointer"),
        DRAG_OR_CANCEL("drag_or_cancel"),
        TOUCH_TRAVEL_EXCEEDED("touch_travel_exceeded"),
        LINEAR_ACCELERATION_UNAVAILABLE("linear_acceleration_unavailable"),
        SAMPLE_RATE_OUT_OF_RANGE("sample_rate_out_of_range"),
        SENSOR_WINDOW_INCOMPLETE("sensor_window_incomplete"),
        HEAVY_EVIDENCE_INSUFFICIENT("heavy_evidence_insufficient");

        private final String key;

        Reason(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }
    }

    private AispectInputQuality() {
    }

    static Reason gestureReason(
            int maximumPointerCount,
            double touchTravelPx,
            boolean dragOrCancel,
            double maximumTouchTravelPx
    ) {
        if (maximumPointerCount > 1) {
            return Reason.MULTI_POINTER;
        }
        if (dragOrCancel) {
            return Reason.DRAG_OR_CANCEL;
        }
        if (Double.isFinite(touchTravelPx)
                && touchTravelPx > Math.max(0.0, maximumTouchTravelPx)) {
            return Reason.TOUCH_TRAVEL_EXCEEDED;
        }
        return Reason.NONE;
    }

    static Reason signalReason(
            boolean hasReliableLinearAcceleration,
            double sampleRateHz,
            int availableWindowFrames,
            double minimumSampleRateHz,
            double maximumSampleRateHz,
            int minimumWindowFrames
    ) {
        if (!hasReliableLinearAcceleration) {
            return Reason.LINEAR_ACCELERATION_UNAVAILABLE;
        }
        double minimumRate = Math.max(1.0, minimumSampleRateHz);
        double maximumRate = Math.max(minimumRate, maximumSampleRateHz);
        if (!Double.isFinite(sampleRateHz) || sampleRateHz < minimumRate || sampleRateHz > maximumRate) {
            return Reason.SAMPLE_RATE_OUT_OF_RANGE;
        }
        if (availableWindowFrames < Math.max(1, minimumWindowFrames)) {
            return Reason.SENSOR_WINDOW_INCOMPLETE;
        }
        return Reason.NONE;
    }

    static boolean shouldRejectReleaseInput(boolean isManualCollection, Reason reason) {
        return !isManualCollection && reason != null && reason != Reason.NONE;
    }

    static boolean shouldRejectGestureInput(boolean isManualCollection, Reason reason) {
        return !isManualCollection && reason != null && reason != Reason.NONE;
    }

}
