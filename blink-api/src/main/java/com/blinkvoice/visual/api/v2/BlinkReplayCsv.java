package com.blinkvoice.visual.api.v2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parser for the stable offline event CSV contract. */
public final class BlinkReplayCsv {
    private static final String[] HEADER = {
            "source", "event_type", "start_ms", "end_ms", "confidence"
    };

    private BlinkReplayCsv() {
    }

    public static List<BlinkReplayEvent> read(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    public static List<BlinkReplayEvent> read(Reader source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        BufferedReader reader = source instanceof BufferedReader
                ? (BufferedReader) source
                : new BufferedReader(source);
        List<BlinkReplayEvent> result = new ArrayList<>();
        boolean headerSeen = false;
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            List<String> columns = parseLine(line, lineNumber);
            if (!headerSeen) {
                validateHeader(columns, lineNumber);
                headerSeen = true;
                continue;
            }
            if (columns.size() != HEADER.length) {
                throw malformed(lineNumber, "expected 5 columns, got " + columns.size());
            }
            result.add(parseEvent(columns, lineNumber));
        }
        if (!headerSeen) {
            throw new IllegalArgumentException("CSV header is missing");
        }
        return Collections.unmodifiableList(result);
    }

    public static Map<String, List<BlinkEvent>> groupByCase(List<BlinkReplayEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("events must not be null");
        }
        Map<String, List<BlinkEvent>> grouped = new LinkedHashMap<>();
        for (BlinkReplayEvent replayEvent : events) {
            if (replayEvent == null) {
                throw new IllegalArgumentException("events must not contain null");
            }
            grouped.computeIfAbsent(replayEvent.getCaseId(), ignored -> new ArrayList<>())
                    .add(replayEvent.getEvent());
        }
        Map<String, List<BlinkEvent>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, List<BlinkEvent>> entry : grouped.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private static void validateHeader(List<String> columns, int lineNumber) {
        if (columns.size() != HEADER.length) {
            throw malformed(lineNumber, "header must contain source,event_type,start_ms,end_ms,confidence");
        }
        for (int index = 0; index < HEADER.length; index++) {
            if (!HEADER[index].equals(columns.get(index).trim())) {
                throw malformed(lineNumber, "unexpected header column " + columns.get(index));
            }
        }
    }

    private static BlinkReplayEvent parseEvent(List<String> columns, int lineNumber) {
        String source = columns.get(0).trim();
        if (source.isEmpty()) {
            throw malformed(lineNumber, "source must not be empty");
        }
        BlinkEventType type;
        try {
            type = BlinkEventType.valueOf(columns.get(1).trim());
        } catch (IllegalArgumentException error) {
            throw malformed(lineNumber, "unknown event_type " + columns.get(1));
        }
        long start = parseLong(columns.get(2), "start_ms", lineNumber);
        long end = parseLong(columns.get(3), "end_ms", lineNumber);
        double confidence = parseDouble(columns.get(4), "confidence", lineNumber);
        try {
            return new BlinkReplayEvent(source, new BlinkEvent(type, start, end, confidence));
        } catch (IllegalArgumentException error) {
            throw malformed(lineNumber, error.getMessage());
        }
    }

    private static long parseLong(String value, String name, int lineNumber) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
            throw malformed(lineNumber, name + " must be an integer");
        }
    }

    private static double parseDouble(String value, String name, int lineNumber) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException error) {
            throw malformed(lineNumber, name + " must be a number");
        }
    }

    private static List<String> parseLine(String line, int lineNumber) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (quoted) {
            throw malformed(lineNumber, "unterminated quoted field");
        }
        columns.add(current.toString());
        return columns;
    }

    private static IllegalArgumentException malformed(int lineNumber, String message) {
        return new IllegalArgumentException("CSV line " + lineNumber + ": " + message);
    }
}
