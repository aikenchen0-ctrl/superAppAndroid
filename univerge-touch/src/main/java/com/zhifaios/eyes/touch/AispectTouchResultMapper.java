package com.zhifaios.eyes.touch;

import com.zhifaios.eyes.aispect.AispectModels;

final class AispectTouchResultMapper {
    private AispectTouchResultMapper() {
    }

    static AispectTouchResult fromPrediction(AispectModels.ImpactPrediction prediction, AispectModels.TouchEvent event) {
        String label = prediction == null ? "" : prediction.predictedLabel;
        return new AispectTouchResult(
                eventType(event),
                strength(label),
                fingerType(label),
                label,
                prediction == null ? 0.0 : prediction.predictedProbability,
                prediction == null ? null : prediction.probabilities,
                prediction == null ? null : prediction.labelOrder,
                prediction == null ? "" : prediction.modelId,
                prediction == null ? "" : prediction.modelVersion,
                event == null ? 0L : Math.round(event.timestampSeconds * 1000.0),
                event == null ? 0f : event.x,
                event == null ? 0f : event.y,
                event == null ? null : event.liftOffset
        );
    }

    private static AispectTouchEventType eventType(AispectModels.TouchEvent event) {
        if (event == null || event.kind == null) {
            return AispectTouchEventType.UNKNOWN;
        }
        switch (event.kind) {
            case SAMPLE:
                return AispectTouchEventType.SAMPLE;
            case PRESS:
                return AispectTouchEventType.PRESS;
            case LIGHT_TAP:
            case HEAVY_TAP:
                return AispectTouchEventType.TAP;
            case LIGHT_HOLD:
            case HEAVY_HOLD:
            case HEAVY_PRESS:
                return AispectTouchEventType.HOLD;
            case DRAG_START:
            case DRAG:
            case DRAG_END:
                return AispectTouchEventType.DRAG;
            case CANCEL:
                return AispectTouchEventType.CANCEL;
            default:
                return AispectTouchEventType.UNKNOWN;
        }
    }

    private static AispectTouchStrength strength(String label) {
        if (label == null) {
            return AispectTouchStrength.UNKNOWN;
        }
        if ("heavy".equals(label) || label.endsWith("_heavy")) {
            return AispectTouchStrength.HEAVY;
        }
        if ("light".equals(label) || label.endsWith("_light")) {
            return AispectTouchStrength.LIGHT;
        }
        return AispectTouchStrength.UNKNOWN;
    }

    private static AispectFingerType fingerType(String label) {
        if (label == null) {
            return AispectFingerType.UNKNOWN;
        }
        if (label.startsWith("thumb_")) {
            return AispectFingerType.THUMB;
        }
        if (label.startsWith("index_")) {
            return AispectFingerType.INDEX;
        }
        return AispectFingerType.UNKNOWN;
    }
}
