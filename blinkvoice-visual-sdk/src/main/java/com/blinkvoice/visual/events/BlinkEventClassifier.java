package com.blinkvoice.visual.events;

import com.blinkvoice.visual.api.BlinkCaptureOptions;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import com.blinkvoice.visual.debug.BlinkDebugLogger;
import java.util.Locale;

/**
 * 根据每帧人脸状态和左右眼 ELA 值，输出单眨、双眨、长闭眼三类动作事件。
 */
public final class BlinkEventClassifier {
    private static final float MICRO_REOPEN_MIN_RISE_DEGREES = 2f;
    private static final float MICRO_REOPEN_MAX_BELOW_CLOSE_DEGREES = 3f;
    private static final float MICRO_REOPEN_SECOND_CLOSE_DROP_DEGREES = 1.5f;
    private static final float RELATIVE_CLOSE_RATIO = 0.42f;
    private static final float RELATIVE_OPEN_RATIO = 0.55f;
    private static final float RELATIVE_CLOSE_MIN_DROP_DEGREES = 14f;
    private static final float RELATIVE_CLOSE_MAX_THRESHOLD = 32f;
    private static final float RELATIVE_OPEN_MAX_THRESHOLD = 46f;
    private static final float OPEN_BASELINE_RISE_ALPHA = 0.30f;
    private static final float OPEN_BASELINE_FALL_ALPHA = 0.02f;
    private static final long SHALLOW_SECOND_BLINK_MIN_DELAY_MS = 60L;
    private static final long SHALLOW_SECOND_BLINK_MAX_DELAY_MS = 220L;
    private static final float SHALLOW_SECOND_BLINK_MIN_DROP_DEGREES = 12f;
    private static final float SHALLOW_SECOND_BLINK_MAX_AVG_ELA = 17f;
    private static final float SHALLOW_SECOND_BLINK_MAX_SINGLE_EYE_ELA = 13f;

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
        final float leftEla;
        final float rightEla;

