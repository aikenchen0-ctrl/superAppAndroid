package com.blinkvoice.visual.api.v2;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class BlinkEventEvaluatorTest {
    @Test
    public void computesClassificationMetricsAndLatencyForOneToOneMatches() {
        BlinkEvent expectedSingle = event(BlinkEventType.SINGLE_BLINK, 100L, 180L);
        BlinkEvent expectedDouble = event(BlinkEventType.DOUBLE_BLINK, 400L, 650L);
        BlinkEvent predictedSingle = event(BlinkEventType.SINGLE_BLINK, 130L, 210L);
        BlinkEvent predictedLong = event(BlinkEventType.LONG_CLOSE, 900L, 1300L);

        BlinkEvaluationMetrics metrics = BlinkEventEvaluator.evaluate(
                Arrays.asList(expectedSingle, expectedDouble),
                Arrays.asList(predictedSingle, predictedLong),
                40L
        );

        assertEquals(1, metrics.getTruePositiveCount());
        assertEquals(1, metrics.getFalsePositiveCount());
        assertEquals(1, metrics.getFalseNegativeCount());
        assertEquals(0.5d, metrics.getPrecision(), 0.0001d);
        assertEquals(0.5d, metrics.getRecall(), 0.0001d);
        assertEquals(0.5d, metrics.getF1(), 0.0001d);
        assertEquals(0.5d, metrics.getFalsePositiveRate(), 0.0001d);
        assertEquals(0.5d, metrics.getMissRate(), 0.0001d);
        assertEquals(30d, metrics.getMeanLatencyMs(), 0.0001d);
        assertEquals(30d, metrics.getP95AbsoluteLatencyMs(), 0.0001d);
    }

    @Test
    public void doesNotMatchTwoPredictionsToOneGroundTruthEvent() {
        BlinkEvent expected = event(BlinkEventType.SINGLE_BLINK, 100L, 180L);
        BlinkEvent firstPrediction = event(BlinkEventType.SINGLE_BLINK, 90L, 170L);
        BlinkEvent secondPrediction = event(BlinkEventType.SINGLE_BLINK, 110L, 190L);

        BlinkEvaluationMetrics metrics = BlinkEventEvaluator.evaluate(
                Collections.singletonList(expected),
                Arrays.asList(firstPrediction, secondPrediction),
                20L
        );

        assertEquals(1, metrics.getTruePositiveCount());
        assertEquals(1, metrics.getFalsePositiveCount());
        assertEquals(0, metrics.getFalseNegativeCount());
    }

    @Test
    public void maximizesMatchCountBeforeMinimizingTimingError() {
        BlinkEvent expectedFirst = event(BlinkEventType.SINGLE_BLINK, 100L, 150L);
        BlinkEvent expectedSecond = event(BlinkEventType.SINGLE_BLINK, 120L, 170L);
        BlinkEvent predictedEarly = event(BlinkEventType.SINGLE_BLINK, 80L, 130L);
        BlinkEvent predictedMiddle = event(BlinkEventType.SINGLE_BLINK, 110L, 160L);

        BlinkEvaluationMetrics metrics = BlinkEventEvaluator.evaluate(
                Arrays.asList(expectedFirst, expectedSecond),
                Arrays.asList(predictedEarly, predictedMiddle),
                20L
        );

        assertEquals(2, metrics.getTruePositiveCount());
        assertEquals(0, metrics.getFalsePositiveCount());
        assertEquals(0, metrics.getFalseNegativeCount());
    }

    @Test
    public void emptyReplayHasPerfectClassificationMetricsAndZeroLatency() {
        BlinkEvaluationMetrics metrics = BlinkEventEvaluator.evaluate(
                Collections.emptyList(),
                Collections.emptyList(),
                0L
        );

        assertEquals(1d, metrics.getPrecision(), 0.0001d);
        assertEquals(1d, metrics.getRecall(), 0.0001d);
        assertEquals(1d, metrics.getF1(), 0.0001d);
        assertEquals(0d, metrics.getMeanLatencyMs(), 0.0001d);
        assertEquals(0d, metrics.getP95AbsoluteLatencyMs(), 0.0001d);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeMatchTolerance() {
        BlinkEventEvaluator.evaluate(
                Collections.emptyList(),
                Collections.emptyList(),
                -1L
        );
    }

    private static BlinkEvent event(BlinkEventType type, long startMs, long endMs) {
        return new BlinkEvent(type, startMs, endMs, 0.9d);
    }
}
