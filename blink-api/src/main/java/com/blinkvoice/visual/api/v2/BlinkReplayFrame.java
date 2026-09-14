package com.blinkvoice.visual.api.v2;

import java.util.Objects;

/** A normalized frame observation in an offline replay case. */
public final class BlinkReplayFrame {
    private final String caseId;
    private final BlinkFrame frame;

    public BlinkReplayFrame(String caseId, BlinkFrame frame) {
        if (caseId == null || caseId.trim().isEmpty()) {
            throw new IllegalArgumentException("caseId must not be empty");
        }
        this.caseId = caseId.trim();
        this.frame = Objects.requireNonNull(frame, "frame");
    }

    public String getCaseId() {
        return caseId;
    }

    public BlinkFrame getFrame() {
        return frame;
    }
}
