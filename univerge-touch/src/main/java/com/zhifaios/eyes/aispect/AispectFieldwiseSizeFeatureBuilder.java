package com.zhifaios.eyes.aispect;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 中文注释：构建 size、touchMajor、touchMinor 逐字段协调化的动态物理时间网格。 */
public final class AispectFieldwiseSizeFeatureBuilder {
    public static final String SCHEMA_ID = "aispect-fieldwise-size-cnn-v1";
    public static final String MODEL_FORMAT = "aispect-fieldwise-size-json-v1";
    public static final String RUNTIME_ARCHITECTURE = "fieldwise_size_cnn_v1";
    private static final int FREEZE_AFTER = 2;
    private static final double EPSILON = 1e-8;
    private static final double MAX_VALUE = 8.0;
    private static final double MAX_RATE = 1000.0;
    private static final String[] FEATURE_NAMES = new String[]{
            "imu_no_gravity_0", "imu_no_gravity_1", "imu_no_gravity_2", "imu_no_gravity_3",
            "imu_no_gravity_4", "imu_no_gravity_5", "imu_no_gravity_6", "imu_no_gravity_7",
            "delta_gravity_x", "delta_gravity_y", "delta_gravity_z",
            "size_coord", "size_rate", "major_coord", "major_rate", "minor_coord", "minor_rate",
            "size_available", "major_available", "minor_available", "touch_valid", "axis_order_violation"
    };

    private AispectFieldwiseSizeFeatureBuilder() {
    }

    public static boolean isFeatureContract(String contract) {
        return contract != null && contract.startsWith("fieldwise_size_") && contract.endsWith("_v1");
    }

    static boolean hasValidTimeGrid(JSONArray offsets, int frameCount, long captureDelayMs) {
        if (offsets == null || offsets.length() != frameCount
                || frameCount < 9 || frameCount > 25 || (frameCount - 9) % 4 != 0) {
            return false;
        }
        long expectedCaptureDelayMs = 25L + ((frameCount - 9) / 4) * 10L;
        if (captureDelayMs != expectedCaptureDelayMs) {
            return false;
        }
        long firstOffsetMs = 10L - expectedCaptureDelayMs;
        for (int index = 0; index < frameCount; index++) {
            Object value = offsets.opt(index);
            long expectedOffsetMs = firstOffsetMs + index * 5L;
            if (!(value instanceof Number)
                    || Double.compare(((Number) value).doubleValue(), (double) expectedOffsetMs) != 0) {
                return false;
            }
        }
        return true;
    }

    public static String[] featureNames() {
        return FEATURE_NAMES.clone();
    }

    public static int[] frameIndices(JSONObject scaler) throws JSONException {
        JSONArray values = scaler.getJSONArray("frameIndices");
        int[] output = new int[values.length()];
        for (int index = 0; index < output.length; index++) {
            output[index] = values.getInt(index);
        }
        return output;
    }

    public static long captureDelayMs(JSONObject scaler) {
        return scaler == null ? 0L : Math.max(0L, scaler.optLong("captureDelayMs", 0L));
    }

    public static boolean hasTimeGridSupport(
            AispectModels.ImpactFrame[] frames,
            long downEventElapsedRealtimeNanos,
            int frameCount,
            long captureDelayMs
    ) {
        if (frames == null || downEventElapsedRealtimeNanos <= 0L
                || frameCount < 9 || frameCount > 25 || (frameCount - 9) % 4 != 0
                || captureDelayMs != 25L + ((frameCount - 9) / 4) * 10L) {
            return false;
        }
        ArrayList<AispectModels.ImpactFrame> ordered = orderedFrames(frames);
        if (ordered.size() < 2 || !validFrameTimestamps(ordered)) {
            return false;
        }
        long maximumGapNanos = maxGapNanos(ordered);
        long firstOffsetMs = 10L - captureDelayMs;
        if (firstOffsetMs >= 0L) {
            return false;
        }
        for (int index = 0; index < frameCount; index++) {
            long offsetMs = firstOffsetMs + index * 5L;
            long offsetNanos = offsetMs * 1_000_000L;
            if (offsetNanos < 0L && downEventElapsedRealtimeNanos < -offsetNanos
                    || offsetNanos > 0L && downEventElapsedRealtimeNanos > Long.MAX_VALUE - offsetNanos) {
                return false;
            }
            Bracket bracket = bracket(ordered, downEventElapsedRealtimeNanos + offsetNanos);
            if (bracket == null) {
                return false;
            }
            long gap = bracket.right.sensorTimestampElapsedRealtimeNanos
                    - bracket.left.sensorTimestampElapsedRealtimeNanos;
            if (gap < 0L || gap > maximumGapNanos) {
                return false;
            }
        }
        return true;
    }

