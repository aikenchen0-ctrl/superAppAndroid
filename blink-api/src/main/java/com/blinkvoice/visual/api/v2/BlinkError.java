package com.blinkvoice.visual.api.v2;

/** A stable, platform-neutral error delivered to a {@link BlinkListener}. */
public final class BlinkError {
    public enum Code {
        INVALID_OPTIONS,
        INVALID_FRAME,
        NOT_STARTED,
        ALREADY_STARTED,
        OUT_OF_ORDER_FRAME,
        PROCESSING_FAILURE,
        RESOURCE_UNAVAILABLE,
        PERMISSION_DENIED,
        CLOSED,
        INTERNAL
    }

    private final Code code;
    private final String message;
    private final boolean recoverable;

    public BlinkError(Code code, String message, boolean recoverable) {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("message must not be empty");
        }
        this.code = code;
        this.message = message;
        this.recoverable = recoverable;
    }

    public Code getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRecoverable() {
        return recoverable;
    }
}
