package com.zhifaios.eyes.touch;

public final class AispectTouchError {
    public final AispectTouchErrorCode code;
    public final Throwable cause;

    public AispectTouchError(AispectTouchErrorCode code, Throwable cause) {
        this.code = code == null ? AispectTouchErrorCode.UNKNOWN : code;
        this.cause = cause;
    }
}