    public static double[][] build(
            AispectModels.ImpactFrame[] frames,
            List<AispectTouchFrame> touchFrames,
            long downEventElapsedRealtimeNanos,
            String featureContract,
            JSONObject scaler,
            String deviceProfileKey
    ) {
        if (!isFeatureContract(featureContract)
                || scaler == null
                || frames == null
                || touchFrames == null
                || downEventElapsedRealtimeNanos <= 0L) {
            return null;
        }
        try {
            JSONArray offsetArray = scaler.getJSONArray("gridOffsetsMs");
            int frameCount = scaler.getInt("frameCount");
            if (!hasValidTimeGrid(offsetArray, frameCount, scaler.optLong("captureDelayMs", -1L))) {
                return null;
            }
            long[] offsets = new long[offsetArray.length()];
            for (int index = 0; index < offsets.length; index++) {
                offsets[index] = offsetArray.getLong(index) * 1_000_000L;
            }
            ArrayList<AispectModels.ImpactFrame> orderedFrames = orderedFrames(frames);
            if (orderedFrames.size() < 2 || !validFrameTimestamps(orderedFrames)) {
                return null;
            }
            long maximumGapNanos = maxGapNanos(orderedFrames);
            ArrayList<AispectTouchFrame> orderedTouches = orderedTouches(touchFrames);
            FieldProfile profile = FieldProfile.fromScaler(scaler, deviceProfileKey);
            String strategy = scaler.optString("profileStrategy", "");
            FieldState sizeState = new FieldState(profile.size, strategy);
            FieldState majorState = new FieldState(profile.major, strategy);
            FieldState minorState = new FieldState(profile.minor, strategy);
            double[][] output = new double[offsets.length][FEATURE_NAMES.length];
            double[] previousGravity = null;
            for (int index = 0; index < offsets.length; index++) {
                long target = downEventElapsedRealtimeNanos + offsets[index];
                Bracket bracket = bracket(orderedFrames, target);
                if (bracket == null) {
                    return null;
                }
                long gap = bracket.right.sensorTimestampElapsedRealtimeNanos
                        - bracket.left.sensorTimestampElapsedRealtimeNanos;
                if (bracket.left != bracket.right && (gap <= 0L || gap > maximumGapNanos)) {
                    return null;
                }
                long receipt = Math.max(
                        bracket.left.receivedElapsedRealtimeNanos,
                        bracket.right.receivedElapsedRealtimeNanos
                );
                AispectModels.ImpactFrame imu = interpolate(bracket, target, receipt);
                AispectTouchFrame touch = latestTouch(orderedTouches, receipt, target);
                double[] row = output[index];
                if (touch != null) {
                    row[0] = clamp(touch.xNorm, 0.0, 1.0);
                    row[1] = clamp(touch.yNorm, 0.0, 1.0);
                }
                row[2] = finite(imu.x);
                row[3] = finite(imu.y);
                row[4] = finite(imu.z);
                row[5] = finite(imu.rotationRateX);
                row[6] = finite(imu.rotationRateY);
                row[7] = finite(imu.rotationRateZ);
                double gravityX = finite(imu.gravityX);
                double gravityY = finite(imu.gravityY);
                double gravityZ = finite(imu.gravityZ);
                if (previousGravity != null) {
                    row[8] = gravityX - previousGravity[0];
                    row[9] = gravityY - previousGravity[1];
                    row[10] = gravityZ - previousGravity[2];
                }
                if (previousGravity == null) {
                    previousGravity = new double[3];
                }
                previousGravity[0] = gravityX;
                previousGravity[1] = gravityY;
                previousGravity[2] = gravityZ;
                if (touch == null) {
                    continue;
                }
                boolean sizeValid = available(touch.size, touch.sizeObserved, touch.hasSizeRange, touch.sizeSynthesizedOrUnknown);
                boolean majorValid = available(touch.touchMajor, touch.touchMajorObserved, touch.hasTouchMajorRange, touch.touchMajorSynthesizedOrUnknown);
                boolean minorValid = available(touch.touchMinor, touch.touchMinorObserved, touch.hasTouchMinorRange, touch.touchMinorSynthesizedOrUnknown);
                double timestamp = touch.eventElapsedRealtimeNanos > 0L
                        ? touch.eventElapsedRealtimeNanos / 1_000_000_000.0
                        : Double.NaN;
                row[11] = sizeState.value(touch.size, sizeValid, timestamp);
                row[12] = sizeState.rate(touch.size, sizeValid, timestamp);
                row[13] = majorState.value(touch.touchMajor, majorValid, timestamp);
                row[14] = majorState.rate(touch.touchMajor, majorValid, timestamp);
                row[15] = minorState.value(touch.touchMinor, minorValid, timestamp);
                row[16] = minorState.rate(touch.touchMinor, minorValid, timestamp);
                row[17] = sizeValid ? 1.0 : 0.0;
                row[18] = majorValid ? 1.0 : 0.0;
                row[19] = minorValid ? 1.0 : 0.0;
                row[20] = 1.0;
                row[21] = majorValid && minorValid && touch.touchMajor < touch.touchMinor ? 1.0 : 0.0;
            }
            return output;
        } catch (JSONException | RuntimeException error) {
            return null;
        }
    }

