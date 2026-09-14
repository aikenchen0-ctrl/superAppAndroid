package com.blinkvoice.visual.api.v2;

/**
 * A lifecycle-bound blink processing session.
 *
 * <p>Lifecycle methods are idempotent and may be called from any host thread.
 * Frames must be submitted serially from one producer thread, in non-decreasing
 * timestamp order. Implementations must not retain a frame after
 * {@link #submitFrame(BlinkFrame)} returns. A session is terminal after
 * {@link #close()}.</p>
 */
public interface BlinkSession extends AutoCloseable {
    void start();

    /**
     * Submits one immutable observation. A false result means that the frame
     * was rejected; the listener receives the corresponding {@link BlinkError}.
     */
    boolean submitFrame(BlinkFrame frame);

    /** Alias kept explicit for hosts that model input as acceptance. */
    default boolean acceptFrame(BlinkFrame frame) {
        return submitFrame(frame);
    }

    void stop();

    @Override
    void close();

    BlinkState getState();
}
