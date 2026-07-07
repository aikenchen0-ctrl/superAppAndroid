package com.example.eyeblinkdetect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoEarThresholdCalibratorTest {
    @Test
    public void startsWithDefaultThresholdsBeforeSamples() {
        AutoEarThresholdCalibrator calibrator = new AutoEarThresholdCalibrator();

        AutoEarThresholdCalibrator.Recommendation recommendation = calibrator.getRecommendation();

        assertEquals(0.22f, recommendation.getCloseThreshold(), 0.0001f);
        assertEquals(0.25f, recommendation.getOpenThreshold(), 0.0001f);
        assertFalse(recommendation.hasOpenBaseline());
        assertFalse(recommendation.hasClosedSample());
    }

    @Test
    public void estimatesThresholdsFromOpenEyesOnly() {
        AutoEarThresholdCalibrator calibrator = new AutoEarThresholdCalibrator();

        calibrator.addSample(0.30f, false);
        calibrator.addSample(0.32f, false);

        AutoEarThresholdCalibrator.Recommendation recommendation = calibrator.getRecommendation();

        assertEquals(0.32f, recommendation.getOpenBaseline(), 0.0001f);
        assertEquals(0.24f, recommendation.getCloseThreshold(), 0.0001f);
        assertEquals(0.272f, recommendation.getOpenThreshold(), 0.0001f);
        assertTrue(recommendation.hasOpenBaseline());
        assertFalse(recommendation.hasClosedSample());
        assertEquals("OPEN_ONLY", recommendation.getMode());
    }

    @Test
    public void usesGapBetweenOpenAndClosedWhenClosedSampleExists() {
        AutoEarThresholdCalibrator calibrator = new AutoEarThresholdCalibrator();

        calibrator.addSample(0.30f, false);
        calibrator.addSample(0.12f, true);

        AutoEarThresholdCalibrator.Recommendation recommendation = calibrator.getRecommendation();

        assertEquals(0.30f, recommendation.getOpenBaseline(), 0.0001f);
        assertEquals(0.12f, recommendation.getClosedMinimum(), 0.0001f);
        assertEquals(0.219f, recommendation.getCloseThreshold(), 0.0001f);
        assertEquals(0.244f, recommendation.getOpenThreshold(), 0.0001f);
        assertTrue(recommendation.hasClosedSample());
        assertEquals("OPEN_CLOSED_GAP", recommendation.getMode());
    }
}