    private static boolean available(float value, boolean observed, boolean declared, boolean unknown) {
        return Float.isFinite(value) && value > 0.0f && observed && declared && !unknown;
    }

    private static ArrayList<AispectModels.ImpactFrame> orderedFrames(AispectModels.ImpactFrame[] frames) {
        ArrayList<AispectModels.ImpactFrame> output = new ArrayList<>(Arrays.asList(frames));
        output.removeAll(Collections.singleton(null));
        output.sort(new Comparator<AispectModels.ImpactFrame>() {
            @Override
            public int compare(AispectModels.ImpactFrame left, AispectModels.ImpactFrame right) {
                return Long.compare(left.sensorTimestampElapsedRealtimeNanos, right.sensorTimestampElapsedRealtimeNanos);
            }
        });
        return output;
    }

    private static boolean validFrameTimestamps(List<AispectModels.ImpactFrame> frames) {
        long previous = 0L;
        for (AispectModels.ImpactFrame frame : frames) {
            if (frame.sensorTimestampElapsedRealtimeNanos <= 0L || frame.receivedElapsedRealtimeNanos <= 0L
                    || (previous > 0L && frame.sensorTimestampElapsedRealtimeNanos <= previous)) {
                return false;
            }
            previous = frame.sensorTimestampElapsedRealtimeNanos;
        }
        return true;
    }

    private static ArrayList<AispectTouchFrame> orderedTouches(List<AispectTouchFrame> frames) {
        ArrayList<AispectTouchFrame> output = new ArrayList<>();
        for (AispectTouchFrame frame : frames) {
            if (frame != null && frame.eventElapsedRealtimeNanos > 0L && frame.receivedElapsedRealtimeNanos > 0L) {
                output.add(frame);
            }
        }
        output.sort(new Comparator<AispectTouchFrame>() {
            @Override
            public int compare(AispectTouchFrame left, AispectTouchFrame right) {
                int event = Long.compare(left.eventElapsedRealtimeNanos, right.eventElapsedRealtimeNanos);
                return event != 0 ? event : Long.compare(left.receivedElapsedRealtimeNanos, right.receivedElapsedRealtimeNanos);
            }
        });
        return output;
    }

    private static long maxGapNanos(List<AispectModels.ImpactFrame> frames) {
        ArrayList<Long> gaps = new ArrayList<>();
        for (int index = 1; index < frames.size(); index++) {
            gaps.add(frames.get(index).sensorTimestampElapsedRealtimeNanos
                    - frames.get(index - 1).sensorTimestampElapsedRealtimeNanos);
        }
        Collections.sort(gaps);
        long median = gaps.get(gaps.size() / 2);
        return Math.max(10_000_000L, median * 4L);
    }

