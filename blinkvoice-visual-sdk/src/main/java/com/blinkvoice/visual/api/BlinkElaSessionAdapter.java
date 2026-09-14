package com.blinkvoice.visual.api;

import com.blinkvoice.visual.api.v2.BlinkEventType;
import com.blinkvoice.visual.api.v2.BlinkFrame;
import com.blinkvoice.visual.api.v2.BlinkListener;
import com.blinkvoice.visual.api.v2.BlinkOptions;
import com.blinkvoice.visual.api.v2.BlinkSession;
import com.blinkvoice.visual.api.v2.BlinkState;
import com.blinkvoice.visual.api.v2.DefaultBlinkSessionFactory;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Converts Android detector-independent ELA observations into the platform-neutral v2 session.
 * This adapter intentionally accepts values, not MediaPipe or CameraX objects.
 */
public final class BlinkElaSessionAdapter implements AutoCloseable {
    private static final double OPEN_ANGLE_DEGREES = 90d;

    private final DefaultBlinkSessionFactory factory;
    private final BlinkSession session;

    public BlinkElaSessionAdapter(BlinkCaptureOptions options, BlinkListener listener) {
        this(options, listener, null);
    }

    public BlinkElaSessionAdapter(
            BlinkCaptureOptions options,
            BlinkListener listener,
            Executor callbackExecutor
    ) {
        BlinkCaptureOptions safeOptions = options != null
                ? options
                : new BlinkCaptureOptions.Builder().build();
        Objects.requireNonNull(listener, "listener");
        factory = callbackExecutor == null
                ? new DefaultBlinkSessionFactory()
                : new DefaultBlinkSessionFactory(callbackExecutor);
        session = factory.create(toBlinkOptions(safeOptions), listener);
    }

    public void start() {
        session.start();
    }

    public void stop() {
        session.stop();
    }

    @Override
    public void close() {
        session.close();
        factory.close();
    }

    public BlinkState getState() {
        return session.getState();
    }

    /**
     * Submits one detector observation. Invalid ELA values are treated as no-face observations,
     * preventing incomplete landmarks from becoming synthetic closed-eye frames.
     */
    public boolean submitElaFrame(
            long timestampMs,
            boolean hasFace,
            float leftEla,
            float rightEla,
            float faceConfidence
    ) {
        if (!hasFace || !isValidEla(leftEla) || !isValidEla(rightEla)) {
            return session.submitFrame(BlinkFrame.noFace(timestampMs));
        }
        return session.submitFrame(new BlinkFrame(
                timestampMs,
                true,
                toNormalizedOpenness(leftEla),
                toNormalizedOpenness(rightEla),
                normalizeConfidence(faceConfidence)
        ));
    }

    static double toNormalizedOpenness(float elaDegrees) {
        if (!isValidEla(elaDegrees)) {
            return Double.NaN;
        }
        return Math.max(0d, Math.min(1d, elaDegrees / OPEN_ANGLE_DEGREES));
    }

    private static boolean isValidEla(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value) && value >= 0f && value <= 180f;
    }

    private static double normalizeConfidence(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, value));
    }

    private static BlinkOptions toBlinkOptions(BlinkCaptureOptions options) {
        Set<BlinkEventType> eventTypes = EnumSet.noneOf(BlinkEventType.class);
        for (com.blinkvoice.visual.api.BlinkEventType eventType : options.getEventTypes()) {
            eventTypes.add(BlinkEventType.valueOf(eventType.name()));
        }

        return new BlinkOptions.Builder()
                .setClosedEyeThreshold(toNormalizedOpenness(options.getElaCloseThreshold()))
                .setOpenEyeThreshold(toNormalizedOpenness(options.getElaOpenThreshold()))
                .setDoubleBlinkWindowMs(options.getDoubleBlinkWindowMs())
                .setLongCloseMinMs(options.getLongCloseMinMs())
                .setMinBlinkDurationMs(options.getMinShortBlinkMs())
                .setMaxBlinkDurationMs(options.getMaxShortBlinkMs())
                .setNoFaceResetMs(options.getNoFaceResetMs())
                .setStopOnEvent(options.isAutoFinishOnEvent())
                .setEventTypes(eventTypes)
                .build();
    }
}
