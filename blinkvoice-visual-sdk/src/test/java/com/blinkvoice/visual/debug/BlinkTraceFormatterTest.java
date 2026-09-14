package com.blinkvoice.visual.debug;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BlinkTraceFormatterTest {
    @Test
    public void formatsFrameTraceWithStableCsvColumns() {
        String line = BlinkTraceFormatter.formatFrame(
                "20260708-094500",
                42,
                1_000L,
                33L,
                true,
                8.25f,
                9.75f,
                true,
                true,
                "CLOSED",
                "MICRO_REOPEN_CANDIDATE",
                120L,
                0L,
                "DOUBLE_BLINK",
                17L
        );

        assertEquals(
                "BVTRACE,frame,20260708-094500,42,1000,33,1,8.250,9.750,9.000,1,1,CLOSED,MICRO_REOPEN_CANDIDATE,120,0,DOUBLE_BLINK,17",
                line
        );
    }

    @Test
    public void formatsActionMarkerWithStableCsvColumns() {
        String line = BlinkTraceFormatter.formatMarker(
                "20260708-094500",
                4,
                "start",
                "FAST_DOUBLE_BLINK",
                "fast double blink x10"
        );

        assertEquals(
                "BVTRACE,mark,20260708-094500,4,start,FAST_DOUBLE_BLINK,fast double blink x10",
                line
        );
    }
}
