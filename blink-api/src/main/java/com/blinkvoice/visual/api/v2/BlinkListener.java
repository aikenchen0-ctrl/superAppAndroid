package com.blinkvoice.visual.api.v2;

/**
 * Receives session output. Callbacks are delivered by the SDK callback
 * executor, never on the host's frame producer unless explicitly documented
 * by an implementation. Implementations must return quickly and must not call
 * back into a session synchronously.
 */
public interface BlinkListener {
    default void onStateChanged(BlinkState state) {
    }

    default void onEvent(BlinkEvent event) {
    }

    default void onDiagnostics(BlinkDiagnostics diagnostics) {
    }

    default void onError(BlinkError error) {
    }
}