    private static Bracket bracket(List<AispectModels.ImpactFrame> frames, long target) {
        AispectModels.ImpactFrame left = null;
        for (AispectModels.ImpactFrame frame : frames) {
            if (frame.sensorTimestampElapsedRealtimeNanos == target) {
                return new Bracket(frame, frame);
            }
            if (frame.sensorTimestampElapsedRealtimeNanos > target) {
                return left == null ? null : new Bracket(left, frame);
            }
            left = frame;
        }
        return null;
    }

    private static AispectTouchFrame latestTouch(List<AispectTouchFrame> frames, long receipt, long target) {
        AispectTouchFrame selected = null;
        for (AispectTouchFrame frame : frames) {
            if (frame.receivedElapsedRealtimeNanos <= receipt && frame.eventElapsedRealtimeNanos <= target) {
                if (selected == null
                        || frame.eventElapsedRealtimeNanos > selected.eventElapsedRealtimeNanos
                        || (frame.eventElapsedRealtimeNanos == selected.eventElapsedRealtimeNanos
                        && frame.receivedElapsedRealtimeNanos > selected.receivedElapsedRealtimeNanos)) {
                    selected = frame;
                }
            }
        }
        return selected;
    }

    private static AispectModels.ImpactFrame interpolate(Bracket bracket, long target, long receipt) {
        AispectModels.ImpactFrame left = bracket.left;
        AispectModels.ImpactFrame right = bracket.right;
        long leftTime = left.sensorTimestampElapsedRealtimeNanos;
        long rightTime = right.sensorTimestampElapsedRealtimeNanos;
        double ratio = leftTime == rightTime ? 0.0 : (target - leftTime) / (double) (rightTime - leftTime);
        return new AispectModels.ImpactFrame(
                0.0,
                lerp(left.x, right.x, ratio), lerp(left.y, right.y, ratio), lerp(left.z, right.z, ratio),
                lerp(left.rotationRateX, right.rotationRateX, ratio),
                lerp(left.rotationRateY, right.rotationRateY, ratio),
                lerp(left.rotationRateZ, right.rotationRateZ, ratio),
                lerp(left.gravityX, right.gravityX, ratio),
                lerp(left.gravityY, right.gravityY, ratio),
                lerp(left.gravityZ, right.gravityZ, ratio),
                0.0, 0.0, 0.0, 0.0,
                target / 1_000_000_000.0,
                target, receipt
        );
    }

