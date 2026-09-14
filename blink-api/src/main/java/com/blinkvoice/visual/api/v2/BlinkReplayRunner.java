package com.blinkvoice.visual.api.v2;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs normalized replay frames through the pure v2 session engine. */
public final class BlinkReplayRunner {
    private BlinkReplayRunner() {
    }

    public static List<BlinkReplayEvent> run(Reader frameCsv, BlinkOptions options) throws IOException {
        return run(BlinkReplayFrameCsv.read(frameCsv), options);
    }

    public static List<BlinkReplayEvent> run(
            List<BlinkReplayFrame> replayFrames,
            BlinkOptions options
    ) {
        if (replayFrames == null) {
            throw new IllegalArgumentException("replayFrames must not be null");
        }
        BlinkOptions safeOptions = options != null
                ? options
                : new BlinkOptions.Builder().setStopOnEvent(false).build();
        Map<String, List<BlinkFrame>> framesByCase = new LinkedHashMap<>();
        for (BlinkReplayFrame replayFrame : replayFrames) {
            if (replayFrame == null) {
                throw new IllegalArgumentException("replayFrames must not contain null");
            }
            framesByCase.computeIfAbsent(replayFrame.getCaseId(), ignored -> new ArrayList<>())
                    .add(replayFrame.getFrame());
        }

        List<BlinkReplayEvent> events = new ArrayList<>();
        for (Map.Entry<String, List<BlinkFrame>> entry : framesByCase.entrySet()) {
            RecordingListener listener = new RecordingListener();
            DefaultBlinkSessionFactory factory = new DefaultBlinkSessionFactory(Runnable::run);
            BlinkSession session = factory.create(safeOptions, listener);
            try {
                session.start();
                for (BlinkFrame frame : entry.getValue()) {
                    session.submitFrame(frame);
                }
            } finally {
                session.close();
                factory.close();
            }
            for (BlinkEvent event : listener.events) {
                events.add(new BlinkReplayEvent(entry.getKey(), event));
            }
        }
        return Collections.unmodifiableList(events);
    }

    private static final class RecordingListener implements BlinkListener {
        final List<BlinkEvent> events = new ArrayList<>();

        @Override
        public void onEvent(BlinkEvent event) {
            events.add(event);
        }
    }
}
