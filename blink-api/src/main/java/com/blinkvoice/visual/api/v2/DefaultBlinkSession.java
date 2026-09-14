package com.blinkvoice.visual.api.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Deterministic, model-neutral blink state machine used behind platform adapters. */
final class DefaultBlinkSession implements BlinkSession {
    private static final double RELATIVE_CLOSE_MIN_DROP = 0.18d;
    private static final double RELATIVE_CLOSE_RATIO = 0.70d;

    private enum GesturePhase {
        OPEN,
        CLOSED,
        WAITING_SECOND_BLINK,
        LONG_CLOSE_EMITTED
    }

    private static final class BlinkSegment {
        final long startMs;
        final long endMs;

        BlinkSegment(long startMs, long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }

        long durationMs() {
            return endMs - startMs;
        }
    }

    private final BlinkOptions options;
    private final BlinkListener listener;
    private final Executor callbackExecutor;
    private final Object lock = new Object();

    private BlinkState state = BlinkState.idle();
    private GesturePhase gesturePhase = GesturePhase.OPEN;
    private boolean started;
    private boolean stopped;
    private boolean closed;
    private boolean eyesClosed;
    private boolean hasSeenOpenEyes;
    private boolean hasSeenFace;
    private long lastTimestampMs = -1L;
    private long lastFaceTimestampMs = -1L;
    private long closedStartMs = -1L;
    private BlinkSegment pendingShortBlink;
    private double openBaseline;
    private BlinkEvent lastEvent;
    private BlinkError lastError;

    DefaultBlinkSession(BlinkOptions options, BlinkListener listener, Executor callbackExecutor) {
        this.options = Objects.requireNonNull(options, "options");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
    }

    @Override
    public void start() {
        List<Runnable> callbacks = new ArrayList<>();
        synchronized (lock) {
            if (closed || started) {
                return;
            }
            if (stopped) {
                resetSessionState();
            }
            started = true;
            stopped = false;
            state = new BlinkState(
                    BlinkState.Phase.STARTING,
                    Math.max(0L, lastTimestampMs),
                    false,
                    lastEvent,
                    lastError
            );
            callbacks.add(stateCallback(state));
            state = new BlinkState(
                    BlinkState.Phase.RUNNING,
                    Math.max(0L, lastTimestampMs),
                    false,
                    lastEvent,
                    lastError
            );
            callbacks.add(stateCallback(state));
        }
        dispatch(callbacks);
    }

    @Override
    public boolean submitFrame(BlinkFrame frame) {
        if (frame == null) {
            dispatch(singleton(errorCallback(new BlinkError(
                    BlinkError.Code.INVALID_FRAME,
                    "frame must not be null",
                    false
            ))));
            return false;
        }

        List<Runnable> callbacks = new ArrayList<>();
        boolean accepted;
        synchronized (lock) {
            if (closed) {
                callbacks.add(errorCallback(new BlinkError(
                        BlinkError.Code.CLOSED,
                        "session is closed",
                        false
                )));
                accepted = false;
            } else if (!started || stopped) {
                callbacks.add(errorCallback(new BlinkError(
                        BlinkError.Code.NOT_STARTED,
                        "session is not started",
                        false
                )));
                accepted = false;
            } else if (lastTimestampMs >= 0L && frame.getTimestampMs() < lastTimestampMs) {
                lastError = new BlinkError(
                        BlinkError.Code.OUT_OF_ORDER_FRAME,
                        "frame timestamp must be non-decreasing",
                        true
                );
                callbacks.add(errorCallback(lastError));
                accepted = false;
            } else {
                lastTimestampMs = frame.getTimestampMs();
                accepted = true;
                BlinkEvent event = processFrame(frame);
                BlinkDiagnostics diagnostics = new BlinkDiagnostics(
                        frame.getTimestampMs(),
                        0L,
                        frame.isFacePresent(),
                        frame.getFaceConfidence(),
                        frame.getLeftEyeOpenness(),
                        frame.getRightEyeOpenness(),
                        0L
                );
                callbacks.add(stateCallback(state));
                callbacks.add(diagnosticsCallback(diagnostics));
                if (event != null && options.getEventTypes().contains(event.getType())) {
                    callbacks.add(eventCallback(event));
                    if (options.isStopOnEvent()) {
                        started = false;
                        stopped = true;
                        state = new BlinkState(
                                BlinkState.Phase.STOPPED,
                                frame.getTimestampMs(),
                                frame.isFacePresent(),
                                lastEvent,
                                lastError
                        );
                        callbacks.add(stateCallback(state));
                    }
                }
            }
        }
        dispatch(callbacks);
        return accepted;
    }

