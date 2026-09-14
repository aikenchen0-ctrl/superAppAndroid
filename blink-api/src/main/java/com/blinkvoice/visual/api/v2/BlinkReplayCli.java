package com.blinkvoice.visual.api.v2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Command-line entry point for offline event replay evaluation. */
public final class BlinkReplayCli {
    private BlinkReplayCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args == null || (args.length != 3 && args.length != 4)) {
            throw new IllegalArgumentException(
                    "usage: <expected.csv> <predicted.csv> <start_tolerance_ms> [report.json]"
            );
        }
        Path expected = Paths.get(args[0]);
        Path predicted = Paths.get(args[1]);
        long toleranceMs;
        try {
            toleranceMs = Long.parseLong(args[2]);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("start_tolerance_ms must be an integer", error);
        }

        BlinkReplayReport report = BlinkReplayReport.evaluate(expected, predicted, toleranceMs);
        String json = report.toJson();
        if (args.length == 4) {
            writeReport(Paths.get(args[3]), json);
        } else {
            System.out.println(json);
        }
    }

    private static void writeReport(Path output, String json) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("output must not be null");
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, json.getBytes(StandardCharsets.UTF_8));
    }
}
