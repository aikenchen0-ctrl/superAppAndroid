package com.blinkvoice.visual.events;

import com.blinkvoice.visual.api.BlinkCaptureOptions;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import com.blinkvoice.visual.debug.BlinkDebugLogger;
import java.util.Locale;

public final class BlinkEventClassifier {
    private enum State {
        OPEN,
        CLOSED,
        WAITING_SECOND_BLINK,
        LONG_CLOSE_EMITTED
    }

    private static final class BlinkSegment {
        final long startTimeMs;
        final long endTimeMs;
        final long durationMs;
        final float leftEar;
        final float rightEar;

        BlinkSegment(long startTimeMs, long endTimeMs, float leftEar, float rightEar) {
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.durationMs = endTimeMs - startTimeMs;
            this.leftEar = leftEar;
            this.rightEar = rightEar;
        }
    }

    private final BlinkCaptureOptions options;
    private State state = State.OPEN;
    private boolean eyesClosed = false;
    private boolean hasSeenOpenEyes = false;
    private long closedStartMs = 0L;
    private long secondBlinkStartMs = 0L;
    private long lastFaceSeenMs = 0L;
    private BlinkSegment pendingShortBlink = null;
    private String lastReason = "INIT";
    private String lastEvent = "-";
    private long lastTimestampMs = 0L;

    public BlinkEventClassifier(BlinkCaptureOptions options) {
        this.options = options != null ? options : new BlinkCaptureOptions.Builder().build();
    }

    public BlinkCaptureResult accept(long timestampMs, boolean hasFace, float leftEar, float rightEar) {
        lastTimestampMs = timestampMs;
        if (!hasFace) {
            if (lastFaceSeenMs > 0L && timestampMs - lastFaceSeenMs >= options.getNoFaceResetMs()) {
                resetState();
                lastReason = "NO_FACE_RESET";
                log("classifier_nil reason=NO_FACE_RESET elapsed=" + (timestampMs - lastFaceSeenMs));
            } else {
                lastReason = "NO_FACE";
                log("classifier_nil reason=NO_FACE hasSeenBefore=" + (lastFaceSeenMs > 0L));
            }
            return null;
        }

        lastFaceSeenMs = timestampMs;
        boolean closed = resolveClosed(leftEar, rightEar);
        float averageEar = averageEar(leftEar, rightEar);
        if (!hasSeenOpenEyes) {
            // 首次稳定睁眼后才启动状态机，避免暗帧或首帧关键点抖动被误判成长闭眼。
            if (!closed) {
                hasSeenOpenEyes = true;
                lastReason = "OPEN_SEEN";
                log("classifier_state WAITING_OPEN_EYES->OPEN avgEar=" + formatEar(averageEar));
            } else {
                lastReason = "WAITING_OPEN_EYES";
                log("classifier_nil reason=WAITING_OPEN_EYES avgEar=" + formatEar(averageEar)
                        + " closed=true seenOpen=false");
            }
            return null;
        }

        switch (state) {
            case OPEN:
                if (closed) {
                    closedStartMs = timestampMs;
                    state = State.CLOSED;
                    lastReason = "CLOSE_STARTED";
                    log("classifier_state OPEN->CLOSED startMs=" + closedStartMs
                            + " avgEar=" + formatEar(averageEar));
                } else {
                    lastReason = "OPEN_NO_CLOSE";
                    log("classifier_nil reason=OPEN_NO_CLOSE state=OPEN avgEar=" + formatEar(averageEar));
                }
                return null;

            case CLOSED:
                return handleClosed(timestampMs, closed, leftEar, rightEar);

            case WAITING_SECOND_BLINK:
                return handleWaitingSecondBlink(timestampMs, closed);

            case LONG_CLOSE_EMITTED:
                if (!closed) {
                    closedStartMs = 0L;
                    secondBlinkStartMs = 0L;
                    state = State.OPEN;
                    lastReason = "REOPEN_AFTER_LONG_CLOSE";
                }
                return null;

            default:
                return null;
        }
    }

