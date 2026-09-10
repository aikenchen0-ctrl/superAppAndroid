package com.zhifaios.eyes.touch;

public final class AispectTouchModelUpdateResult {
    public final AispectTouchModelUpdateStatus status;
    public final String requestedModelId;
    public final String requestedModelVersion;
    public final String activeModelId;
    public final String activeModelVersion;
    public final String reason;
    public final boolean updated;
    public final String modelId;
    public final String version;

    public AispectTouchModelUpdateResult(
            AispectTouchModelUpdateStatus status,
            String requestedModelId,
            String requestedModelVersion,
            String activeModelId,
            String activeModelVersion,
            String reason
    ) {
        this.status = status == null ? AispectTouchModelUpdateStatus.FAILED : status;
        this.requestedModelId = safe(requestedModelId);
        this.requestedModelVersion = safe(requestedModelVersion);
        this.activeModelId = safe(activeModelId);
        this.activeModelVersion = safe(activeModelVersion);
        this.reason = safe(reason);
        this.updated = this.status == AispectTouchModelUpdateStatus.ACTIVATED;
        this.modelId = this.requestedModelId;
        this.version = this.requestedModelVersion;
    }

    public AispectTouchModelUpdateResult(boolean updated, String modelId, String reason) {
        this(
                updated ? AispectTouchModelUpdateStatus.ACTIVATED : AispectTouchModelUpdateStatus.REJECTED,
                modelId,
                "",
                updated ? modelId : "",
                "",
                reason
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
