package com.blinkvoice.visual.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blinkvoice.visual.api.v2.BlinkDiagnostics;
import com.blinkvoice.visual.api.v2.BlinkError;
import com.blinkvoice.visual.api.v2.BlinkEvent;
import com.blinkvoice.visual.api.v2.BlinkEventType;
import com.blinkvoice.visual.api.v2.BlinkListener;
import com.blinkvoice.visual.api.v2.BlinkState;
import org.junit.Test;

public class BlinkElaSessionAdapterTest {
    @Test
    public void convertsElaAnglesIntoNormalizedFramesAndEvents() {
        RecordingListener listener = new RecordingListener();
        BlinkElaSessionAdapter adapter = new BlinkElaSessionAdapter(
                new BlinkCaptureOptions.Builder().setAutoFinishOnEvent(false).build(),
                listener,
                Runnable::run
        );
        adapter.start();

        assertTrue(adapter.submitElaFrame(0L, true, 72f, 72f, 0.95f));
        assertTrue(adapter.submitElaFrame(100L, true, 5f, 5f, 0.95f));
        assertTrue(adapter.submitElaFrame(180L, true, 72f, 72f, 0.95f));
        assertTrue(adapter.submitElaFrame(851L, true, 72f, 72f, 0.95f));

        assertNotNull(listener.lastEvent);
        assertEquals(BlinkEventType.SINGLE_BLINK, listener.lastEvent.getType());
        assertEquals(100L, listener.lastEvent.getStartTimeMs());
        assertEquals(180L, listener.lastEvent.getEndTimeMs());
        assertEquals(BlinkState.Phase.READY, adapter.getState().getPhase());
    }

    @Test
    public void treatsInvalidElaAsNoFaceInsteadOfSyntheticClosedEyes() {
        RecordingListener listener = new RecordingListener();
        BlinkElaSessionAdapter adapter = new BlinkElaSessionAdapter(
                new BlinkCaptureOptions.Builder().build(),
                listener,
                Runnable::run
        );
        adapter.start();

        assertTrue(adapter.submitElaFrame(0L, true, Float.NaN, 72f, 0.95f));

        assertNotNull(listener.lastDiagnostics);
        assertFalse(listener.lastDiagnostics.isFacePresent());
        assertEquals(BlinkState.Phase.WAITING_FOR_FACE, adapter.getState().getPhase());
    }

    @Test
    public void closeRejectsSubsequentFrames() {
        RecordingListener listener = new RecordingListener();
        BlinkElaSessionAdapter adapter = new BlinkElaSessionAdapter(
                new BlinkCaptureOptions.Builder().build(),
                listener,
                Runnable::run
        );
        adapter.start();
        adapter.close();

        assertFalse(adapter.submitElaFrame(0L, true, 72f, 72f, 0.95f));
        assertEquals(BlinkError.Code.CLOSED, listener.lastError.getCode());
    }

    private static final class RecordingListener implements BlinkListener {
        private BlinkEvent lastEvent;
        private BlinkError lastError;
        private BlinkDiagnostics lastDiagnostics;

        @Override
        public void onEvent(BlinkEvent event) {
            lastEvent = event;
        }

        @Override
        public void onError(BlinkError error) {
            lastError = error;
        }

        @Override
        public void onDiagnostics(BlinkDiagnostics diagnostics) {
            lastDiagnostics = diagnostics;
        }
    }
}