    private BlinkCaptureResult handleClosed(long timestampMs, boolean closed, float leftEar, float rightEar) {
        long startMs = secondBlinkStartMs > 0L ? secondBlinkStartMs : closedStartMs;
        long durationMs = timestampMs - startMs;

        // 长闭眼优先级最高：连续闭眼达到阈值后立即返回，避免后续睁眼再被当成短眨。
        if (closed && durationMs >= options.getLongCloseMinMs()) {
            pendingShortBlink = null;
            secondBlinkStartMs = 0L;
            state = State.LONG_CLOSE_EMITTED;
            lastReason = "LONG_CLOSE_EMITTED";
            BlinkCaptureResult result = buildResult(
                    BlinkEventType.LONG_CLOSE,
                    new BlinkSegment(startMs, timestampMs, leftEar, rightEar)
            );
            logEvent(result);
            return result;
        }

        if (!closed) {
            BlinkSegment segment = new BlinkSegment(startMs, timestampMs, leftEar, rightEar);
            if (pendingShortBlink != null && isShortBlink(segment)) {
                if (isInsideDoubleBlinkTotalWindow(pendingShortBlink, segment)) {
                    BlinkCaptureResult result = buildDoubleBlink(pendingShortBlink, segment);
                    pendingShortBlink = null;
                    secondBlinkStartMs = 0L;
                    state = State.OPEN;
                    lastReason = "DOUBLE_BLINK";
                    logEvent(result);
                    return result;
                }

                // 第二次短闭眼是真的，但超出双眨总窗口；本次 capture 只返回一个事件，所以先返回第一次单眨。
                BlinkCaptureResult result = buildResult(BlinkEventType.SINGLE_BLINK, pendingShortBlink);
                pendingShortBlink = null;
                secondBlinkStartMs = 0L;
                state = State.OPEN;
                lastReason = "SECOND_BLINK_OUTSIDE_WINDOW";
                log("classifier_state CLOSED->OPEN reason=SECOND_BLINK_OUTSIDE_WINDOW duration="
                        + segment.durationMs);
                logEvent(result);
                return result;
            }

            if (isShortBlink(segment)) {
                // 单次短闭眼先作为候选保留，等待双眨窗口结束或第二次短眨升级为 DOUBLE_BLINK。
                pendingShortBlink = segment;
                state = State.WAITING_SECOND_BLINK;
                lastReason = "SHORT_BLINK_PENDING";
                log("classifier_nil reason=SHORT_BLINK_PENDING start=" + segment.startTimeMs
                        + " end=" + segment.endTimeMs
                        + " duration=" + segment.durationMs);
                return null;
            }

            // 介于短眨上限和长闭眼下限之间是灰区：既不像短眨，也不够长闭眼，因此不输出事件。
            secondBlinkStartMs = 0L;
            state = State.OPEN;
            lastReason = "GRAY_ZONE_DURATION";
            log("classifier_nil reason=GRAY_ZONE_DURATION duration=" + segment.durationMs
                    + " shortMax=" + options.getMaxShortBlinkMs()
                    + " longMin=" + options.getLongCloseMinMs());
        }

        if (closed) {
            lastReason = "CLOSED_ACCUMULATING";
            log("classifier_nil reason=CLOSED_ACCUMULATING start=" + startMs
                    + " duration=" + durationMs
                    + " longMin=" + options.getLongCloseMinMs());
        }
        return null;
    }

