package com.blinkvoice.visual.api.v2;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class BlinkReplayCliTest {
    @Test
    public void writesJsonReportToRequestedPath() throws Exception {
        Path directory = Files.createTempDirectory("blink-replay-cli");
        Path expected = directory.resolve("expected.csv");
        Path predicted = directory.resolve("predicted.csv");
        Path output = directory.resolve("nested/report.json");
        String header = "source,event_type,start_ms,end_ms,confidence\n";
        Files.write(expected, (header + "case-a,SINGLE_BLINK,100,180,1.0\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(predicted, (header + "case-a,SINGLE_BLINK,110,190,1.0\n")
                .getBytes(StandardCharsets.UTF_8));

        BlinkReplayCli.main(new String[]{
                expected.toString(), predicted.toString(), "20", output.toString()
        });

        String json = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"caseCount\":1"));
        assertTrue(json.contains("\"truePositiveCount\":1"));
    }
}