    @Override
    public void stop() {
        List<Runnable> callbacks = new ArrayList<>();
        synchronized (lock) {
            if (closed || stopped || !started) {
                return;
            }
            started = false;
            stopped = true;
            clearGestureState();
            state = new BlinkState(
                    BlinkState.Phase.STOPPED,
                    Math.max(0L, lastTimestampMs),
                    false,
                    lastEvent,
                    lastError
            );
            callbacks.add(stateCallback(state));
        }
        dispatch(callbacks);
    }

    @Override
    public void close() {
        List<Runnable> callbacks = new ArrayList<>();
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (started && !stopped) {
                started = false;
                stopped = true;
                clearGestureState();
            }
            closed = true;
            state = new BlinkState(
                    BlinkState.Phase.CLOSED,
                    Math.max(0L, lastTimestampMs),
                    false,
                    lastEvent,
                    lastError
            );
            callbacks.add(stateCallback(state));
        }
        dispatch(callbacks);
    }

    @Override
    public BlinkState getState() {
        synchronized (lock) {
            return state;
        }
    }

    private BlinkEvent processFrame(BlinkFrame frame) {
        long timestampMs = frame.getTimestampMs();
        if (!frame.isFacePresent()) {
            if (hasSeenFace && timestampMs - lastFaceTimestampMs >= options.getNoFaceResetMs()) {
                clearGestureState();
                hasSeenFace = false;
                openBaseline = 0d;
            }
            state = new BlinkState(
                    hasSeenFace ? BlinkState.Phase.RUNNING : BlinkState.Phase.WAITING_FOR_FACE,
                    timestampMs,
                    false,
                    lastEvent,
                    lastError
            );
            return flushExpiredPending(timestampMs);
        }

        hasSeenFace = true;
        lastFaceTimestampMs = timestampMs;
        double averageOpenness = (frame.getLeftEyeOpenness() + frame.getRightEyeOpenness()) / 2d;
        boolean closedNow = resolveClosed(averageOpenness);
        if (!closedNow) {
            updateOpenBaseline(averageOpenness);
        }

        BlinkEvent expiredEvent = flushExpiredPending(timestampMs);
        if (!hasSeenOpenEyes) {
            if (!closedNow) {
                hasSeenOpenEyes = true;
                gesturePhase = GesturePhase.OPEN;
                eyesClosed = false;
                state = readyState(timestampMs, true);
            } else {
                state = new BlinkState(
                        BlinkState.Phase.WAITING_FOR_FACE,
                        timestampMs,
                        true,
                        lastEvent,
                        lastError
                );
            }
            return expiredEvent;
        }

        switch (gesturePhase) {
            case OPEN:
                if (closedNow) {
                    beginClose(timestampMs);
                }
                break;
            case CLOSED:
                if (closedNow) {
                    if (timestampMs - closedStartMs >= options.getLongCloseMinMs()) {
                        BlinkEvent event = new BlinkEvent(
                                BlinkEventType.LONG_CLOSE,
                                closedStartMs,
                                timestampMs,
                                confidenceForLongClose(timestampMs - closedStartMs)
                        );
                        lastEvent = event;
                        pendingShortBlink = null;
                        gesturePhase = GesturePhase.LONG_CLOSE_EMITTED;
                        eyesClosed = true;
                        state = new BlinkState(
                                BlinkState.Phase.EYES_CLOSED,
                                timestampMs,
                                true,
                                lastEvent,
                                lastError
                        );
                        return event;
                    }
                } else {
                    BlinkSegment segment = new BlinkSegment(closedStartMs, timestampMs);
                    eyesClosed = false;
                    gesturePhase = GesturePhase.OPEN;
                    BlinkEvent event = completeShortSegment(segment);
                    state = event == null && pendingShortBlink != null
                            ? new BlinkState(BlinkState.Phase.READY, timestampMs, true, lastEvent, lastError)
                            : readyState(timestampMs, true);
                    return event != null ? event : expiredEvent;
                }
                break;
            case WAITING_SECOND_BLINK:
                if (closedNow) {
                    beginClose(timestampMs);
                    gesturePhase = GesturePhase.CLOSED;
                }
                break;
            case LONG_CLOSE_EMITTED:
                if (!closedNow) {
                    eyesClosed = false;
                    gesturePhase = GesturePhase.OPEN;
                    state = readyState(timestampMs, true);
                }
                break;
            default:
                break;
        }

        state = eyesClosed
                ? new BlinkState(BlinkState.Phase.EYES_CLOSED, timestampMs, true, lastEvent, lastError)
                : (gesturePhase == GesturePhase.WAITING_SECOND_BLINK
                        ? new BlinkState(BlinkState.Phase.READY, timestampMs, true, lastEvent, lastError)
                        : readyState(timestampMs, true));
        return expiredEvent;
    }

    private BlinkEvent completeShortSegment(BlinkSegment segment) {
        if (segment.durationMs() < options.getMinBlinkDurationMs()
                || segment.durationMs() > options.getMaxBlinkDurationMs()) {
            pendingShortBlink = null;
            gesturePhase = GesturePhase.OPEN;
            return null;
        }
        if (pendingShortBlink == null) {
            pendingShortBlink = segment;
            gesturePhase = GesturePhase.WAITING_SECOND_BLINK;
            return null;
        }
        if (segment.endMs - pendingShortBlink.startMs <= options.getDoubleBlinkWindowMs()) {
            BlinkEvent event = new BlinkEvent(
                    BlinkEventType.DOUBLE_BLINK,
                    pendingShortBlink.startMs,
                    segment.endMs,
                    confidenceForDoubleBlink(pendingShortBlink, segment)
            );
            pendingShortBlink = null;
            lastEvent = event;
            gesturePhase = GesturePhase.OPEN;
            return event;
        }
        BlinkEvent event = new BlinkEvent(
                BlinkEventType.SINGLE_BLINK,
                pendingShortBlink.startMs,
                pendingShortBlink.endMs,
                confidenceForShortBlink(pendingShortBlink)
        );
        pendingShortBlink = segment;
        gesturePhase = GesturePhase.WAITING_SECOND_BLINK;
        lastEvent = event;
        return event;
    }

    private BlinkEvent flushExpiredPending(long timestampMs) {
        if (pendingShortBlink == null
                || timestampMs - pendingShortBlink.startMs <= options.getDoubleBlinkWindowMs()) {
            return null;
        }
        BlinkEvent event = new BlinkEvent(
                BlinkEventType.SINGLE_BLINK,
                pendingShortBlink.startMs,
                pendingShortBlink.endMs,
                confidenceForShortBlink(pendingShortBlink)
        );
        pendingShortBlink = null;
        lastEvent = event;
        gesturePhase = GesturePhase.OPEN;
        return event;
    }

    private void beginClose(long timestampMs) {
        closedStartMs = timestampMs;
        eyesClosed = true;
        gesturePhase = GesturePhase.CLOSED;
    }

    private boolean resolveClosed(double averageOpenness) {
        if (eyesClosed) {
            return averageOpenness <= adaptiveOpenThreshold();
        }
        return averageOpenness < options.getClosedEyeThreshold()
                || isRelativeCloseCandidate(averageOpenness);
    }

    private boolean isRelativeCloseCandidate(double averageOpenness) {
        return openBaseline > 0d
                && openBaseline - averageOpenness >= RELATIVE_CLOSE_MIN_DROP
                && averageOpenness <= Math.max(options.getClosedEyeThreshold(),
                openBaseline * RELATIVE_CLOSE_RATIO);
    }

    private double adaptiveOpenThreshold() {
        if (openBaseline <= 0d) {
            return options.getOpenEyeThreshold();
        }
        return Math.max(options.getOpenEyeThreshold(), openBaseline * 0.78d);
    }

    private void updateOpenBaseline(double openness) {
        if (openness < options.getOpenEyeThreshold()) {
            return;
        }
        if (openBaseline <= 0d) {
            openBaseline = openness;
        } else if (openness > openBaseline) {
            openBaseline += (openness - openBaseline) * 0.30d;
        } else {
            openBaseline += (openness - openBaseline) * 0.02d;
        }
    }

    private double confidenceForShortBlink(BlinkSegment segment) {
        double durationRange = Math.max(1L, options.getMaxBlinkDurationMs() - options.getMinBlinkDurationMs());
        return clamp(0.55d + 0.35d * (segment.durationMs() - options.getMinBlinkDurationMs()) / durationRange);
    }

    private double confidenceForDoubleBlink(BlinkSegment first, BlinkSegment second) {
        return clamp(Math.min(confidenceForShortBlink(first), confidenceForShortBlink(second)) + 0.10d);
    }

    private double confidenceForLongClose(long durationMs) {
        return clamp(0.65d + 0.35d * durationMs / Math.max(1L, options.getLongCloseMinMs()));
    }

    private static double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private BlinkState readyState(long timestampMs, boolean facePresent) {
        return new BlinkState(
                BlinkState.Phase.READY,
                timestampMs,
                facePresent,
                lastEvent,
                lastError
        );
    }

    private void clearGestureState() {
        gesturePhase = GesturePhase.OPEN;
        eyesClosed = false;
        hasSeenOpenEyes = false;
        closedStartMs = -1L;
        pendingShortBlink = null;
    }

    private void resetSessionState() {
        clearGestureState();
        hasSeenFace = false;
        lastTimestampMs = -1L;
        lastFaceTimestampMs = -1L;
        openBaseline = 0d;
        lastEvent = null;
        lastError = null;
        state = BlinkState.idle();
    }

    private Runnable stateCallback(final BlinkState value) {
        return () -> safeStateChanged(value);
    }

    private Runnable diagnosticsCallback(final BlinkDiagnostics value) {
        return () -> safeDiagnostics(value);
    }

    private Runnable eventCallback(final BlinkEvent value) {
        return () -> safeEvent(value);
    }

    private Runnable errorCallback(final BlinkError value) {
        return () -> safeError(value);
    }

    private void dispatch(List<Runnable> callbacks) {
        for (Runnable callback : callbacks) {
            callbackExecutor.execute(callback);
        }
    }

    private List<Runnable> singleton(Runnable callback) {
        List<Runnable> callbacks = new ArrayList<>();
        callbacks.add(callback);
        return callbacks;
    }

    private void safeStateChanged(BlinkState value) {
        try {
            listener.onStateChanged(value);
        } catch (RuntimeException ignored) {
            // A host callback must not break the recognition session.
        }
    }

    private void safeEvent(BlinkEvent value) {
        try {
            listener.onEvent(value);
        } catch (RuntimeException ignored) {
            // A host callback must not break the recognition session.
        }
    }

    private void safeDiagnostics(BlinkDiagnostics value) {
        try {
            listener.onDiagnostics(value);
        } catch (RuntimeException ignored) {
            // A host callback must not break the recognition session.
        }
    }

    private void safeError(BlinkError value) {
        try {
            listener.onError(value);
        } catch (RuntimeException ignored) {
            // A host callback must not break the recognition session.
        }
    }
}