    private BlinkCaptureResult handleWaitingSecondBlink(long timestampMs, boolean closed) {
        if (pendingShortBlink == null) {
            state = State.OPEN;
            return null;
        }

        if (timestampMs - pendingShortBlink.startTimeMs > options.getDoubleBlinkWindowMs()) {
            BlinkCaptureResult result = buildResult(BlinkEventType.SINGLE_BLINK, pendingShortBlink);
            pendingShortBlink = null;
            state = closed ? State.CLOSED : State.OPEN;
            if (closed) {
                closedStartMs = timestampMs;
            }
            lastReason = "SINGLE_BLINK";
            logEvent(result);
            return result;
        }

        if (closed) {
            // 第二次闭眼开始计时；只有在双眨总窗口内重新睁眼，才会升级为 DOUBLE_BLINK。
            secondBlinkStartMs = timestampMs;
            state = State.CLOSED;
            lastReason = "SECOND_CLOSE_STARTED";
            log("classifier_state WAITING_SECOND_BLINK->CLOSED secondStart=" + secondBlinkStartMs
                    + " pendingStart=" + pendingShortBlink.startTimeMs);
        } else {
            lastReason = "WAITING_SECOND_BLINK";
            log("classifier_nil reason=WAITING_SECOND_BLINK elapsed="
                    + (timestampMs - pendingShortBlink.startTimeMs)
                    + " window=" + options.getDoubleBlinkWindowMs());
        }
        return null;
    }

    private boolean resolveClosed(float leftEar, float rightEar) {
        float averageEar = averageEar(leftEar, rightEar);
        if (eyesClosed) {
            eyesClosed = averageEar <= options.getEarOpenThreshold();
        } else {
            eyesClosed = averageEar < options.getEarCloseThreshold();
        }
        return eyesClosed;
    }

    private float averageEar(float leftEar, float rightEar) {
        return (leftEar + rightEar) / 2f;
    }

    private boolean isShortBlink(BlinkSegment segment) {
        return segment.durationMs >= options.getMinShortBlinkMs()
                && segment.durationMs <= options.getMaxShortBlinkMs();
    }

    private boolean isInsideDoubleBlinkTotalWindow(BlinkSegment first, BlinkSegment second) {
        return second.endTimeMs - first.startTimeMs <= options.getDoubleBlinkWindowMs();
    }

    private BlinkCaptureResult buildResult(BlinkEventType eventType, BlinkSegment segment) {
        return new BlinkCaptureResult(
                eventType,
                segment.startTimeMs,
                segment.endTimeMs,
                segment.durationMs,
                1f
        );
    }

    private BlinkCaptureResult buildDoubleBlink(BlinkSegment first, BlinkSegment second) {
        return new BlinkCaptureResult(
                BlinkEventType.DOUBLE_BLINK,
                first.startTimeMs,
                second.endTimeMs,
                second.endTimeMs - first.startTimeMs,
                1f
        );
    }

    private void resetState() {
        state = State.OPEN;
        eyesClosed = false;
        hasSeenOpenEyes = false;
        closedStartMs = 0L;
        secondBlinkStartMs = 0L;
        pendingShortBlink = null;
    }

    private void logEvent(BlinkCaptureResult result) {
        lastEvent = result.getEventType().name();
        log("classifier_event type=" + result.getEventType().name()
                + " start=" + result.getStartTimeMs()
                + " end=" + result.getEndTimeMs()
                + " duration=" + result.getDurationMs());
    }

    public BlinkClassifierDebugSnapshot getDebugSnapshot() {
        long closedDurationMs = 0L;
        long pendingElapsedMs = 0L;
        long activeClosedStartMs = secondBlinkStartMs > 0L ? secondBlinkStartMs : closedStartMs;
        if (eyesClosed && activeClosedStartMs > 0L && lastTimestampMs >= activeClosedStartMs) {
            closedDurationMs = lastTimestampMs - activeClosedStartMs;
        }
        if (pendingShortBlink != null && lastTimestampMs >= pendingShortBlink.startTimeMs) {
            pendingElapsedMs = lastTimestampMs - pendingShortBlink.startTimeMs;
        }
        return new BlinkClassifierDebugSnapshot(
                phaseName(),
                lastReason,
                lastEvent,
                hasSeenOpenEyes,
                eyesClosed,
                closedDurationMs,
                pendingElapsedMs
        );
    }

    private String phaseName() {
        if (!hasSeenOpenEyes) {
            return "WAITING_OPEN_EYES";
        }
        return state.name();
    }

    private void log(String message) {
        BlinkDebugLogger.log(options.isDebugLoggingEnabled(), message);
    }

    private String formatEar(float value) {
        return String.format(Locale.US, "%.3f", value);
    }
}