        /**
         * 记录一次闭眼片段的起止时间、持续时间和该帧左右眼 ELA 值。
         */
        BlinkSegment(long startTimeMs, long endTimeMs, float leftEla, float rightEla) {
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.durationMs = endTimeMs - startTimeMs;
            this.leftEla = leftEla;
            this.rightEla = rightEla;
        }
    }

    private final BlinkCaptureOptions options;
    private State state = State.OPEN;
    private boolean eyesClosed = false;
    private boolean hasSeenOpenEyes = false;
    private long closedStartMs = 0L;
    private long secondBlinkStartMs = 0L;
    private long lastFaceSeenMs = 0L;
    private boolean hasSeenFace = false;
    private float closedMinEla = Float.MAX_VALUE;
    private long microReopenTimeMs = 0L;
    private float microReopenEla = 0f;
    private float microReopenLeftEla = 0f;
    private float microReopenRightEla = 0f;
    private boolean secondBlinkStartedFromMicroReopen = false;
    private float waitingSecondBlinkPeakEla = 0f;
    private float openBaselineEla = 0f;
    private boolean closedStartedByRelativeDrop = false;
    private BlinkSegment pendingShortBlink = null;
    private String lastReason = "INIT";
    private String lastEvent = "-";
    private long lastTimestampMs = Long.MIN_VALUE;

    /**
     * 使用外部传入的识别参数初始化分类器；未传参数时使用默认阈值。
     */
    public BlinkEventClassifier(BlinkCaptureOptions options) {
        this.options = options != null ? options : new BlinkCaptureOptions.Builder().build();
    }

    /**
     * 接收一帧检测结果，并按当前状态机判断是否已经形成一个完整动作事件。
     */
    public BlinkCaptureResult accept(long timestampMs, boolean hasFace, float leftEla, float rightEla) {
        if (timestampMs < 0L) {
            lastReason = "INVALID_TIMESTAMP";
            log("classifier_nil reason=INVALID_TIMESTAMP timestamp=" + timestampMs);
            return null;
        }
        if (lastTimestampMs != Long.MIN_VALUE && timestampMs < lastTimestampMs) {
            lastReason = "OUT_OF_ORDER_FRAME";
            log("classifier_nil reason=OUT_OF_ORDER_FRAME previous=" + lastTimestampMs
                    + " timestamp=" + timestampMs);
            return null;
        }
        lastTimestampMs = timestampMs;
        if (!hasFace) {
            if (hasSeenFace && timestampMs - lastFaceSeenMs >= options.getNoFaceResetMs()) {
                resetState();
                lastReason = "NO_FACE_RESET";
                log("classifier_nil reason=NO_FACE_RESET elapsed=" + (timestampMs - lastFaceSeenMs));
            } else {
                lastReason = "NO_FACE";
                log("classifier_nil reason=NO_FACE hasSeenBefore=" + hasSeenFace);
            }
            return null;
        }

        lastFaceSeenMs = timestampMs;
        hasSeenFace = true;
        if (!isValidEla(leftEla) || !isValidEla(rightEla)) {
            lastReason = "INVALID_EYE_MEASURE";
            log("classifier_nil reason=INVALID_EYE_MEASURE leftEla=" + leftEla
                    + " rightEla=" + rightEla);
            return null;
        }
        float averageEla = averageEla(leftEla, rightEla);
        boolean closed = resolveClosed(averageEla);
        boolean belowCloseThreshold = isBelowCloseThreshold(averageEla);
        if (!closed) {
            updateOpenBaseline(averageEla);
        }
        if (!hasSeenOpenEyes) {
            // 首次稳定睁眼后才启动状态机，避免暗帧或首帧关键点抖动被误判成长闭眼。
            if (!closed) {
                hasSeenOpenEyes = true;
                lastReason = "OPEN_SEEN";
                log("classifier_state WAITING_OPEN_EYES->OPEN avgEla=" + formatEla(averageEla));
            } else {
                lastReason = "WAITING_OPEN_EYES";
                log("classifier_nil reason=WAITING_OPEN_EYES avgEla=" + formatEla(averageEla)
                        + " closed=true seenOpen=false");
            }
            return null;
        }

        switch (state) {
            case OPEN:
                if (belowCloseThreshold) {
                    closedStartMs = timestampMs;
                    closedStartedByRelativeDrop = isRelativeOnlyClose(averageEla);
                    resetClosedElaTracking(averageEla);
                    state = State.CLOSED;
                    lastReason = "CLOSE_STARTED";
                    log("classifier_state OPEN->CLOSED startMs=" + closedStartMs
                            + " avgEla=" + formatEla(averageEla));
                } else {
                    lastReason = "OPEN_NO_CLOSE";
                    log("classifier_nil reason=OPEN_NO_CLOSE state=OPEN avgEla=" + formatEla(averageEla));
                }
                return null;

            case CLOSED:
                return handleClosed(timestampMs, closed, belowCloseThreshold, leftEla, rightEla);

            case WAITING_SECOND_BLINK:
                return handleWaitingSecondBlink(timestampMs, belowCloseThreshold, averageEla, leftEla, rightEla);

            case LONG_CLOSE_EMITTED:
                if (!closed) {
                    closedStartMs = 0L;
                    secondBlinkStartMs = 0L;
                    clearClosedElaTracking();
                    state = State.OPEN;
                    lastReason = "REOPEN_AFTER_LONG_CLOSE";
                }
                return null;

            default:
                return null;
        }
    }

    /**
     * 处理已经进入闭眼状态后的帧，优先判断长闭眼，再判断短眨和双眨。
     */
    private BlinkCaptureResult handleClosed(
            long timestampMs,
            boolean closed,
            boolean belowCloseThreshold,
            float leftEla,
            float rightEla
    ) {
        long startMs = secondBlinkStartMs > 0L ? secondBlinkStartMs : closedStartMs;
        long durationMs = timestampMs - startMs;
        long longCloseStartMs = secondBlinkStartedFromMicroReopen ? closedStartMs : startMs;
        long longCloseDurationMs = timestampMs - longCloseStartMs;
        float averageEla = averageEla(leftEla, rightEla);
        updateMicroReopenCandidate(timestampMs, durationMs, averageEla, leftEla, rightEla);
        boolean shortBlinkReopen = !belowCloseThreshold
                && durationMs >= options.getMinShortBlinkMs()
                && durationMs <= options.getMaxShortBlinkMs();
        boolean segmentEnded = !closed || shortBlinkReopen;

        // 长闭眼优先级最高：连续闭眼达到阈值后立即返回，避免后续睁眼再被当成短眨。
        if (closed && longCloseDurationMs >= options.getLongCloseMinMs()) {
            pendingShortBlink = null;
            secondBlinkStartMs = 0L;
            clearClosedElaTracking();
            state = State.LONG_CLOSE_EMITTED;
            lastReason = "LONG_CLOSE_EMITTED";
            BlinkCaptureResult result = buildResult(
                    BlinkEventType.LONG_CLOSE,
                    new BlinkSegment(longCloseStartMs, timestampMs, leftEla, rightEla)
            );
            logEvent(result);
            return result;
        }

        if (tryStartSecondBlinkFromMicroReopen(timestampMs, averageEla)) {
            return null;
        }

        if (segmentEnded) {
            BlinkSegment segment = new BlinkSegment(startMs, timestampMs, leftEla, rightEla);
            if (pendingShortBlink != null && isShortBlink(segment)) {
                if (isInsideDoubleBlinkTotalWindow(pendingShortBlink, segment)) {
                    BlinkCaptureResult result = buildDoubleBlink(pendingShortBlink, segment);
                    pendingShortBlink = null;
                    secondBlinkStartMs = 0L;
                    clearClosedElaTracking();
                    state = State.OPEN;
                    lastReason = "DOUBLE_BLINK";
                    logEvent(result);
                    return result;
                }

                // 第二次短闭眼是真的，但超出双眨总窗口；本次识别流程只返回一个事件，所以先返回第一次单眨。
                BlinkCaptureResult result = buildResult(BlinkEventType.SINGLE_BLINK, pendingShortBlink);
                pendingShortBlink = null;
                secondBlinkStartMs = 0L;
                clearClosedElaTracking();
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
                resetWaitingSecondBlinkTracking(averageEla(segment.leftEla, segment.rightEla));
                clearClosedElaTracking();
                state = State.WAITING_SECOND_BLINK;
                lastReason = "SHORT_BLINK_PENDING";
                log("classifier_nil reason=SHORT_BLINK_PENDING start=" + segment.startTimeMs
                        + " end=" + segment.endTimeMs
                        + " duration=" + segment.durationMs);
                return null;
            }

            // 介于短眨上限和长闭眼下限之间是灰区：既不像短眨，也不够长闭眼，因此不输出事件。
            // 灰区说明前一个候选已经被更长的闭眼段打断，不能再与未来动作拼成双眨。
            pendingShortBlink = null;
            clearWaitingSecondBlinkTracking();
            secondBlinkStartMs = 0L;
            clearClosedElaTracking();
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

    /**
     * 处理第一次短眨后的等待窗口，窗口内再次闭眼则尝试升级为双眨。
     */
    private BlinkCaptureResult handleWaitingSecondBlink(
            long timestampMs,
            boolean closed,
            float averageEla,
            float leftEla,
            float rightEla
    ) {
        if (pendingShortBlink == null) {
            clearWaitingSecondBlinkTracking();
            state = State.OPEN;
            return null;
        }

        if (timestampMs - pendingShortBlink.startTimeMs > options.getDoubleBlinkWindowMs()) {
            BlinkCaptureResult result = buildResult(BlinkEventType.SINGLE_BLINK, pendingShortBlink);
            pendingShortBlink = null;
            clearWaitingSecondBlinkTracking();
            clearClosedElaTracking();
            state = closed ? State.CLOSED : State.OPEN;
            if (closed) {
                closedStartMs = timestampMs;
                resetClosedElaTracking(averageEla);
            }
            lastReason = "SINGLE_BLINK";
            logEvent(result);
            return result;
        }

        if (closed && !isTooSoonRelativeSecondClose(timestampMs, averageEla)) {
            // 第二次闭眼开始计时；只有在双眨总窗口内重新睁眼，才会升级为 DOUBLE_BLINK。
            secondBlinkStartMs = timestampMs;
            closedStartedByRelativeDrop = isRelativeOnlyClose(averageEla);
            clearWaitingSecondBlinkTracking();
            resetClosedElaTracking(averageEla);
            state = State.CLOSED;
            lastReason = "SECOND_CLOSE_STARTED";
            log("classifier_state WAITING_SECOND_BLINK->CLOSED secondStart=" + secondBlinkStartMs
                    + " pendingStart=" + pendingShortBlink.startTimeMs);
        } else if (closed) {
            eyesClosed = false;
            closedStartedByRelativeDrop = false;
            lastReason = "WAITING_SECOND_BLINK";
            log("classifier_nil reason=RELATIVE_SECOND_CLOSE_TOO_SOON elapsed="
                    + (timestampMs - pendingShortBlink.endTimeMs));
        } else if (tryStartSecondBlinkFromShallowDecline(timestampMs, averageEla, leftEla, rightEla)) {
            return null;
        } else {
            lastReason = "WAITING_SECOND_BLINK";
            log("classifier_nil reason=WAITING_SECOND_BLINK elapsed="
                    + (timestampMs - pendingShortBlink.startTimeMs)
                    + " window=" + options.getDoubleBlinkWindowMs());
        }
        return null;
    }

    /**
     * 捕捉第一眨后已明显睁开、但第二眨较浅导致平均 ELA 未跌破 close 阈值的快速双眨。
     */
    private boolean tryStartSecondBlinkFromShallowDecline(
            long timestampMs,
            float averageEla,
            float leftEla,
            float rightEla
    ) {
        if (averageEla > waitingSecondBlinkPeakEla) {
            waitingSecondBlinkPeakEla = averageEla;
        }

        long elapsedSinceFirstBlinkEndMs = timestampMs - pendingShortBlink.endTimeMs;
        if (elapsedSinceFirstBlinkEndMs < SHALLOW_SECOND_BLINK_MIN_DELAY_MS
                || elapsedSinceFirstBlinkEndMs > SHALLOW_SECOND_BLINK_MAX_DELAY_MS) {
            return false;
        }

        float dropFromPeak = waitingSecondBlinkPeakEla - averageEla;
        boolean droppedEnough = dropFromPeak >= SHALLOW_SECOND_BLINK_MIN_DROP_DEGREES
                || isRelativeCloseCandidate(averageEla);
        boolean shallowClosed = averageEla <= SHALLOW_SECOND_BLINK_MAX_AVG_ELA
                || Math.min(leftEla, rightEla) <= SHALLOW_SECOND_BLINK_MAX_SINGLE_EYE_ELA
                || isRelativeCloseCandidate(averageEla);
        if (!droppedEnough || !shallowClosed) {
            return false;
        }

        secondBlinkStartMs = timestampMs;
        float peakEla = waitingSecondBlinkPeakEla;
        eyesClosed = true;
        closedStartedByRelativeDrop = isRelativeOnlyClose(averageEla);
        clearWaitingSecondBlinkTracking();
        resetClosedElaTracking(averageEla);
        state = State.CLOSED;
        lastReason = "SHALLOW_SECOND_CLOSE_STARTED";
        log("classifier_state WAITING_SECOND_BLINK->CLOSED reason=SHALLOW_SECOND_CLOSE_STARTED secondStart="
                + secondBlinkStartMs
                + " pendingStart=" + pendingShortBlink.startTimeMs
                + " elapsedSinceFirstEnd=" + elapsedSinceFirstBlinkEndMs
                + " avgEla=" + formatEla(averageEla)
                + " peakEla=" + formatEla(peakEla)
                + " drop=" + formatEla(dropFromPeak));
        return true;
    }

    private boolean isTooSoonRelativeSecondClose(long timestampMs, float averageEla) {
        return pendingShortBlink != null
                && isRelativeOnlyClose(averageEla)
                && timestampMs - pendingShortBlink.endTimeMs < SHALLOW_SECOND_BLINK_MIN_DELAY_MS;
    }

    /**
     * 记录闭眼过程中的接近睁眼阈值回升，用于切分极快双眨中未完全睁开的中间段。
     */
    private void updateMicroReopenCandidate(
            long timestampMs,
            long durationMs,
            float averageEla,
            float leftEla,
            float rightEla
    ) {
        if (averageEla < closedMinEla) {
            closedMinEla = averageEla;
        }

        if (pendingShortBlink != null || secondBlinkStartMs > 0L) {
            return;
        }

        if (durationMs < options.getMinShortBlinkMs() || durationMs > options.getMaxShortBlinkMs()) {
            return;
        }

        boolean closeToThreshold = averageEla >= adaptiveCloseThreshold() - MICRO_REOPEN_MAX_BELOW_CLOSE_DEGREES;
        boolean roseEnough = averageEla - closedMinEla >= MICRO_REOPEN_MIN_RISE_DEGREES;
        if (closeToThreshold && roseEnough && averageEla >= microReopenEla) {
            microReopenTimeMs = timestampMs;
            microReopenEla = averageEla;
            microReopenLeftEla = leftEla;
            microReopenRightEla = rightEla;
            lastReason = "MICRO_REOPEN_CANDIDATE";
            log("classifier_nil reason=MICRO_REOPEN_CANDIDATE at=" + microReopenTimeMs
                    + " avgEla=" + formatEla(averageEla)
                    + " minEla=" + formatEla(closedMinEla));
        }
    }

    /**
     * 如果微睁后又迅速下降，把连续闭眼流切成两次短眨候选。
     */
    private boolean tryStartSecondBlinkFromMicroReopen(long timestampMs, float averageEla) {
        if (microReopenTimeMs == 0L) {
            return false;
        }

        boolean droppedEnough = microReopenEla - averageEla >= MICRO_REOPEN_SECOND_CLOSE_DROP_DEGREES;
        if (!droppedEnough) {
            return false;
        }

        BlinkSegment firstSegment = new BlinkSegment(
                closedStartMs,
                microReopenTimeMs,
                microReopenLeftEla,
                microReopenRightEla
        );
        if (!isShortBlink(firstSegment)) {
            clearClosedElaTracking();
            return false;
        }

        pendingShortBlink = firstSegment;
        secondBlinkStartMs = timestampMs;
        secondBlinkStartedFromMicroReopen = true;
        closedStartedByRelativeDrop = isRelativeOnlyClose(averageEla);
        clearWaitingSecondBlinkTracking();
        resetClosedElaTracking(averageEla);
        state = State.CLOSED;
        lastReason = "MICRO_REOPEN_SECOND_CLOSE_STARTED";
        log("classifier_state CLOSED->CLOSED reason=MICRO_REOPEN_SECOND_CLOSE_STARTED firstStart="
                + firstSegment.startTimeMs
                + " firstEnd=" + firstSegment.endTimeMs
                + " secondStart=" + secondBlinkStartMs
                + " avgEla=" + formatEla(averageEla));
        return true;
    }

    /**
     * 根据左右眼平均 ELA 和滞回阈值判断当前眼睛是否处于闭合状态。
     */
    private boolean resolveClosed(float averageEla) {
        if (eyesClosed) {
            eyesClosed = closedStartedByRelativeDrop
                    ? averageEla < adaptiveOpenThreshold()
                    : averageEla <= options.getEarOpenThreshold();
            if (!eyesClosed) {
                closedStartedByRelativeDrop = false;
            }
        } else {
            eyesClosed = isBelowCloseThreshold(averageEla);
            closedStartedByRelativeDrop = eyesClosed && isRelativeOnlyClose(averageEla);
        }
        return eyesClosed;
    }

    private boolean isBelowCloseThreshold(float averageEla) {
        return averageEla < options.getEarCloseThreshold() || isRelativeCloseCandidate(averageEla);
    }

    private boolean isRelativeCloseCandidate(float averageEla) {
        if (openBaselineEla <= 0f) {
            return false;
        }
        return openBaselineEla - averageEla >= RELATIVE_CLOSE_MIN_DROP_DEGREES
                && averageEla <= adaptiveCloseThreshold();
    }

    private boolean isRelativeOnlyClose(float averageEla) {
        return averageEla >= options.getEarCloseThreshold()
                && isRelativeCloseCandidate(averageEla);
    }

    private float adaptiveCloseThreshold() {
        if (openBaselineEla <= 0f) {
            return options.getEarCloseThreshold();
        }
        float relativeThreshold = Math.min(
                RELATIVE_CLOSE_MAX_THRESHOLD,
                openBaselineEla * RELATIVE_CLOSE_RATIO
        );
        return Math.max(options.getEarCloseThreshold(), relativeThreshold);
    }

    private float adaptiveOpenThreshold() {
        if (openBaselineEla <= 0f) {
            return options.getEarOpenThreshold();
        }
        float relativeThreshold = Math.min(
                RELATIVE_OPEN_MAX_THRESHOLD,
                openBaselineEla * RELATIVE_OPEN_RATIO
        );
        return Math.max(options.getEarOpenThreshold(), relativeThreshold);
    }

    private void updateOpenBaseline(float averageEla) {
        if (averageEla < options.getEarOpenThreshold()) {
            return;
        }
        if (openBaselineEla <= 0f) {
            openBaselineEla = averageEla;
        } else if (averageEla > openBaselineEla) {
            openBaselineEla = openBaselineEla
                    + (averageEla - openBaselineEla) * OPEN_BASELINE_RISE_ALPHA;
        } else {
            openBaselineEla = openBaselineEla
                    + (averageEla - openBaselineEla) * OPEN_BASELINE_FALL_ALPHA;
        }
    }

    /**
     * 计算左右眼 ELA 的平均值，作为状态机的统一闭眼判定输入。
     */
    private float averageEla(float leftEla, float rightEla) {
        return (leftEla + rightEla) / 2f;
    }

    private boolean isValidEla(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value) && value >= 0f && value <= 180f;
    }

    /**
     * 判断一次闭眼片段时长是否落在短眨允许范围内。
     */
    private boolean isShortBlink(BlinkSegment segment) {
        return segment.durationMs >= options.getMinShortBlinkMs()
                && segment.durationMs <= options.getMaxShortBlinkMs();
    }

    /**
     * 判断两次短眨从第一次闭眼开始到第二次睁眼结束是否仍在双眨总窗口内。
     */
    private boolean isInsideDoubleBlinkTotalWindow(BlinkSegment first, BlinkSegment second) {
        return second.endTimeMs - first.startTimeMs <= options.getDoubleBlinkWindowMs();
    }

    private void resetClosedElaTracking(float averageEla) {
        closedMinEla = averageEla;
        microReopenTimeMs = 0L;
        microReopenEla = 0f;
        microReopenLeftEla = 0f;
        microReopenRightEla = 0f;
    }

    private void clearClosedElaTracking() {
        closedMinEla = Float.MAX_VALUE;
        microReopenTimeMs = 0L;
        microReopenEla = 0f;
        microReopenLeftEla = 0f;
        microReopenRightEla = 0f;
        secondBlinkStartedFromMicroReopen = false;
    }

    private void resetWaitingSecondBlinkTracking(float peakEla) {
        waitingSecondBlinkPeakEla = peakEla;
    }

    private void clearWaitingSecondBlinkTracking() {
        waitingSecondBlinkPeakEla = 0f;
    }

    /**
     * 把单个闭眼片段封装成 SDK 对外返回的动作结果。
     */
    private BlinkCaptureResult buildResult(BlinkEventType eventType, BlinkSegment segment) {
        return new BlinkCaptureResult(
                eventType,
                segment.startTimeMs,
                segment.endTimeMs,
                segment.durationMs,
                1f
        );
    }

    /**
     * 把两次短眨合并成一个双眨结果，持续时间覆盖完整双眨窗口。
     */
    private BlinkCaptureResult buildDoubleBlink(BlinkSegment first, BlinkSegment second) {
        return new BlinkCaptureResult(
                BlinkEventType.DOUBLE_BLINK,
                first.startTimeMs,
                second.endTimeMs,
                second.endTimeMs - first.startTimeMs,
                1f
        );
    }

    /**
     * 在长时间无人脸或重新开始识别时清空状态机中间态。
     */
    private void resetState() {
        state = State.OPEN;
        eyesClosed = false;
        hasSeenOpenEyes = false;
        closedStartMs = 0L;
        secondBlinkStartMs = 0L;
        closedStartedByRelativeDrop = false;
        clearClosedElaTracking();
        clearWaitingSecondBlinkTracking();
        pendingShortBlink = null;
        openBaselineEla = 0f;
        hasSeenFace = false;
    }

    /**
     * 清空当前会话状态，供连续检测器 stop/start 时复用同一个分类器实例。
     */
    public void reset() {
        resetState();
        lastFaceSeenMs = 0L;
        lastTimestampMs = Long.MIN_VALUE;
        lastReason = "RESET";
        lastEvent = "-";
    }

    /**
     * 记录已经输出的动作事件，供调试日志和调试浮层查看。
     */
    private void logEvent(BlinkCaptureResult result) {
        lastEvent = result.getEventType().name();
        log("classifier_event type=" + result.getEventType().name()
                + " start=" + result.getStartTimeMs()
                + " end=" + result.getEndTimeMs()
                + " duration=" + result.getDurationMs());
    }

    /**
     * 生成当前状态机快照，便于宿主 App 判断识别卡在哪个阶段。
     */
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

    /**
     * 将内部状态转换成稳定的调试阶段名称。
     */
    private String phaseName() {
        if (!hasSeenOpenEyes) {
            return "WAITING_OPEN_EYES";
        }
        return state.name();
    }

    /**
     * 根据调试开关输出分类器日志。
     */
    private void log(String message) {
        BlinkDebugLogger.log(options.isDebugLoggingEnabled(), message);
    }

    /**
     * 将 ELA 数值格式化成固定两位小数，保证日志可读。
     */
    private String formatEla(float value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