    private static double lerp(double left, double right, double ratio) {
        return left + (right - left) * ratio;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, finite(value)));
    }

    private static double positive(float value) {
        return Float.isFinite(value) && value > 0.0f ? value : 0.0;
    }

    private static double logValue(float value) {
        return Math.log(Math.max(positive(value), EPSILON));
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        ArrayList<Double> ordered = new ArrayList<>(values);
        Collections.sort(ordered);
        int middle = ordered.size() / 2;
        return (ordered.size() & 1) == 1
                ? ordered.get(middle)
                : (ordered.get(middle - 1) + ordered.get(middle)) * 0.5;
    }

    private static double clip(double value) {
        return Math.max(-MAX_VALUE, Math.min(MAX_VALUE, finite(value)));
    }

    private static final class Bracket {
        final AispectModels.ImpactFrame left;
        final AispectModels.ImpactFrame right;

        Bracket(AispectModels.ImpactFrame left, AispectModels.ImpactFrame right) {
            this.left = left;
            this.right = right;
        }
    }

    private static final class FieldProfile {
        final JSONObject size;
        final JSONObject major;
        final JSONObject minor;

        FieldProfile(JSONObject size, JSONObject major, JSONObject minor) {
            this.size = size;
            this.major = major;
            this.minor = minor;
        }

        static FieldProfile fromScaler(JSONObject scaler, String deviceKey) throws JSONException {
            JSONObject profiles = scaler.optJSONObject("fieldwiseProfiles");
            if (profiles == null) {
                return new FieldProfile(null, null, null);
            }
            String group = deviceKey == null ? "" : deviceKey;
            JSONObject aliases = scaler.optJSONObject("profileAliases");
            if (aliases != null && aliases.has(group)) {
                group = aliases.optString(group, group);
            }
            JSONObject device = profiles.optJSONObject(group);
            if (device == null) {
                device = profiles.optJSONObject("__global__");
            }
            if (device == null) {
                return new FieldProfile(null, null, null);
            }
            return new FieldProfile(device.optJSONObject("size"), device.optJSONObject("major"), device.optJSONObject("minor"));
        }
    }

    private static final class FieldState {
        final JSONObject profile;
        final String strategy;
        final ArrayList<Double> history = new ArrayList<>();
        double frozenBaseline = Double.NaN;
        double previousLog = Double.NaN;
        double previousTime = Double.NaN;

        FieldState(JSONObject profile, String strategy) {
            this.profile = profile;
            this.strategy = strategy == null ? "" : strategy;
        }

        double value(float raw, boolean valid, double timestamp) {
            if (!valid) {
                return 0.0;
            }
            double currentLog = logValue(raw);
            double baseline = Double.isNaN(frozenBaseline) ? medianWithCurrent(currentLog) : frozenBaseline;
            history.add(currentLog);
            if (history.size() >= FREEZE_AFTER && Double.isNaN(frozenBaseline)) {
                frozenBaseline = median(history.subList(0, FREEZE_AFTER));
            }
            if (reliable(profile, "quantile_normal".equals(strategy))) {
                if ("centered_log".equals(strategy)) {
                    return clip(currentLog - profile.optDouble("median", 0.0));
                }
                if ("robust_z".equals(strategy)) {
                    return clip((currentLog - profile.optDouble("median", 0.0)) / Math.max(profile.optDouble("iqr", 0.0), 1e-3));
                }
                if ("quantile_normal".equals(strategy)) {
                    return clip(normalInverse(rank(profile.optJSONArray("values"), currentLog)));
                }
            }
            return clip(currentLog - baseline);
        }

        double rate(float raw, boolean valid, double timestamp) {
            if (!valid) {
                return 0.0;
            }
            double currentLog = logValue(raw);
            double output = 0.0;
            if (!Double.isNaN(previousLog) && !Double.isNaN(previousTime) && Double.isFinite(timestamp)) {
                double dt = timestamp - previousTime;
                if (dt >= 1e-4) {
                    output = Math.max(-MAX_RATE, Math.min(MAX_RATE, (currentLog - previousLog) / dt));
                }
            }
            previousLog = currentLog;
            previousTime = timestamp;
            return finite(output);
        }

        private double medianWithCurrent(double current) {
            ArrayList<Double> values = new ArrayList<>(history);
            values.add(current);
            return median(values);
        }
    }

    private static boolean reliable(JSONObject profile, boolean requireValues) {
        if (profile == null || !"informative".equals(profile.optString("semanticStatus", ""))) {
            return false;
        }
        if (profile.optInt("sampleCount", 0) < 32
                || profile.optDouble("iqr", 0.0) < 0.02
                || profile.optDouble("validFraction", 0.0) < 0.8) {
            return false;
        }
        return !requireValues || profile.optJSONArray("values") != null;
    }

    private static double rank(JSONArray values, double target) {
        if (values == null || values.length() == 0) {
            return 0.5;
        }
        int low = 0;
        int high = values.length();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values.optDouble(middle, 0.0) <= target) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return Math.max(1e-3, Math.min(1.0 - 1e-3, low / (double) values.length()));
    }

    // 中文注释：Acklam 近似逆正态分布，避免端侧引入额外数学库。
    private static double normalInverse(double probability) {
        double p = Math.max(1e-3, Math.min(1.0 - 1e-3, probability));
        double[] a = {-39.6968302866538, 220.946098424521, -275.928510446969, 138.357751867269, -30.6647980661472, 2.50662827745924};
        double[] b = {-54.4760987982241, 161.585836858041, -155.698979859887, 66.8013118877197, -13.2806815528857};
        double[] c = {-0.00778489400243029, -0.322396458041136, -2.40075827716184, -2.54973253934373, 4.37466414146497, 2.93816398269878};
        double[] d = {0.00778469570904146, 0.32246712907004, 2.445134137143, 3.75440866190742};
        if (p < 0.02425) {
            double q = Math.sqrt(-2.0 * Math.log(p));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        }
        if (p > 1.0 - 0.02425) {
            double q = Math.sqrt(-2.0 * Math.log(1.0 - p));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        }
        double q = p - 0.5;
        double r = q * q;
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
                / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0);
    }
}
