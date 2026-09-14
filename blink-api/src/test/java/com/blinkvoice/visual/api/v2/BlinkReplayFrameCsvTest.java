package com.blinkvoice.visual.api.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.StringReader;
import java.util.List;
import org.junit.Test;

public class BlinkReplayFrameCsvTest {
    @Test
    public void parsesNormalizedFrameRowsAndNoFaceRows() throws Exception {
        String csv = "source,timestamp_ms,face_present,left_eye_openness,right_eye_openness,face_confidence\n"
                + "case-a,0,1,0.9,0.8,0.95\n"
                + "case-a,100,0,,,0\n";

        List<BlinkReplayFrame> frames = BlinkReplayFrameCsv.read(new StringReader(csv));

        assertEquals(2, frames.size());
        assertEquals("case-a", frames.get(0).getCaseId());
        assertEquals(0.9d, frames.get(0).getFrame().getLeftEyeOpenness(), 0.0001d);
        assertFalse(frames.get(1).getFrame().isFacePresent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFacePresentRowWithMissingEyeValues() throws Exception {
        BlinkReplayFrameCsv.read(new StringReader(
                "source,timestamp_ms,face_present,left_eye_openness,right_eye_openness,face_confidence\n"
                        + "case-a,0,1,,,0.9\n"
        ));
    }
}
