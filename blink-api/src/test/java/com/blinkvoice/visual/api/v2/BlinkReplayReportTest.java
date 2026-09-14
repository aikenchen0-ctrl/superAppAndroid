package com.blinkvoice.visual.api.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import org.junit.Test;

public class BlinkReplayReportTest {
    @Test
    public void evaluatesCasesIndependentlyAndProducesJsonSummary() throws Exception {
        String expected = "source,event_type,start_ms,end_ms,confidence\n"
                + "case-a,SINGLE_BLINK,100,180,1.0\n"
                + "case-b,DOUBLE_BLINK,300,500,1.0\n";
        String predicted = "source,event_type,start_ms,end_ms,confidence\n"
                + "case-a,SINGLE_BLINK,120,200,0.8\n"
                + "case-a,LONG_CLOSE,900,1200,0.7\n"
                + "case-b,SINGLE_BLINK,305,380,0.9\n";

        BlinkReplayReport report = BlinkReplayReport.evaluate(
                new StringReader(expected),
                new StringReader(predicted),
                30L
        );

        assertEquals(2, report.getCaseMetrics().size());
        assertEquals(1, report.getOverall().getTruePositiveCount());
        assertEquals(2, report.getOverall().getFalsePositiveCount());
        assertEquals(1, report.getOverall().getFalseNegativeCount());
        assertTrue(report.toJson().contains("\"caseCount\":2"));
        assertTrue(report.toJson().contains("\"precision\":0.333333"));
    }

    @Test
    public void casesPresentOnlyOnOneSideStillCountAsErrors() throws Exception {
        String expected = "source,event_type,start_ms,end_ms,confidence\n"
                + "only-expected,SINGLE_BLINK,100,180,1.0\n";
        String predicted = "source,event_type,start_ms,end_ms,confidence\n"
                + "only-predicted,LONG_CLOSE,200,600,1.0\n";

        BlinkReplayReport report = BlinkReplayReport.evaluate(
                new StringReader(expected),
                new StringReader(predicted),
                20L
        );

        assertEquals(2, report.getCaseMetrics().size());
        assertEquals(0, report.getOverall().getTruePositiveCount());
        assertEquals(1, report.getOverall().getFalsePositiveCount());
        assertEquals(1, report.getOverall().getFalseNegativeCount());
    }

    @Test
    public void overallMetricsDoNotMatchSameTypeEventsAcrossCases() throws Exception {
        String expected = "source,event_type,start_ms,end_ms,confidence\n"
                + "case-a,SINGLE_BLINK,100,180,1.0\n";
        String predicted = "source,event_type,start_ms,end_ms,confidence\n"
                + "case-b,SINGLE_BLINK,105,185,1.0\n";

        BlinkReplayReport report = BlinkReplayReport.evaluate(
                new StringReader(expected),
                new StringReader(predicted),
                20L
        );

        assertEquals(0, report.getOverall().getTruePositiveCount());
        assertEquals(1, report.getOverall().getFalsePositiveCount());
        assertEquals(1, report.getOverall().getFalseNegativeCount());
    }
}
