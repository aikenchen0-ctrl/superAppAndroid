package com.blinkvoice.visual.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BlinkVoiceStartupTimingTest {
    @Test
    public void storesStartupStageElapsedTimeAndDetail() {
        BlinkVoiceStartupTiming timing = new BlinkVoiceStartupTiming(
                BlinkVoiceStartupTiming.STAGE_CAMERA_BOUND,
                123L,
                "front"
        );

        assertEquals(BlinkVoiceStartupTiming.STAGE_CAMERA_BOUND, timing.getStage());
        assertEquals(123L, timing.getElapsedMs());
        assertEquals("front", timing.getDetail());
    }

    @Test
    public void normalizesNullDetailAndNegativeElapsedTime() {
        BlinkVoiceStartupTiming timing = new BlinkVoiceStartupTiming(
                BlinkVoiceStartupTiming.STAGE_FIRST_RESULT,
                -1L,
                null
        );

        assertEquals(0L, timing.getElapsedMs());
        assertEquals("", timing.getDetail());
    }
}
