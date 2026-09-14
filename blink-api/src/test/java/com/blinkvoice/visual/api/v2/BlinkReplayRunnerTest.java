package com.blinkvoice.visual.api.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.List;
import org.junit.Test;

public class BlinkReplayRunnerTest {
    @Test
    public void runsNormalizedFramesThroughSessionAndProducesEvents() throws Exception {
        String csv = "source,timestamp_ms,face_present,left_eye_openness,right_eye_openness,face_confidence\n"
                + "case-a,0,1,0.9,0.9,0.95\n"
                + "case-a,100,1,0.05,0.05,0.95\n"
                + "case-a,180,1,0.9,0.9,0.95\n"
                + "case-a,851,1,0.9,0.9,0.95\n";

        List<BlinkReplayEvent> events = BlinkReplayRunner.run(
                BlinkReplayFrameCsv.read(new StringReader(csv)),
                new BlinkOptions.Builder().setStopOnEvent(false).build()
        );

        assertEquals(1, events.size());
        assertEquals("case-a", events.get(0).getCaseId());
        assertEquals(BlinkEventType.SINGLE_BLINK, events.get(0).getEvent().getType());
    }

    @Test
    public void replayOutputCanBeEvaluatedAgainstExpectedEvents() throws Exception {
        String frames = "source,timestamp_ms,face_present,left_eye_openness,right_eye_openness,face_confidence\n"
                + "case-a,0,1,0.9,0.9,0.95\n"
                + "case-a,100,1,0.05,0.05,0.95\n"
                + "case-a,180,1,0.9,0.9,0.95\n"
                + "case-a,851,1,0.9,0.9,0.95\n";
        String expected = "source,event_type,start_ms,end_ms,confidence\n"
                + "case-a,SINGLE_BLINK,100,180,1.0\n";

        List<BlinkReplayEvent> predicted = BlinkReplayRunner.run(
                BlinkReplayFrameCsv.read(new StringReader(frames)),
                new BlinkOptions.Builder().setStopOnEvent(false).build()
        );
        BlinkReplayReport report = BlinkReplayReport.evaluate(
                BlinkReplayCsv.read(new StringReader(expected)),
                predicted,
                0L
        );

        assertEquals(1, report.getOverall().getTruePositiveCount());
        assertTrue(report.getOverall().getPrecision() > 0.99d);
    }
}
