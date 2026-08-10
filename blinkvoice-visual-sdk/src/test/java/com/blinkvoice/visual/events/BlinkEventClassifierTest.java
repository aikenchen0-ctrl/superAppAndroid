package com.blinkvoice.visual.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.blinkvoice.visual.api.BlinkCaptureOptions;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import org.junit.Test;

public class BlinkEventClassifierTest {
    @Test
    public void emitsSingleBlinkAfterDoubleBlinkWindowExpires() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 0.30f, 0.30f));
        assertNull(classifier.accept(100L, true, 0.10f, 0.10f));
        assertNull(classifier.accept(180L, true, 0.30f, 0.30f));

        BlinkCaptureResult result = classifier.accept(751L, true, 0.30f, 0.30f);

        assertEquals(BlinkEventType.SINGLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(180L, result.getEndTimeMs());
        assertEquals(80L, result.getDurationMs());
    }

    @Test
    public void emitsDoubleBlinkWhenSecondShortBlinkEndsInsideWindow() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 0.30f, 0.30f));
        assertNull(classifier.accept(100L, true, 0.10f, 0.10f));
        assertNull(classifier.accept(180L, true, 0.30f, 0.30f));
        assertNull(classifier.accept(300L, true, 0.10f, 0.10f));

        BlinkCaptureResult result = classifier.accept(370L, true, 0.30f, 0.30f);

        assertEquals(BlinkEventType.DOUBLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(370L, result.getEndTimeMs());
        assertEquals(270L, result.getDurationMs());
    }

    @Test
    public void emitsDoubleBlinkWhenTwoShortBlinksFinishInsideTotalWindow() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 0.30f, 0.30f));
        assertNull(classifier.accept(100L, true, 0.10f, 0.10f));
        assertNull(classifier.accept(180L, true, 0.30f, 0.30f));
        assertNull(classifier.accept(620L, true, 0.10f, 0.10f));

        BlinkCaptureResult result = classifier.accept(740L, true, 0.30f, 0.30f);

        assertEquals(BlinkEventType.DOUBLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(740L, result.getEndTimeMs());
        assertEquals(640L, result.getDurationMs());
    }

    @Test
    public void emitsSingleBlinkWhenSecondShortBlinkFinishesOutsideTotalWindow() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 0.30f, 0.30f));
        assertNull(classifier.accept(100L, true, 0.10f, 0.10f));
        assertNull(classifier.accept(180L, true, 0.30f, 0.30f));
        assertNull(classifier.accept(620L, true, 0.10f, 0.10f));

        BlinkCaptureResult result = classifier.accept(751L, true, 0.30f, 0.30f);

        assertEquals(BlinkEventType.SINGLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(180L, result.getEndTimeMs());
        assertEquals(80L, result.getDurationMs());
    }

    @Test
    public void emitsLongCloseOnceUntilEyesOpenAgain() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 0.30f, 0.30f));
        assertNull(classifier.accept(100L, true, 0.10f, 0.10f));

        BlinkCaptureResult result = classifier.accept(600L, true, 0.10f, 0.10f);

        assertEquals(BlinkEventType.LONG_CLOSE, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(600L, result.getEndTimeMs());
        assertEquals(500L, result.getDurationMs());
        assertNull(classifier.accept(650L, true, 0.10f, 0.10f));
    }

    @Test
    public void ignoresBlinkShorterThanMinimumFrameDuration() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 0.30f, 0.30f));
        assertNull(classifier.accept(100L, true, 0.10f, 0.10f));
        assertNull(classifier.accept(120L, true, 0.30f, 0.30f));

        assertNull(classifier.accept(751L, true, 0.30f, 0.30f));
    }

    @Test
    public void ignoresClosedStartupUntilOpenEyesAreSeen() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 0.10f, 0.10f));
        assertEquals("WAITING_OPEN_EYES", classifier.getDebugSnapshot().getPhase());
        assertEquals("WAITING_OPEN_EYES", classifier.getDebugSnapshot().getLastReason());
        assertNull(classifier.accept(450L, true, 0.10f, 0.10f));
        assertNull(classifier.accept(900L, true, 0.30f, 0.30f));
        assertEquals("OPEN", classifier.getDebugSnapshot().getPhase());
        assertEquals(true, classifier.getDebugSnapshot().hasSeenOpenEyes());
        assertNull(classifier.accept(1000L, true, 0.10f, 0.10f));
        assertEquals("CLOSED", classifier.getDebugSnapshot().getPhase());
        assertEquals("CLOSE_STARTED", classifier.getDebugSnapshot().getLastReason());

        BlinkCaptureResult result = classifier.accept(1500L, true, 0.10f, 0.10f);

        assertEquals(BlinkEventType.LONG_CLOSE, result.getEventType());
        assertEquals(1000L, result.getStartTimeMs());
        assertEquals(1500L, result.getEndTimeMs());
        assertEquals(500L, result.getDurationMs());
        assertEquals("LONG_CLOSE", classifier.getDebugSnapshot().getLastEvent());
    }
}
