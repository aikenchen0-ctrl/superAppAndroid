package com.blinkvoice.visual.api.v2;

import java.util.Objects;

/** An event in an offline replay set, identified by a stable case id. */
public final class BlinkReplayEvent {
    private final String caseId;
    private final BlinkEvent event;

    public BlinkReplayEvent(String caseId, BlinkEvent event) {
        if (caseId == null || caseId.trim().isEmpty()) {
            throw new IllegalArgumentException("caseId must not be empty");
        }
        this.caseId = caseId.trim();
        this.event = Objects.requireNonNull(event, "event");
    }

    public String getCaseId() {
        return caseId;
    }

    public BlinkEvent getEvent() {
        return event;
    }
}
