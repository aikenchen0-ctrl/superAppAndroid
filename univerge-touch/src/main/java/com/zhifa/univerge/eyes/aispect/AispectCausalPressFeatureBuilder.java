package com.zhifa.univerge.eyes.aispect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AispectCausalPressFeatureBuilder {
    private static final String CONTRACT_IMU12 = "causal_imu12_v1";
    private static final String CONTRACT_IMU11_TOUCH9 = "causal_imu11_touch9_v1";
    private static final String CONTRACT_TIME_GRID_IMU11 = "causal_time_grid_imu11_v2";
    private static final String CONTRACT_TIME_GRID_IMU11_TOUCH9 = "causal_time_grid_imu11_touch9_v2";
    private static final String CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5 = "causal_time_grid_imu11_touch7_mask5_v3";
    private static final String CONTRACT_TIME_GRID_TOUCH7_MASK5_NO_GRAVITY = "causal_time_grid_touch7_mask5_no_gravity_v1";
    private static final String CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5_DELTA_GRAVITY = "causal_time_grid_imu11_touch7_mask5_delta_gravity_v1";
    private static final String CONTRACT_TOUCH_RELATIVE = "causal_touch_relative_v1";
    private static final String CONTRACT_TOUCH_RELATIVE_MASKED = "causal_touch_relative_masked_v1";
    private static final long[] TIME_GRID_OFFSETS_NANOS = new long[]{
            -15_000_000L,
            -10_000_000L,
            -5_000_000L,
            0L,
            5_000_000L,
            10_000_000L,
            15_000_000L,
            20_000_000L,
            25_000_000L
    };
    private static final String[] IMU12_FEATURE_NAMES = new String[]{
            "x_norm",
            "y_norm",
            "userAcceleration.x",
            "userAcceleration.y",
            "userAcceleration.z",
            "rotationRate.x",
            "rotationRate.y",
            "rotationRate.z",
            "gravity.x",
            "gravity.y",
            "gravity.z",
            "touch_valid"
    };
    private static final String[] IMU11_TOUCH9_FEATURE_NAMES = new String[]{
            "x_norm",
            "y_norm",
            "userAcceleration.x",
            "userAcceleration.y",
            "userAcceleration.z",
            "rotationRate.x",
            "rotationRate.y",
            "rotationRate.z",
            "gravity.x",
            "gravity.y",
            "gravity.z",
            "pressure",
            "size",
            "major_norm",
            "minor_norm",
            "log_axis_ratio",
            "orientation_sin",
            "orientation_cos",
            "pointer_count",
            "touch_valid"
    };
    private static final String[] TOUCH_RELATIVE_FEATURE_NAMES = new String[]{
            "x_norm",
            "y_norm",
            "userAcceleration.x",
            "userAcceleration.y",
            "userAcceleration.z",
            "rotationRate.x",
            "rotationRate.y",
            "rotationRate.z",
            "gravity.x",
            "gravity.y",
            "gravity.z",
            "touch_log_axis_ratio",
            "touch_log_long_relative",
            "touch_log_short_relative",
            "touch_log_size_relative",
            "touch_delta_log_long",
            "touch_delta_log_short",
            "touch_delta_log_size",
            "touch_valid"
    };
    private static final String[] TOUCH_RELATIVE_MASKED_FEATURE_NAMES = new String[]{
            "x_norm",
            "y_norm",
            "userAcceleration.x",
            "userAcceleration.y",
            "userAcceleration.z",
            "rotationRate.x",
            "rotationRate.y",
            "rotationRate.z",
            "gravity.x",
            "gravity.y",
            "gravity.z",
            "touch_log_axis_ratio",
            "touch_log_long_relative",
            "touch_log_short_relative",
            "touch_log_size_relative",
            "touch_delta_log_long",
            "touch_delta_log_short",
            "touch_delta_log_size",
            "touch_valid",
            "touch_major_available",
            "touch_minor_available",
            "touch_size_available"
    };
    private static final String[] IMU11_TOUCH7_MASK5_FEATURE_NAMES = new String[]{
            "x_norm",
            "y_norm",
            "userAcceleration.x",
            "userAcceleration.y",
            "userAcceleration.z",
            "rotationRate.x",
            "rotationRate.y",
            "rotationRate.z",
            "gravity.x",
            "gravity.y",
            "gravity.z",
            "pressure",
            "size",
            "major_norm",
            "minor_norm",
            "log_axis_ratio",
            "pointer_count",
            "touch_valid",
            "pressure_available",
            "size_available",
            "major_available",
            "minor_available",
            "pointer_count_available"
    };
    private static final String[] TOUCH7_MASK5_NO_GRAVITY_FEATURE_NAMES = new String[]{
            "x_norm",
            "y_norm",
            "userAcceleration.x",
            "userAcceleration.y",
            "userAcceleration.z",
            "rotationRate.x",
            "rotationRate.y",
            "rotationRate.z",
            "pressure",
            "size",
            "major_norm",
            "minor_norm",
            "log_axis_ratio",
            "pointer_count",
            "touch_valid",
            "pressure_available",
            "size_available",
            "major_available",
            "minor_available",
            "pointer_count_available"
    };
    private static final String[] IMU11_TOUCH7_MASK5_DELTA_GRAVITY_FEATURE_NAMES = new String[]{
            "x_norm",
            "y_norm",
            "userAcceleration.x",
            "userAcceleration.y",
            "userAcceleration.z",
            "rotationRate.x",
            "rotationRate.y",
            "rotationRate.z",
            "deltaGravity.x",
            "deltaGravity.y",
            "deltaGravity.z",
            "pressure",
            "size",
            "major_norm",
            "minor_norm",
            "log_axis_ratio",
            "pointer_count",
            "touch_valid",
            "pressure_available",
            "size_available",
            "major_available",
            "minor_available",
            "pointer_count_available"
    };

    private AispectCausalPressFeatureBuilder() {
    }

    public static boolean isCausalFeatureContract(String featureContract) {
        return AispectFieldwiseSizeFeatureBuilder.isFeatureContract(featureContract)
                || CONTRACT_IMU12.equals(featureContract)
                || CONTRACT_IMU11_TOUCH9.equals(featureContract)
                || CONTRACT_TIME_GRID_IMU11.equals(featureContract)
                || CONTRACT_TIME_GRID_IMU11_TOUCH9.equals(featureContract)
                || CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5.equals(featureContract)
                || CONTRACT_TIME_GRID_TOUCH7_MASK5_NO_GRAVITY.equals(featureContract)
                || CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5_DELTA_GRAVITY.equals(featureContract)
                || CONTRACT_TOUCH_RELATIVE.equals(featureContract)
                || CONTRACT_TOUCH_RELATIVE_MASKED.equals(featureContract);
    }

    public static boolean isTimeGridFeatureContract(String featureContract) {
        return AispectFieldwiseSizeFeatureBuilder.isFeatureContract(featureContract)
                || CONTRACT_TIME_GRID_IMU11.equals(featureContract)
                || CONTRACT_TIME_GRID_IMU11_TOUCH9.equals(featureContract)
                || CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5.equals(featureContract)
                || CONTRACT_TIME_GRID_TOUCH7_MASK5_NO_GRAVITY.equals(featureContract)
                || CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5_DELTA_GRAVITY.equals(featureContract)
                || CONTRACT_TOUCH_RELATIVE.equals(featureContract)
                || CONTRACT_TOUCH_RELATIVE_MASKED.equals(featureContract);
    }

    /** 中文注释：只检查固定物理网格是否已经具备全部真实左右支撑，不执行特征构建。 */
    public static boolean hasTimeGridSupport(
            AispectModels.ImpactFrame[] frames,
            long downEventElapsedRealtimeNanos
    ) {
        if (frames == null || downEventElapsedRealtimeNanos <= 0L || frames.length < 2) {
            return false;
        }
        ArrayList<AispectModels.ImpactFrame> ordered = new ArrayList<>();
        for (AispectModels.ImpactFrame frame : frames) {
            if (frame == null
                    || frame.sensorTimestampElapsedRealtimeNanos <= 0L
                    || frame.receivedElapsedRealtimeNanos <= 0L) {
                return false;
            }
            ordered.add(frame);
        }
        Collections.sort(ordered, new Comparator<AispectModels.ImpactFrame>() {
            @Override
            public int compare(AispectModels.ImpactFrame left, AispectModels.ImpactFrame right) {
                return Long.compare(left.sensorTimestampElapsedRealtimeNanos, right.sensorTimestampElapsedRealtimeNanos);
            }
        });
        long medianGap = medianGapNanosForRange(
                ordered,
                downEventElapsedRealtimeNanos + TIME_GRID_OFFSETS_NANOS[0],
                downEventElapsedRealtimeNanos + TIME_GRID_OFFSETS_NANOS[TIME_GRID_OFFSETS_NANOS.length - 1]
        );
        if (medianGap <= 0L) {
            return false;
        }
        long maxGap = Math.max(10_000_000L, medianGap * 4L);
        for (long offset : TIME_GRID_OFFSETS_NANOS) {
            FrameBracket bracket = frameBracket(ordered, downEventElapsedRealtimeNanos + offset);
            if (bracket == null) {
                return false;
            }
            long gap = bracket.right.sensorTimestampElapsedRealtimeNanos
                    - bracket.left.sensorTimestampElapsedRealtimeNanos;
            if (bracket.left != bracket.right && (gap <= 0L || gap > maxGap)) {
                return false;
            }
        }
        return true;
    }

    public static String[] featureNames(String featureContract) {
        if (AispectFieldwiseSizeFeatureBuilder.isFeatureContract(featureContract)) {
            return AispectFieldwiseSizeFeatureBuilder.featureNames();
        }
        if (CONTRACT_IMU12.equals(featureContract)) {
            return IMU12_FEATURE_NAMES.clone();
        }
        if (CONTRACT_TIME_GRID_IMU11.equals(featureContract)) {
            String[] output = new String[IMU12_FEATURE_NAMES.length - 1];
            System.arraycopy(IMU12_FEATURE_NAMES, 0, output, 0, output.length);
            return output;
        }
        if (CONTRACT_IMU11_TOUCH9.equals(featureContract) || CONTRACT_TIME_GRID_IMU11_TOUCH9.equals(featureContract)) {
            return IMU11_TOUCH9_FEATURE_NAMES.clone();
        }
        if (CONTRACT_TOUCH_RELATIVE.equals(featureContract)) {
            return TOUCH_RELATIVE_FEATURE_NAMES.clone();
        }
        if (CONTRACT_TOUCH_RELATIVE_MASKED.equals(featureContract)) {
            return TOUCH_RELATIVE_MASKED_FEATURE_NAMES.clone();
        }
        if (CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5.equals(featureContract)) {
            return IMU11_TOUCH7_MASK5_FEATURE_NAMES.clone();
        }
        if (CONTRACT_TIME_GRID_TOUCH7_MASK5_NO_GRAVITY.equals(featureContract)) {
            return TOUCH7_MASK5_NO_GRAVITY_FEATURE_NAMES.clone();
        }
        if (CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5_DELTA_GRAVITY.equals(featureContract)) {
            return IMU11_TOUCH7_MASK5_DELTA_GRAVITY_FEATURE_NAMES.clone();
        }
        throw new IllegalArgumentException("unknown_causal_feature_contract");
    }

    public static double[][] build(
            AispectModels.ImpactFrame[] frames,
            int firstFrameIndex,
            int[] frameIndices,
            List<AispectTouchFrame> touchFrames,
            String featureContract
    ) {
        if (!isCausalFeatureContract(featureContract) || frames == null || frameIndices == null || touchFrames == null) {
            return null;
        }
        boolean includeTouchDetails = CONTRACT_IMU11_TOUCH9.equals(featureContract);
        boolean includeTouchValidity = CONTRACT_IMU12.equals(featureContract);
        boolean includeTouchRelative = CONTRACT_TOUCH_RELATIVE.equals(featureContract)
                || CONTRACT_TOUCH_RELATIVE_MASKED.equals(featureContract);
        boolean includeTouchRelativeMasks = CONTRACT_TOUCH_RELATIVE_MASKED.equals(featureContract);
        double[][] output = new double[frameIndices.length][includeTouchRelativeMasks ? 22 : (includeTouchRelative ? 19 : (includeTouchDetails ? 20 : (includeTouchValidity ? 12 : 11)))];
        List<AispectTouchFrame> orderedTouchFrames = includeTouchRelative
                ? orderedTouchFrames(touchFrames)
                : touchFrames;
        for (int index = 0; index < frameIndices.length; index++) {
            int offset = frameIndices[index] - firstFrameIndex;
            if (offset < 0 || offset >= frames.length || frames[offset] == null) {
                return null;
            }
            AispectModels.ImpactFrame imuFrame = frames[offset];
            if (imuFrame.receivedElapsedRealtimeNanos <= 0L) {
                return null;
            }
            if (imuFrame.sensorTimestampElapsedRealtimeNanos <= 0L) {
                return null;
            }
            for (AispectTouchFrame candidate : touchFrames) {
                if (candidate == null
                        || candidate.receivedElapsedRealtimeNanos <= 0L
                        || candidate.eventElapsedRealtimeNanos <= 0L) {
                    return null;
                }
            }
            AispectTouchFrame touchFrame = latestTouchFrame(
                    orderedTouchFrames,
                    imuFrame.receivedElapsedRealtimeNanos,
                    imuFrame.sensorTimestampElapsedRealtimeNanos
            );
            if (touchFrame != null && (touchFrame.width <= 0 || touchFrame.height <= 0)) {
                return null;
            }
            if (includeTouchRelative) {
                int touchIndex = latestTouchFrameIndex(
                        orderedTouchFrames,
                        imuFrame.receivedElapsedRealtimeNanos,
                        imuFrame.sensorTimestampElapsedRealtimeNanos
                );
                fillRelativeVector(
                        output[index],
                        imuFrame,
                        orderedTouchFrames,
                        touchIndex,
                        imuFrame.receivedElapsedRealtimeNanos,
                        imuFrame.sensorTimestampElapsedRealtimeNanos,
                        includeTouchRelativeMasks
                );
            } else {
                fillVector(output[index], imuFrame, touchFrame, includeTouchDetails, includeTouchValidity, false, false);
            }
        }
        return output;
    }

    public static double[][] buildTimeGrid(
            AispectModels.ImpactFrame[] frames,
            List<AispectTouchFrame> touchFrames,
            long downEventElapsedRealtimeNanos,
            String featureContract
    ) {
        if (AispectFieldwiseSizeFeatureBuilder.isFeatureContract(featureContract)) {
            return AispectFieldwiseSizeFeatureBuilder.build(
                    frames,
                    touchFrames,
                    downEventElapsedRealtimeNanos,
                    featureContract,
                    null,
                    null
            );
        }
        if (!isTimeGridFeatureContract(featureContract)
                || frames == null
                || touchFrames == null
                || downEventElapsedRealtimeNanos <= 0L) {
            return null;
        }
        ArrayList<AispectModels.ImpactFrame> ordered = new ArrayList<>();
        for (AispectModels.ImpactFrame frame : frames) {
            if (frame == null
                    || frame.sensorTimestampElapsedRealtimeNanos <= 0L
                    || frame.receivedElapsedRealtimeNanos <= 0L) {
                return null;
            }
            ordered.add(frame);
        }
        if (ordered.size() < 2) {
            return null;
        }
        for (AispectTouchFrame candidate : touchFrames) {
            if (candidate == null
                    || candidate.receivedElapsedRealtimeNanos <= 0L
                    || candidate.eventElapsedRealtimeNanos <= 0L) {
                return null;
            }
        }
        Collections.sort(ordered, new Comparator<AispectModels.ImpactFrame>() {
            @Override
            public int compare(AispectModels.ImpactFrame left, AispectModels.ImpactFrame right) {
                return Long.compare(left.sensorTimestampElapsedRealtimeNanos, right.sensorTimestampElapsedRealtimeNanos);
            }
        });
        long medianGap = medianGapNanosForRange(
                ordered,
                downEventElapsedRealtimeNanos + TIME_GRID_OFFSETS_NANOS[0],
                downEventElapsedRealtimeNanos + TIME_GRID_OFFSETS_NANOS[TIME_GRID_OFFSETS_NANOS.length - 1]
        );
        if (medianGap <= 0L) {
            return null;
        }
        long maxGap = Math.max(10_000_000L, medianGap * 4L);
        boolean includeTouchDetails = CONTRACT_TIME_GRID_IMU11_TOUCH9.equals(featureContract);
        boolean includeTouchMasks = isTouchMaskTimeGridContract(featureContract);
        boolean includeTouchRelative = CONTRACT_TOUCH_RELATIVE.equals(featureContract)
                || CONTRACT_TOUCH_RELATIVE_MASKED.equals(featureContract);
        boolean includeTouchRelativeMasks = CONTRACT_TOUCH_RELATIVE_MASKED.equals(featureContract);
        List<AispectTouchFrame> orderedTouchFrames = includeTouchRelative
                ? orderedTouchFrames(touchFrames)
                : touchFrames;
        double[][] output = new double[TIME_GRID_OFFSETS_NANOS.length][includeTouchRelativeMasks ? 22 : (includeTouchRelative ? 19 : (includeTouchMasks ? 23 : (includeTouchDetails ? 20 : 11)))];
        for (int index = 0; index < TIME_GRID_OFFSETS_NANOS.length; index++) {
            long targetNanos = downEventElapsedRealtimeNanos + TIME_GRID_OFFSETS_NANOS[index];
            FrameBracket bracket = frameBracket(ordered, targetNanos);
            if (bracket == null) {
                return null;
            }
            long gap = bracket.right.sensorTimestampElapsedRealtimeNanos - bracket.left.sensorTimestampElapsedRealtimeNanos;
            if (bracket.left != bracket.right && (gap <= 0L || gap > maxGap)) {
                return null;
            }
            long availabilityReceipt = Math.max(
                    bracket.left.receivedElapsedRealtimeNanos,
                    bracket.right.receivedElapsedRealtimeNanos
            );
            AispectTouchFrame touchFrame = latestTimeGridTouchFrame(orderedTouchFrames, availabilityReceipt, targetNanos);
            if (touchFrame != null && (touchFrame.width <= 0 || touchFrame.height <= 0)) {
                return null;
            }
            AispectModels.ImpactFrame interpolated = interpolate(bracket, targetNanos, availabilityReceipt);
            if (includeTouchRelative) {
                int touchIndex = latestTouchFrameIndex(orderedTouchFrames, availabilityReceipt, targetNanos);
                fillRelativeVector(
                        output[index],
                        interpolated,
                        orderedTouchFrames,
                        touchIndex,
                        availabilityReceipt,
                        targetNanos,
                        includeTouchRelativeMasks
                );
            } else {
                fillVector(
                        output[index],
                        interpolated,
                        touchFrame,
                        includeTouchDetails,
                        false,
                        true,
                        includeTouchMasks
                );
            }
        }
        return transformPoseInvariantTimeGrid(output, featureContract);
    }

    private static boolean isTouchMaskTimeGridContract(String featureContract) {
        return CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5.equals(featureContract)
                || CONTRACT_TIME_GRID_TOUCH7_MASK5_NO_GRAVITY.equals(featureContract)
                || CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5_DELTA_GRAVITY.equals(featureContract);
    }

    private static double[][] transformPoseInvariantTimeGrid(double[][] source, String featureContract) {
        if (CONTRACT_TIME_GRID_TOUCH7_MASK5_NO_GRAVITY.equals(featureContract)) {
            double[][] output = new double[source.length][20];
            for (int rowIndex = 0; rowIndex < source.length; rowIndex++) {
                System.arraycopy(source[rowIndex], 0, output[rowIndex], 0, 8);
                System.arraycopy(source[rowIndex], 11, output[rowIndex], 8, 12);
            }
            return output;
        }
        if (CONTRACT_TIME_GRID_IMU11_TOUCH7_MASK5_DELTA_GRAVITY.equals(featureContract)) {
            return applyDeltaGravity(source);
        }
        return source;
    }

    static double[][] applyDeltaGravity(double[][] source) {
        if (source == null) {
            return null;
        }
        double[][] output = new double[source.length][];
        double previousX = 0.0;
        double previousY = 0.0;
        double previousZ = 0.0;
        boolean hasPrevious = false;
        for (int rowIndex = 0; rowIndex < source.length; rowIndex++) {
            if (source[rowIndex] == null || source[rowIndex].length < 11) {
                return null;
            }
            output[rowIndex] = source[rowIndex].clone();
            double currentX = source[rowIndex][8];
            double currentY = source[rowIndex][9];
            double currentZ = source[rowIndex][10];
            output[rowIndex][8] = hasPrevious ? currentX - previousX : 0.0;
            output[rowIndex][9] = hasPrevious ? currentY - previousY : 0.0;
            output[rowIndex][10] = hasPrevious ? currentZ - previousZ : 0.0;
            previousX = currentX;
            previousY = currentY;
            previousZ = currentZ;
            hasPrevious = true;
        }
        return output;
    }

    private static AispectTouchFrame latestTouchFrame(
            List<AispectTouchFrame> touchFrames,
            long imuReceivedElapsedRealtimeNanos,
            long imuSensorTimestampElapsedRealtimeNanos
    ) {
        if (touchFrames == null) {
            return null;
        }
        AispectTouchFrame selected = null;
        long selectedEvent = Long.MIN_VALUE;
        long selectedReceipt = 0L;
        for (AispectTouchFrame candidate : touchFrames) {
            if (candidate == null || candidate.receivedElapsedRealtimeNanos <= 0L) {
                continue;
            }
            if (candidate.receivedElapsedRealtimeNanos <= imuReceivedElapsedRealtimeNanos
                    && candidate.eventElapsedRealtimeNanos <= imuSensorTimestampElapsedRealtimeNanos
                    && (candidate.eventElapsedRealtimeNanos > selectedEvent
                    || (candidate.eventElapsedRealtimeNanos == selectedEvent
                    && candidate.receivedElapsedRealtimeNanos > selectedReceipt))) {
                selected = candidate;
                selectedEvent = candidate.eventElapsedRealtimeNanos;
                selectedReceipt = candidate.receivedElapsedRealtimeNanos;
            }
        }
        return selected;
    }

    private static List<AispectTouchFrame> orderedTouchFrames(List<AispectTouchFrame> touchFrames) {
        ArrayList<AispectTouchFrame> ordered = new ArrayList<>(touchFrames);
        Collections.sort(ordered, new Comparator<AispectTouchFrame>() {
            @Override
            public int compare(AispectTouchFrame left, AispectTouchFrame right) {
                int event = Long.compare(
                        left.eventElapsedRealtimeNanos,
                        right.eventElapsedRealtimeNanos
                );
                if (event != 0) {
                    return event;
                }
                return Long.compare(
                        left.receivedElapsedRealtimeNanos,
                        right.receivedElapsedRealtimeNanos
                );
            }
        });
        return ordered;
    }

    private static int latestTouchFrameIndex(
            List<AispectTouchFrame> touchFrames,
            long imuReceivedElapsedRealtimeNanos,
            long imuSensorTimestampElapsedRealtimeNanos
    ) {
        int selected = -1;
        long selectedEvent = Long.MIN_VALUE;
        long selectedReceipt = 0L;
        for (int index = 0; index < touchFrames.size(); index++) {
            AispectTouchFrame candidate = touchFrames.get(index);
            if (candidate != null
                    && candidate.receivedElapsedRealtimeNanos <= imuReceivedElapsedRealtimeNanos
                    && candidate.eventElapsedRealtimeNanos <= imuSensorTimestampElapsedRealtimeNanos
                    && (candidate.eventElapsedRealtimeNanos > selectedEvent
                    || (candidate.eventElapsedRealtimeNanos == selectedEvent
                    && candidate.receivedElapsedRealtimeNanos > selectedReceipt))) {
                selected = index;
                selectedEvent = candidate.eventElapsedRealtimeNanos;
                selectedReceipt = candidate.receivedElapsedRealtimeNanos;
            }
        }
        return selected;
    }

    private static AispectTouchFrame latestTimeGridTouchFrame(
            List<AispectTouchFrame> touchFrames,
            long imuReceivedElapsedRealtimeNanos,
            long targetElapsedRealtimeNanos
    ) {
        AispectTouchFrame selected = null;
        long selectedEvent = Long.MIN_VALUE;
        long selectedReceipt = 0L;
        for (AispectTouchFrame candidate : touchFrames) {
            if (candidate == null
                    || candidate.receivedElapsedRealtimeNanos <= 0L
                    || candidate.eventElapsedRealtimeNanos <= 0L) {
                continue;
            }
            if (candidate.receivedElapsedRealtimeNanos <= imuReceivedElapsedRealtimeNanos
                    && candidate.eventElapsedRealtimeNanos <= targetElapsedRealtimeNanos
                    && (candidate.eventElapsedRealtimeNanos > selectedEvent
                    || (candidate.eventElapsedRealtimeNanos == selectedEvent
                    && candidate.receivedElapsedRealtimeNanos > selectedReceipt))) {
                selected = candidate;
                selectedEvent = candidate.eventElapsedRealtimeNanos;
                selectedReceipt = candidate.receivedElapsedRealtimeNanos;
            }
        }
        return selected;
    }

    private static long medianGapNanos(List<AispectModels.ImpactFrame> frames) {
        ArrayList<Long> gaps = new ArrayList<>();
        for (int index = 1; index < frames.size(); index++) {
            long gap = frames.get(index).sensorTimestampElapsedRealtimeNanos
                    - frames.get(index - 1).sensorTimestampElapsedRealtimeNanos;
            if (gap <= 0L) {
                return 0L;
            }
            gaps.add(gap);
        }
        if (gaps.isEmpty()) {
            return 0L;
        }
        Collections.sort(gaps);
        return gaps.get(gaps.size() / 2);
    }

    private static long medianGapNanosForRange(
            List<AispectModels.ImpactFrame> frames,
            long startNanos,
            long endNanos
    ) {
        ArrayList<AispectModels.ImpactFrame> selected = new ArrayList<>();
        AispectModels.ImpactFrame before = null;
        AispectModels.ImpactFrame after = null;
        for (AispectModels.ImpactFrame frame : frames) {
            long timestamp = frame.sensorTimestampElapsedRealtimeNanos;
            if (timestamp < startNanos) {
                before = frame;
            } else if (timestamp > endNanos) {
                after = frame;
                break;
            } else {
                selected.add(frame);
            }
        }
        if (before != null) {
            selected.add(0, before);
        }
        if (after != null) {
            selected.add(after);
        }
        return medianGapNanos(selected);
    }

    private static FrameBracket frameBracket(List<AispectModels.ImpactFrame> frames, long targetNanos) {
        AispectModels.ImpactFrame left = null;
        for (AispectModels.ImpactFrame frame : frames) {
            long timestamp = frame.sensorTimestampElapsedRealtimeNanos;
            if (timestamp == targetNanos) {
                return new FrameBracket(frame, frame);
            }
            if (timestamp > targetNanos) {
                return left == null ? null : new FrameBracket(left, frame);
            }
            left = frame;
        }
        return null;
    }

    private static AispectModels.ImpactFrame interpolate(
            FrameBracket bracket,
            long targetNanos,
            long availabilityReceipt
    ) {
        AispectModels.ImpactFrame left = bracket.left;
        AispectModels.ImpactFrame right = bracket.right;
        long leftNanos = left.sensorTimestampElapsedRealtimeNanos;
        long rightNanos = right.sensorTimestampElapsedRealtimeNanos;
        double ratio = rightNanos == leftNanos ? 0.0 : (targetNanos - leftNanos) / (double) (rightNanos - leftNanos);
        return new AispectModels.ImpactFrame(
                0,
                interpolate(left.x, right.x, ratio),
                interpolate(left.y, right.y, ratio),
                interpolate(left.z, right.z, ratio),
                interpolate(left.rotationRateX, right.rotationRateX, ratio),
                interpolate(left.rotationRateY, right.rotationRateY, ratio),
                interpolate(left.rotationRateZ, right.rotationRateZ, ratio),
                interpolate(left.gravityX, right.gravityX, ratio),
                interpolate(left.gravityY, right.gravityY, ratio),
                interpolate(left.gravityZ, right.gravityZ, ratio),
                0,
                0,
                0,
                0,
                targetNanos / 1_000_000_000.0,
                targetNanos,
                availabilityReceipt
        );
    }

    private static double interpolate(double left, double right, double ratio) {
        return left + (right - left) * ratio;
    }

    private static final class FrameBracket {
        final AispectModels.ImpactFrame left;
        final AispectModels.ImpactFrame right;

        FrameBracket(AispectModels.ImpactFrame left, AispectModels.ImpactFrame right) {
            this.left = left;
            this.right = right;
        }
    }

    private static void fillRelativeVector(
            double[] output,
            AispectModels.ImpactFrame imuFrame,
            List<AispectTouchFrame> touchFrames,
            int selectedTouchIndex,
            long availabilityReceipt,
            long targetNanos,
            boolean includeMasks
    ) {
        AispectTouchFrame touchFrame = selectedTouchIndex >= 0
                ? touchFrames.get(selectedTouchIndex)
                : null;
        if (touchFrame != null) {
            output[0] = clamp(touchFrame.xNorm, 0.0, 1.0);
            output[1] = clamp(touchFrame.yNorm, 0.0, 1.0);
        }
        output[2] = finite(imuFrame.x);
        output[3] = finite(imuFrame.y);
        output[4] = finite(imuFrame.z);
        output[5] = finite(imuFrame.rotationRateX);
        output[6] = finite(imuFrame.rotationRateY);
        output[7] = finite(imuFrame.rotationRateZ);
        output[8] = finite(imuFrame.gravityX);
        output[9] = finite(imuFrame.gravityY);
        output[10] = finite(imuFrame.gravityZ);
        if (touchFrame == null) {
            return;
        }
        double[] currentAxes = sortedTouchAxes(touchFrame, includeMasks);
        double currentSize = usableTouchSize(touchFrame, includeMasks);
        ArrayList<Double> longHistory = new ArrayList<>();
        ArrayList<Double> shortHistory = new ArrayList<>();
        ArrayList<Double> sizeHistory = new ArrayList<>();
        double[] previousAxes = null;
        double previousSize = 0.0;
        int first = Math.max(0, selectedTouchIndex - 63);
        for (int index = first; index <= selectedTouchIndex; index++) {
            AispectTouchFrame candidate = touchFrames.get(index);
            if (candidate.receivedElapsedRealtimeNanos > availabilityReceipt
                    || candidate.eventElapsedRealtimeNanos > targetNanos) {
                continue;
            }
            double[] axes = sortedTouchAxes(candidate, includeMasks);
            double size = usableTouchSize(candidate, includeMasks);
            if (axes != null) {
                longHistory.add(axes[0]);
                shortHistory.add(axes[1]);
                if (index < selectedTouchIndex) {
                    previousAxes = axes;
                }
            }
            if (size > 0.0) {
                sizeHistory.add(size);
                if (index < selectedTouchIndex) {
                    previousSize = size;
                }
            }
        }
        output[11] = currentAxes == null ? 0.0 : safeLogRatio(currentAxes[0], currentAxes[1]);
        output[12] = currentAxes == null ? 0.0 : safeLogRatio(currentAxes[0], median(longHistory));
        output[13] = currentAxes == null ? 0.0 : safeLogRatio(currentAxes[1], median(shortHistory));
        output[14] = safeLogRatio(currentSize, median(sizeHistory));
        output[15] = currentAxes == null || previousAxes == null
                ? 0.0 : safeLogRatio(currentAxes[0], previousAxes[0]);
        output[16] = currentAxes == null || previousAxes == null
                ? 0.0 : safeLogRatio(currentAxes[1], previousAxes[1]);
        output[17] = safeLogRatio(currentSize, previousSize);
        output[18] = 1.0;
        if (includeMasks) {
            output[19] = touchFieldAvailable(touchFrame, 0) ? 1.0 : 0.0;
            output[20] = touchFieldAvailable(touchFrame, 1) ? 1.0 : 0.0;
            output[21] = touchFieldAvailable(touchFrame, 2) ? 1.0 : 0.0;
        }
    }

    private static double[] sortedTouchAxes(AispectTouchFrame frame, boolean strict) {
        if (strict && (!touchFieldAvailable(frame, 0) || !touchFieldAvailable(frame, 1))) {
            return null;
        }
        if (frame == null
                || !frame.touchMajorObserved
                || !frame.touchMinorObserved
                || (strict && frame.touchMajorSynthesizedOrUnknown)
                || (strict && frame.touchMinorSynthesizedOrUnknown)) {
            return null;
        }
        double major = positive(frame.touchMajor);
        double minor = positive(frame.touchMinor);
        if (major <= 0.0 || minor <= 0.0) {
            return null;
        }
        return new double[]{Math.max(major, minor), Math.min(major, minor)};
    }

    // 触摸 size 只有在设备声明可用、确实观测到且未由未知回退合成时才进入模型。
    private static double usableTouchSize(AispectTouchFrame frame, boolean strict) {
        if (strict && !touchFieldAvailable(frame, 2)) {
            return 0.0;
        }
        if (frame == null
                || !frame.hasSizeRange
                || !frame.sizeObserved
                || frame.sizeSynthesizedOrUnknown) {
            return 0.0;
        }
        return positive(frame.size);
    }

    private static boolean touchFieldAvailable(AispectTouchFrame frame, int field) {
        if (frame == null) {
            return false;
        }
        if (field == 0) {
            return positive(frame.touchMajor) > 0.0
                    && frame.touchMajorObserved
                    && frame.hasTouchMajorRange
                    && !frame.touchMajorSynthesizedOrUnknown;
        }
        if (field == 1) {
            return positive(frame.touchMinor) > 0.0
                    && frame.touchMinorObserved
                    && frame.hasTouchMinorRange
                    && !frame.touchMinorSynthesizedOrUnknown;
        }
        return positive(frame.size) > 0.0
                && frame.sizeObserved
                && frame.hasSizeRange
                && !frame.sizeSynthesizedOrUnknown;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        ArrayList<Double> ordered = new ArrayList<>(values);
        Collections.sort(ordered);
        int middle = ordered.size() / 2;
        if ((ordered.size() & 1) == 1) {
            return ordered.get(middle);
        }
        return (ordered.get(middle - 1) + ordered.get(middle)) * 0.5;
    }

    private static double safeLogRatio(double value, double baseline) {
        if (value <= 0.000001 || baseline <= 0.000001) {
            return 0.0;
        }
        return clamp(Math.log(value / baseline), -4.0, 4.0);
    }

    private static double positive(float value) {
        return Float.isFinite(value) && value > 0.0f ? value : 0.0;
    }

    private static void fillVector(
            double[] output,
            AispectModels.ImpactFrame imuFrame,
            AispectTouchFrame touchFrame,
            boolean includeTouchDetails,
            boolean includeTouchValidity,
            boolean respectAvailability,
            boolean includeTouchMasks
    ) {
        if (touchFrame != null) {
            output[0] = clamp(touchFrame.xNorm, 0.0, 1.0);
            output[1] = clamp(touchFrame.yNorm, 0.0, 1.0);
        }
        output[2] = finite(imuFrame.x);
        output[3] = finite(imuFrame.y);
        output[4] = finite(imuFrame.z);
        output[5] = finite(imuFrame.rotationRateX);
        output[6] = finite(imuFrame.rotationRateY);
        output[7] = finite(imuFrame.rotationRateZ);
        output[8] = finite(imuFrame.gravityX);
        output[9] = finite(imuFrame.gravityY);
        output[10] = finite(imuFrame.gravityZ);
        if (includeTouchValidity) {
            output[11] = touchFrame == null ? 0.0 : 1.0;
        }
        if ((!includeTouchDetails && !includeTouchMasks) || touchFrame == null) {
            return;
        }
        double diagonal = Math.hypot(touchFrame.width, touchFrame.height);
        double major = clamp(touchFrame.touchMajor, 0.0, diagonal);
        double minor = clamp(touchFrame.touchMinor, 0.0, diagonal);
        output[11] = !respectAvailability || touchFrame.hasPressureRange
                ? clamp(touchFrame.pressure, 0.0, 1.0)
                : 0.0;
        output[12] = !respectAvailability || touchFrame.hasSizeRange
                ? clamp(touchFrame.size, 0.0, 1.0)
                : 0.0;
        output[13] = !respectAvailability || touchFrame.hasTouchMajorRange
                ? major / diagonal
                : 0.0;
        output[14] = !respectAvailability || touchFrame.hasTouchMinorRange
                ? minor / diagonal
                : 0.0;
        output[15] = (!respectAvailability || (touchFrame.hasTouchMajorRange && touchFrame.hasTouchMinorRange))
                && major > 0.0
                && minor > 0.0
                ? clamp(Math.log(major / minor), -4.0, 4.0)
                : 0.0;
        if (touchFrame.hasOrientationRange) {
            output[16] = Math.sin(finite(touchFrame.orientation));
            output[17] = Math.cos(finite(touchFrame.orientation));
        }
        output[18] = clamp(touchFrame.pointerCount, 0.0, 10.0);
        output[19] = 1.0;
        if (includeTouchMasks) {
            output[16] = clamp(touchFrame.pointerCount, 0.0, 10.0);
            output[17] = 1.0;
            output[18] = touchFrame.hasPressureRange ? 1.0 : 0.0;
            output[19] = touchFrame.hasSizeRange ? 1.0 : 0.0;
            output[20] = touchFrame.hasTouchMajorRange ? 1.0 : 0.0;
            output[21] = touchFrame.hasTouchMinorRange ? 1.0 : 0.0;
            output[22] = 1.0;
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, finite(value)));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
