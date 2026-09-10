package com.zhifaios.eyes.touch;

public final class AispectTouchResult {
    public final AispectTouchEventType eventType;
    public final AispectTouchStrength strength;
    public final AispectFingerType fingerType;
    public final String classLabel;
    public final double confidence;
    public final double[] probabilities;
    public final String[] labelOrder;
    public final String modelId;
    public final String modelVersion;
    public final long eventTimeMillis;
    public final float x;
    public final float y;
    public final Float liftOffset;

    AispectTouchResult(
            AispectTouchEventType eventType,
            AispectTouchStrength strength,
            AispectFingerType fingerType,
            String classLabel,
            double confidence,
            double[] probabilities,
            String[] labelOrder,
            String modelId,
            String modelVersion,
            long eventTimeMillis,
            float x,
            float y,
            Float liftOffset
    ) {
        this.eventType = eventType == null ? AispectTouchEventType.UNKNOWN : eventType;
        this.strength = strength == null ? AispectTouchStrength.UNKNOWN : strength;
        this.fingerType = fingerType == null ? AispectFingerType.UNKNOWN : fingerType;
        this.classLabel = classLabel == null ? "" : classLabel;
        this.confidence = confidence;
        this.probabilities = probabilities == null ? new double[0] : probabilities.clone();
        this.labelOrder = labelOrder == null ? new String[0] : labelOrder.clone();
        this.modelId = modelId == null ? "" : modelId;
        this.modelVersion = modelVersion == null ? "" : modelVersion;
        this.eventTimeMillis = eventTimeMillis;
        this.x = x;
        this.y = y;
        this.liftOffset = liftOffset;
    }
}
