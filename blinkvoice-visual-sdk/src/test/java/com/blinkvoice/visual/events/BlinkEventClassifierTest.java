package com.blinkvoice.visual.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.blinkvoice.visual.api.BlinkCaptureOptions;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import org.junit.Test;

public class BlinkEventClassifierTest {
    @Test
    public void emitsSingleBlinkAfterDoubleBlinkWindowExpires() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 20f, 20f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(180L, true, 20f, 20f));

        BlinkCaptureResult result = classifier.accept(751L, true, 20f, 20f);

        assertEquals(BlinkEventType.SINGLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(180L, result.getEndTimeMs());
        assertEquals(80L, result.getDurationMs());
    }

    @Test
    public void emitsDoubleBlinkWhenSecondShortBlinkEndsInsideWindow() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 20f, 20f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(180L, true, 20f, 20f));
        assertNull(classifier.accept(300L, true, 5f, 5f));

        BlinkCaptureResult result = classifier.accept(370L, true, 20f, 20f);

        assertEquals(BlinkEventType.DOUBLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(370L, result.getEndTimeMs());
        assertEquals(270L, result.getDurationMs());
    }

    @Test
    public void emitsDoubleBlinkWhenBriefReopenOnlyReachesHysteresisBand() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 20f, 20f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(150L, true, 12f, 12f));
        assertNull(classifier.accept(180L, true, 5f, 5f));

        BlinkCaptureResult result = classifier.accept(230L, true, 20f, 20f);

        assertEquals(BlinkEventType.DOUBLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(230L, result.getEndTimeMs());
        assertEquals(130L, result.getDurationMs());
    }

    @Test
    public void emitsDoubleBlinkWhenMiddleReopenStaysBelowCloseThreshold() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 20f, 20f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(150L, true, 9f, 9f));
        assertNull(classifier.accept(180L, true, 5f, 5f));

        BlinkCaptureResult result = classifier.accept(230L, true, 20f, 20f);

        assertEquals(BlinkEventType.DOUBLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(230L, result.getEndTimeMs());
        assertEquals(130L, result.getDurationMs());
    }

    @Test
    public void emitsDoubleBlinkWhenSecondCloseStartsBeforeEyesReachCloseThresholdBand() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 20f, 20f));
        assertNull(classifier.accept(100L, true, 4f, 4f));
        assertNull(classifier.accept(160L, true, 7.6f, 7.6f));
        assertNull(classifier.accept(190L, true, 5f, 5f));

        BlinkCaptureResult result = classifier.accept(250L, true, 20f, 20f);

        assertEquals(BlinkEventType.DOUBLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(250L, result.getEndTimeMs());
        assertEquals(150L, result.getDurationMs());
    }

    @Test
    public void emitsDoubleBlinkWhenTwoShortBlinksFinishInsideTotalWindow() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 20f, 20f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(180L, true, 20f, 20f));
        assertNull(classifier.accept(620L, true, 5f, 5f));

        BlinkCaptureResult result = classifier.accept(740L, true, 20f, 20f);

        assertEquals(BlinkEventType.DOUBLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(740L, result.getEndTimeMs());
        assertEquals(640L, result.getDurationMs());
    }

    @Test
    public void emitsDoubleBlinkWhenSecondBlinkIsShallowAfterClearReopen() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 72f, 72f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(180L, true, 72f, 72f));
        assertNull(classifier.accept(220L, true, 70f, 70f));
        assertNull(classifier.accept(264L, true, 12.8f, 19.8f));

        BlinkCaptureResult result = classifier.accept(315L, true, 72f, 72f);

        assertEquals(BlinkEventType.DOUBLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(315L, result.getEndTimeMs());
        assertEquals(215L, result.getDurationMs());
    }

    @Test
    public void emitsSingleBlinkWhenHighOpenBaselineHasNaturalLightClose() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 72f, 72f));
        assertNull(classifier.accept(40L, true, 70f, 70f));
        assertNull(classifier.accept(100L, true, 18f, 18f));
        assertNull(classifier.accept(180L, true, 72f, 72f));

        BlinkCaptureResult result = classifier.accept(751L, true, 72f, 72f);

        assertEquals(BlinkEventType.SINGLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(180L, result.getEndTimeMs());
        assertEquals(80L, result.getDurationMs());
    }

    @Test
    public void emitsDoubleBlinkWhenFastSecondBlinkIsRelativeDropButAboveAbsoluteCloseThreshold() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 72f, 72f));
        assertNull(classifier.accept(40L, true, 70f, 70f));
        assertNull(classifier.accept(100L, true, 18f, 18f));
        assertNull(classifier.accept(180L, true, 72f, 72f));
        assertNull(classifier.accept(220L, true, 70f, 70f));
        assertNull(classifier.accept(260L, true, 28f, 28f));

        BlinkCaptureResult result = classifier.accept(320L, true, 72f, 72f);

        assertEquals(BlinkEventType.DOUBLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(320L, result.getEndTimeMs());
        assertEquals(220L, result.getDurationMs());
    }

    @Test
    public void keepsSingleBlinkWhenOnlyImmediateResidualDipFollowsFirstBlink() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 72f, 72f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(180L, true, 16f, 16f));
        assertNull(classifier.accept(210L, true, 9.6f, 21.3f));
        assertNull(classifier.accept(260L, true, 72f, 72f));

        BlinkCaptureResult result = classifier.accept(751L, true, 72f, 72f);

        assertEquals(BlinkEventType.SINGLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(180L, result.getEndTimeMs());
        assertEquals(80L, result.getDurationMs());
    }

    @Test
    public void emitsSingleBlinkWhenSecondShortBlinkFinishesOutsideTotalWindow() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 20f, 20f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(180L, true, 20f, 20f));
        assertNull(classifier.accept(620L, true, 5f, 5f));

        BlinkCaptureResult result = classifier.accept(751L, true, 20f, 20f);

        assertEquals(BlinkEventType.SINGLE_BLINK, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(180L, result.getEndTimeMs());
        assertEquals(80L, result.getDurationMs());
    }

    @Test
    public void emitsLongCloseOnceUntilEyesOpenAgain() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 20f, 20f));
        assertNull(classifier.accept(100L, true, 5f, 5f));

        BlinkCaptureResult result = classifier.accept(375L, true, 5f, 5f);

        assertEquals(BlinkEventType.LONG_CLOSE, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(375L, result.getEndTimeMs());
        assertEquals(275L, result.getDurationMs());
        assertNull(classifier.accept(425L, true, 5f, 5f));
    }

    @Test
    public void emitsLongCloseOnScheduleWhenClosedEyeNoiseLooksLikeMicroReopen() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 20f, 20f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(150L, true, 9f, 9f));
        assertNull(classifier.accept(180L, true, 5f, 5f));

        BlinkCaptureResult result = classifier.accept(375L, true, 5f, 5f);

        assertEquals(BlinkEventType.LONG_CLOSE, result.getEventType());
        assertEquals(100L, result.getStartTimeMs());
        assertEquals(375L, result.getEndTimeMs());
        assertEquals(275L, result.getDurationMs());
    }

    @Test
    public void ignoresBlinkShorterThanMinimumFrameDuration() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 20f, 20f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(120L, true, 20f, 20f));

        assertNull(classifier.accept(751L, true, 20f, 20f));
    }

    @Test
    public void ignoresClosedStartupUntilOpenEyesAreSeen() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 5f, 5f));
        assertEquals("WAITING_OPEN_EYES", classifier.getDebugSnapshot().getPhase());
        assertEquals("WAITING_OPEN_EYES", classifier.getDebugSnapshot().getLastReason());
        assertNull(classifier.accept(450L, true, 5f, 5f));
        assertNull(classifier.accept(900L, true, 20f, 20f));
        assertEquals("OPEN", classifier.getDebugSnapshot().getPhase());
        assertEquals(true, classifier.getDebugSnapshot().hasSeenOpenEyes());
        assertNull(classifier.accept(1000L, true, 5f, 5f));
        assertEquals("CLOSED", classifier.getDebugSnapshot().getPhase());
        assertEquals("CLOSE_STARTED", classifier.getDebugSnapshot().getLastReason());

        BlinkCaptureResult result = classifier.accept(1275L, true, 5f, 5f);

        assertEquals(BlinkEventType.LONG_CLOSE, result.getEventType());
        assertEquals(1000L, result.getStartTimeMs());
        assertEquals(1275L, result.getEndTimeMs());
        assertEquals(275L, result.getDurationMs());
        assertEquals("LONG_CLOSE", classifier.getDebugSnapshot().getLastEvent());
    }

    @Test
    public void clearsFirstBlinkAfterGrayZoneBeforeAcceptingAnotherBlink() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 35f, 35f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(180L, true, 35f, 35f));

        assertNull(classifier.accept(300L, true, 5f, 5f));
        assertNull(classifier.accept(551L, true, 5f, 5f));
        assertNull(classifier.accept(600L, true, 35f, 35f));

        assertNull(classifier.accept(620L, true, 5f, 5f));
        assertNull(classifier.accept(700L, true, 35f, 35f));

        BlinkCaptureResult result = classifier.accept(1400L, true, 35f, 35f);

        assertEquals(BlinkEventType.SINGLE_BLINK, result.getEventType());
        assertEquals(620L, result.getStartTimeMs());
        assertEquals(700L, result.getEndTimeMs());
    }

    @Test
    public void marksShallowSecondBlinkAsClosedInDebugSnapshot() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(0L, true, 35f, 35f));
        assertNull(classifier.accept(100L, true, 5f, 5f));
        assertNull(classifier.accept(180L, true, 35f, 35f));
        assertNull(classifier.accept(220L, true, 35f, 35f));
        assertNull(classifier.accept(260L, true, 12f, 28f));

        assertEquals("CLOSED", classifier.getDebugSnapshot().getPhase());
        assertTrue(classifier.getDebugSnapshot().isClosed());
    }

    @Test
    public void ignoresOutOfOrderFramesInsteadOfCreatingNegativeDurationEvents() {
        BlinkEventClassifier classifier = new BlinkEventClassifier(new BlinkCaptureOptions.Builder().build());

        assertNull(classifier.accept(100L, true, 35f, 35f));
        assertNull(classifier.accept(200L, true, 5f, 5f));
        assertNull(classifier.accept(150L, true, 35f, 35f));

        assertEquals("CLOSED", classifier.getDebugSnapshot().getPhase());
        assertEquals(0L, classifier.getDebugSnapshot().getClosedDurationMs());
    }
}
