package com.blinkvoice.visual.api.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class BlinkReplayCsvTest {
    @Test
    public void parsesHeaderRowsAndQuotedCaseIds() throws Exception {
        String csv = "source,event_type,start_ms,end_ms,confidence\n"
                + "\"case,001\",SINGLE_BLINK,100,180,0.91\n"
                + "case-002,LONG_CLOSE,200,600,1.0\n";

        List<BlinkReplayEvent> events = BlinkReplayCsv.read(new StringReader(csv));

        assertEquals(2, events.size());
        assertEquals("case,001", events.get(0).getCaseId());
        assertEquals(BlinkEventType.SINGLE_BLINK, events.get(0).getEvent().getType());
        assertEquals(0.91d, events.get(0).getEvent().getConfidence(), 0.0001d);
    }

    @Test
    public void groupsEventsByCaseWithoutMixingCases() throws Exception {
        String csv = "source,event_type,start_ms,end_ms,confidence\n"
                + "case-a,SINGLE_BLINK,100,180,1.0\n"
                + "case-b,DOUBLE_BLINK,300,500,1.0\n"
                + "case-a,LONG_CLOSE,700,1000,1.0\n";

        Map<String, List<BlinkEvent>> grouped = BlinkReplayCsv.groupByCase(
                BlinkReplayCsv.read(new StringReader(csv))
        );

        assertEquals(2, grouped.size());
        assertEquals(2, grouped.get("case-a").size());
        assertEquals(1, grouped.get("case-b").size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownEventType() throws Exception {
        BlinkReplayCsv.read(new StringReader(
                "source,event_type,start_ms,end_ms,confidence\ncase-x,UNKNOWN,1,2,1.0\n"
        ));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedColumnCount() throws Exception {
        BlinkReplayCsv.read(new StringReader(
                "source,event_type,start_ms,end_ms,confidence\ncase-x,SINGLE_BLINK,1\n"
        ));
    }

    @Test
    public void ignoresBlankAndCommentLines() throws Exception {
        String csv = "# replay\n\nsource,event_type,start_ms,end_ms,confidence\n"
                + "case-x,SINGLE_BLINK,1,2,1.0\n# trailing\n";

        assertTrue(BlinkReplayCsv.read(new StringReader(csv)).size() == 1);
    }
}
