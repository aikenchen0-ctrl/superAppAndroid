package com.blinkvoice.visual.api.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;

public class DefaultBlinkSessionTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    public void rejectsFramesBeforeStartAndReportsStableError() {
        RecordingListener listener = new RecordingListener();
        BlinkSession session = new DefaultBlinkSessionFactory(DIRECT_EXECUTOR)
                .create(new BlinkOptions.Builder().build(), listener);

        assertFalse(session.submitFrame(openFrame(0L)));
        assertEquals(BlinkError.Code.NOT_STARTED, listener.lastError.getCode());
        assertEquals(BlinkState.Phase.IDLE, session.getState().getPhase());
    }

    @Test
    public void emitsSingleBlinkAfterWindowExpires() {
        RecordingListener listener = new RecordingListener();
        BlinkSession session = newSession(listener);
        session.start();

        assertTrue(session.submitFrame(openFrame(0L)));
        assertTrue(session.submitFrame(openFrame(40L)));
        assertTrue(session.submitFrame(closedFrame(100L)));
        assertTrue(session.submitFrame(openFrame(180L)));
        assertNull(listener.lastEvent);

        assertTrue(session.submitFrame(openFrame(851L)));

        assertNotNull(listener.lastEvent);
        assertEquals(BlinkEventType.SINGLE_BLINK, listener.lastEvent.getType());
        assertEquals(100L, listener.lastEvent.getStartTimeMs());
        assertEquals(180L, listener.lastEvent.getEndTimeMs());
    }

    @Test
    public void emitsDoubleBlinkWithinConfiguredWindow() {
        RecordingListener listener = new RecordingListener();
        BlinkSession session = newSession(listener);
        session.start();

        session.submitFrame(openFrame(0L));
        session.submitFrame(closedFrame(100L));
        session.submitFrame(openFrame(180L));
        session.submitFrame(closedFrame(300L));
        session.submitFrame(openFrame(370L));

        assertNotNull(listener.lastEvent);
        assertEquals(BlinkEventType.DOUBLE_BLINK, listener.lastEvent.getType());
        assertEquals(100L, listener.lastEvent.getStartTimeMs());
        assertEquals(370L, listener.lastEvent.getEndTimeMs());
    }

    @Test
    public void emitsLongCloseOnlyOnceUntilReopen() {
        RecordingListener listener = new RecordingListener();
        BlinkOptions options = new BlinkOptions.Builder()
                .setLongCloseMinMs(275L)
                .setStopOnEvent(false)
                .build();
        BlinkSession session = new DefaultBlinkSessionFactory(DIRECT_EXECUTOR).create(options, listener);
        session.start();

        session.submitFrame(openFrame(0L));
        session.submitFrame(closedFrame(100L));
        session.submitFrame(closedFrame(375L));
        assertEquals(BlinkEventType.LONG_CLOSE, listener.lastEvent.getType());
        int eventCount = listener.events.size();

        session.submitFrame(closedFrame(450L));
        assertEquals(eventCount, listener.events.size());
        session.submitFrame(openFrame(500L));
        assertEquals(BlinkState.Phase.READY, session.getState().getPhase());
    }

    @Test
    public void ignoresOutOfOrderFrameWithoutChangingState() {
        RecordingListener listener = new RecordingListener();
        BlinkSession session = newSession(listener);
        session.start();
        session.submitFrame(openFrame(100L));
        session.submitFrame(closedFrame(200L));

        assertFalse(session.submitFrame(openFrame(150L)));
        assertEquals(BlinkError.Code.OUT_OF_ORDER_FRAME, listener.lastError.getCode());
        assertEquals(BlinkState.Phase.EYES_CLOSED, session.getState().getPhase());
    }

    @Test
    public void noFaceResetsPendingGestureAfterConfiguredGap() {
        RecordingListener listener = new RecordingListener();
        BlinkOptions options = new BlinkOptions.Builder()
                .setNoFaceResetMs(200L)
                .build();
        BlinkSession session = new DefaultBlinkSessionFactory(DIRECT_EXECUTOR).create(options, listener);
        session.start();
        session.submitFrame(openFrame(0L));
        session.submitFrame(closedFrame(100L));
        session.submitFrame(BlinkFrame.noFace(350L));
        session.submitFrame(openFrame(400L));
        session.submitFrame(closedFrame(500L));
        session.submitFrame(openFrame(580L));
        session.submitFrame(openFrame(1200L));

        assertEquals(BlinkEventType.SINGLE_BLINK, listener.lastEvent.getType());
        assertEquals(500L, listener.lastEvent.getStartTimeMs());
    }

    @Test
    public void stopIsTerminalForProcessingButCloseIsIdempotent() {
        RecordingListener listener = new RecordingListener();
        BlinkSession session = newSession(listener);
        session.start();
        session.stop();
        assertEquals(BlinkState.Phase.STOPPED, session.getState().getPhase());
        assertFalse(session.submitFrame(openFrame(10L)));
        assertEquals(BlinkError.Code.NOT_STARTED, listener.lastError.getCode());
        session.close();
        session.close();
        assertEquals(BlinkState.Phase.CLOSED, session.getState().getPhase());
    }

    @Test
    public void stopAllowsAResetAndRestartBeforeClose() {
        RecordingListener listener = new RecordingListener();
        BlinkSession session = newSession(listener);
        session.start();
        session.submitFrame(openFrame(0L));
        session.submitFrame(closedFrame(100L));
        session.stop();

        session.start();
        assertTrue(session.submitFrame(openFrame(0L)));
        assertTrue(session.submitFrame(closedFrame(100L)));
        assertTrue(session.submitFrame(openFrame(180L)));
        session.submitFrame(openFrame(900L));

        assertEquals(BlinkEventType.SINGLE_BLINK, listener.lastEvent.getType());
        assertEquals(100L, listener.lastEvent.getStartTimeMs());
    }

    private static BlinkSession newSession(RecordingListener listener) {
        return new DefaultBlinkSessionFactory(DIRECT_EXECUTOR)
                .create(new BlinkOptions.Builder().build(), listener);
    }

    private static BlinkFrame openFrame(long timestampMs) {
        return new BlinkFrame(timestampMs, true, 0.9d, 0.9d, 0.95d);
    }

    private static BlinkFrame closedFrame(long timestampMs) {
        return new BlinkFrame(timestampMs, true, 0.05d, 0.05d, 0.95d);
    }

    private static final class RecordingListener implements BlinkListener {
        private final List<BlinkEvent> events = new ArrayList<>();
        private BlinkEvent lastEvent;
        private BlinkError lastError;

        @Override
        public void onEvent(BlinkEvent event) {
            lastEvent = event;
            events.add(event);
        }

        @Override
        public void onError(BlinkError error) {
            lastError = error;
        }
    }
}
