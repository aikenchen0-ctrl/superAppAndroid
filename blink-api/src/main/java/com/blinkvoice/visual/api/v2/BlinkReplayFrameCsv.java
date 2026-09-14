package com.blinkvoice.visual.api.v2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parser for normalized frame replay CSV files. */
public final class BlinkReplayFrameCsv {
    private static final String[] HEADER = {
            "source", "timestamp_ms", "face_present", "left_eye_openness",
            "right_eye_openness", "face_confidence"
    };

    private BlinkReplayFrameCsv() {
    }

    public static List<BlinkReplayFrame> read(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    public static List<BlinkReplayFrame> read(Reader source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        BufferedReader reader = source instanceof BufferedReader
                ? (BufferedReader) source
                : new BufferedReader(source);
        List<BlinkReplayFrame> result = new ArrayList<>();
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
                throw malformed(lineNumber, "expected 6 columns, got " + columns.size());
            }
            result.add(parseFrame(columns, lineNumber));
        }
        if (!headerSeen) {
            throw new IllegalArgumentException("CSV header is missing");
        }
        return Collections.unmodifiableList(result);
    }

    private static BlinkReplayFrame parseFrame(List<String> columns, int lineNumber) {
        String source = columns.get(0).trim();
        if (source.isEmpty()) {
            throw malformed(lineNumber, "source must not be empty");
        }
        long timestamp = parseLong(columns.get(1), "timestamp_ms", lineNumber);
        boolean facePresent = parseFacePresent(columns.get(2), lineNumber);
        BlinkFrame frame;
        if (!facePresent) {
            frame = BlinkFrame.noFace(timestamp);
        } else {
            if (columns.get(3).trim().isEmpty() || columns.get(4).trim().isEmpty()) {
                throw malformed(lineNumber, "eye openness values are required when face_present=1");
            }
            double left = parseDouble(columns.get(3), "left_eye_openness", lineNumber);
            double right = parseDouble(columns.get(4), "right_eye_openness", lineNumber);
            double confidence = parseDouble(columns.get(5), "face_confidence", lineNumber);
            try {
                frame = new BlinkFrame(timestamp, true, left, right, confidence);
            } catch (IllegalArgumentException error) {
                throw malformed(lineNumber, error.getMessage());
            }
        }
        return new BlinkReplayFrame(source, frame);
    }

    private static boolean parseFacePresent(String value, int lineNumber) {
        String normalized = value.trim();
        if ("1".equals(normalized) || "true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw malformed(lineNumber, "face_present must be 0/1 or true/false");
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

    private static void validateHeader(List<String> columns, int lineNumber) {
        if (columns.size() != HEADER.length) {
            throw malformed(lineNumber, "frame header must contain 6 columns");
        }
        for (int index = 0; index < HEADER.length; index++) {
            if (!HEADER[index].equals(columns.get(index).trim())) {
                throw malformed(lineNumber, "unexpected header column " + columns.get(index));
            }
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
