package com.blinkvoice.visual.performance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnalysisFrameGateTest {
    @Test
    public void acceptsFirstFrameAndDropsFramesInsideInterval() {
        AnalysisFrameGate gate = new AnalysisFrameGate(20);

        assertTrue(gate.shouldAnalyze(1_000L));
        assertFalse(gate.shouldAnalyze(1_030L));
        assertTrue(gate.shouldAnalyze(1_050L));
    }

    @Test
    public void nonPositiveFpsDisablesThrottling() {
        AnalysisFrameGate gate = new AnalysisFrameGate(0);

        assertTrue(gate.shouldAnalyze(1_000L));
        assertTrue(gate.shouldAnalyze(1_001L));
    }

    @Test
    public void resetAcceptsNextFrameImmediately() {
        AnalysisFrameGate gate = new AnalysisFrameGate(20);

        assertTrue(gate.shouldAnalyze(1_000L));
        assertFalse(gate.shouldAnalyze(1_010L));
        gate.reset();

        assertTrue(gate.shouldAnalyze(1_011L));
    }
}
