package com.zhifa.univerge.eyes.aispect;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class AispectModelBundleValidator {
    static final class Result {
        final boolean valid;
        final String reason;

        private Result(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason == null ? "" : reason;
        }
    }

    private static final Set<String> SUPPORTED_FEATURES = new HashSet<>(Arrays.asList(
            "delta",
            "x",
            "y",
            "z",
            "userAcceleration.x",
            "userAcceleration.y",
            "userAcceleration.z",
            "rotationRate.x",
            "rotationRate.y",
            "rotationRate.z",
            "gravity.x",
            "gravity.y",
            "gravity.z",
            "x_norm",
            "y_norm",
            "time_since_touch_down",
            "time_since_anchor",
            "contactPatch.cov_11",
            "contactPatch.cov_12",
            "contactPatch.cov_22",
            "contactPatch.sigmaX",
            "contactPatch.sigmaY",
            "contactPatch.rawMajorPx",
            "contactPatch.rawMinorPx",
            "touchNormalization.majorValue",
            "touchNormalization.minorValue",
            "touchNormalization.areaValue",
            "touchNormalization.confidence",
            "touchNormalization.rawMajorPx",
            "touchNormalization.rawMinorPx",
            "touchNormalization.screenAreaValue",
            "touchNormalization.covarianceAreaValue",
            "touchNormalization.baselineAreaValue",
            "gaussian.center",
            "gaussian.sum",
            "gaussian.energy",
            "gaussian.spread",
            "gaussian.sigma_x_px",
            "gaussian.sigma_y_px",
            "gaussian.valid_mask",
            "multi.center_x_norm",
            "multi.center_y_norm",
            "multi.major_px",
            "multi.minor_px",
            "multi.orientation_rad",
            "multi.valid_mask",
            "covariance.x_norm",
            "covariance.y_norm",
            "covariance.cov_11",
            "covariance.cov_12",
            "covariance.cov_22",
            "covariance.valid_mask"
    ));

    private AispectModelBundleValidator() {
    }

    static Result validate(
            AispectRemoteModelAssignment assignment,
            JSONObject weights,
            JSONObject scaler
    ) {
        if (assignment == null || !assignment.isUsable() || weights == null || scaler == null) {
            return invalid("assignment_rejected");
        }
        if (AispectRemoteModelAssignment.FEATURE_SCHEMA_TIME_GRID_GROUPNORM_V1.equals(
                assignment.featureSchemaId
        )) {
            return validateTimeGrid(assignment, weights, scaler);
        }
        try {
            JSONArray center = scaler.getJSONArray("center");
            JSONArray scale = scaler.getJSONArray("scale");
            JSONArray featureNames = scaler.getJSONArray("featureNames");
            JSONArray frameIndices = scaler.getJSONArray("frameIndices");
            int inputChannels = center.length();
            if (inputChannels <= 0
                    || scale.length() != inputChannels
                    || featureNames.length() != inputChannels) {
                return invalid("scaler_length_mismatch");
            }
            for (int i = 0; i < inputChannels; i++) {
                if (!isFiniteNumber(center.opt(i))) {
                    return invalid("center_invalid");
                }
                Object scaleValue = scale.opt(i);
                if (!isFiniteNumber(scaleValue) || Math.abs(((Number) scaleValue).doubleValue()) < 1e-12) {
                    return invalid("scale_invalid");
                }
                if (!isSupportedFeature(featureNames.optString(i, ""))) {
                    return invalid("feature_unsupported");
                }
            }
            int frameCount = scaler.optInt("frameCount", frameIndices.length());
            if (frameCount < 4 || frameIndices.length() != frameCount) {
                return invalid("frame_schema_mismatch");
            }
            if (!hasValidFrameIndices(frameIndices)) {
                return invalid("frame_indices_invalid");
            }
            if (!matchesInputContract(assignment, featureNames, frameIndices)) {
                return invalid("input_contract_mismatch");
            }
            if (!AispectCanonicalModelContract.matches(
                    scaler.optString("windowMode", ""),
                    scaler.optInt("captureDelayMs", -1),
                    intArray(frameIndices),
                    stringArray(featureNames)
            )) {
                return invalid("release_contract_mismatch");
            }
            int classCount = scaler.optInt("classCount", 0);
            String[] labelOrder = stringArray(scaler.optJSONArray("labelOrder"));
            if (!AispectCanonicalModelContract.matchesLabels(labelOrder)
                    || classCount != labelOrder.length
                    || assignment.classCount != classCount
                    || !Arrays.equals(assignment.labelOrder, labelOrder)) {
                return invalid("output_schema_mismatch");
            }

            Tensor conv0 = tensor(weights, "features.0.weight");
            Tensor conv0Bias = tensor(weights, "features.0.bias");
            if (!isConv(conv0, inputChannels) || !isVector(conv0Bias, conv0.shape[0])) {
                return invalid("tensor_shape_mismatch");
            }
            if (!hasBatchNorm(weights, "features.1", conv0.shape[0])) {
                return invalid("tensor_shape_mismatch");
            }

            Tensor conv1 = tensor(weights, "features.4.weight");
            Tensor conv1Bias = tensor(weights, "features.4.bias");
            if (!isConv(conv1, conv0.shape[0]) || !isVector(conv1Bias, conv1.shape[0])) {
                return invalid("tensor_shape_mismatch");
            }
            if (!hasBatchNorm(weights, "features.5", conv1.shape[0])) {
                return invalid("tensor_shape_mismatch");
            }

            Tensor conv2 = tensor(weights, "features.9.weight");
            Tensor conv2Bias = tensor(weights, "features.9.bias");
            if (!isConv(conv2, conv1.shape[0]) || !isVector(conv2Bias, conv2.shape[0])) {
                return invalid("tensor_shape_mismatch");
            }
            if (!hasBatchNorm(weights, "features.10", conv2.shape[0])) {
                return invalid("tensor_shape_mismatch");
            }

            int pooledLength = (frameCount / 2) / 2;
            int flatSize = conv2.shape[0] * pooledLength;
            Tensor classifier0 = tensor(weights, "classifier.0.weight");
            Tensor classifier0Bias = tensor(weights, "classifier.0.bias");
            if (!isMatrix(classifier0, 64, flatSize) || !isVector(classifier0Bias, 64)) {
                return invalid("tensor_shape_mismatch");
            }
            Tensor classifier3 = tensor(weights, "classifier.3.weight");
            Tensor classifier3Bias = tensor(weights, "classifier.3.bias");
            if (!isMatrix(classifier3, classCount, 64) || !isVector(classifier3Bias, classCount)) {
                return invalid("tensor_shape_mismatch");
            }
            return new Result(true, "valid");
        } catch (JSONException | RuntimeException error) {
            return invalid("model_json_invalid");
        }
    }

    private static Result validateTimeGrid(
            AispectRemoteModelAssignment assignment,
            JSONObject weights,
            JSONObject scaler
    ) {
        try {
            if (!AispectRemoteModelAssignment.MODEL_TYPE_TIME_GRID_GROUPNORM_V1.equals(assignment.modelType)
                    || !AispectTimeGridGroupNormRuntime.isSupportedArchitecture(
                    scaler.optString("runtimeArchitecture", "")
            )) {
                return invalid("time_grid_architecture_mismatch");
            }
            String featureContract = scaler.optString("featureContract", "");
            if (!AispectCausalPressFeatureBuilder.isTimeGridFeatureContract(featureContract)) {
                return invalid("time_grid_contract_mismatch");
            }
            JSONArray featureNames = scaler.optJSONArray("featureNames");
            JSONArray matrixShape = scaler.optJSONArray("matrixShape");
            String[] expectedNames = AispectCausalPressFeatureBuilder.featureNames(featureContract);
            if (featureNames == null
                    || matrixShape == null
                    || matrixShape.length() != 2
                    || matrixShape.optInt(0, -1) != 9
                    || matrixShape.optInt(1, -1) != expectedNames.length
                    || featureNames.length() != expectedNames.length) {
                return invalid("time_grid_contract_mismatch");
            }
            for (int index = 0; index < expectedNames.length; index++) {
                if (!expectedNames[index].equals(featureNames.optString(index, ""))) {
                    return invalid("time_grid_contract_mismatch");
                }
            }
            int classCount = scaler.optInt("classCount", 0);
            String[] labelOrder = stringArray(scaler.optJSONArray("labelOrder"));
            if (!AispectCanonicalModelContract.matchesLabels(labelOrder)
                    || classCount != labelOrder.length
                    || assignment.classCount != classCount
                    || !Arrays.equals(assignment.labelOrder, labelOrder)) {
                return invalid("output_schema_mismatch");
            }
            AispectTimeGridGroupNormRuntime runtime = AispectTimeGridGroupNormRuntime.load(
                    assignment.modelId,
                    weights,
                    scaler
            );
            return runtime == null
                    ? invalid("time_grid_tensor_shape_mismatch")
                    : new Result(true, "valid");
        } catch (JSONException | RuntimeException error) {
            return invalid("model_json_invalid");
        }
    }

    private static boolean hasBatchNorm(JSONObject weights, String prefix, int size) throws JSONException {
        return isVector(tensor(weights, prefix + ".weight"), size)
                && isVector(tensor(weights, prefix + ".bias"), size)
                && isVector(tensor(weights, prefix + ".running_mean"), size)
                && isVector(tensor(weights, prefix + ".running_var"), size);
    }

    private static Tensor tensor(JSONObject weights, String key) throws JSONException {
        JSONObject object = weights.getJSONObject(key);
        JSONArray shapeArray = object.getJSONArray("shape");
        JSONArray values = object.getJSONArray("values");
        int[] shape = new int[shapeArray.length()];
        long expectedValues = 1L;
        for (int i = 0; i < shape.length; i++) {
            shape[i] = shapeArray.getInt(i);
            if (shape[i] <= 0) {
                throw new JSONException("invalid tensor dimension");
            }
            expectedValues *= shape[i];
            if (expectedValues > Integer.MAX_VALUE) {
                throw new JSONException("tensor too large");
            }
        }
        if (shape.length == 0 || values.length() != (int) expectedValues) {
            throw new JSONException("tensor value count mismatch");
        }
        for (int i = 0; i < values.length(); i++) {
            if (!isFiniteNumber(values.opt(i))) {
                throw new JSONException("tensor value invalid");
            }
        }
        return new Tensor(shape);
    }

    private static boolean isConv(Tensor tensor, int inputChannels) {
        return tensor.shape.length == 3
                && tensor.shape[0] > 0
                && tensor.shape[1] == inputChannels
                && tensor.shape[2] == 3;
    }

    private static boolean isVector(Tensor tensor, int size) {
        return tensor.shape.length == 1 && tensor.shape[0] == size;
    }

    private static boolean isMatrix(Tensor tensor, int rows, int columns) {
        return tensor.shape.length == 2 && tensor.shape[0] == rows && tensor.shape[1] == columns;
    }

    private static boolean isSupportedFeature(String name) {
        if (SUPPORTED_FEATURES.contains(name)) {
            return true;
        }
        String prefix = "gaussian.map.";
        if (name == null || !name.startsWith(prefix) || name.length() == prefix.length()) {
            return false;
        }
        String indexText = name.substring(prefix.length());
        for (int i = 0; i < indexText.length(); i++) {
            char character = indexText.charAt(i);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        try {
            int index = Integer.parseInt(indexText);
            return index >= 0 && index <= 80;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private static boolean hasValidFrameIndices(JSONArray frameIndices) {
        int previous = Integer.MIN_VALUE;
        for (int i = 0; i < frameIndices.length(); i++) {
            Object value = frameIndices.opt(i);
            if (!(value instanceof Number)) {
                return false;
            }
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || Math.rint(number) != number) {
                return false;
            }
            int index = (int) number;
            if (index < AispectSignalWindowBuilder.MIN_FRAME_INDEX
                    || index > AispectSignalWindowBuilder.MAX_FRAME_INDEX
                    || index <= previous) {
                return false;
            }
            previous = index;
        }
        return true;
    }

    private static boolean matchesInputContract(
            AispectRemoteModelAssignment assignment,
            JSONArray featureNames,
            JSONArray frameIndices
    ) {
        if (assignment.inputFeatureNames.length == 0 && assignment.inputFrameIndices.length == 0) {
            return true;
        }
        if (assignment.inputFeatureNames.length != featureNames.length()
                || assignment.inputFrameIndices.length != frameIndices.length()) {
            return false;
        }
        for (int index = 0; index < assignment.inputFeatureNames.length; index++) {
            if (!assignment.inputFeatureNames[index].equals(featureNames.optString(index, ""))) {
                return false;
            }
        }
        for (int index = 0; index < assignment.inputFrameIndices.length; index++) {
            if (assignment.inputFrameIndices[index] != frameIndices.optInt(index, Integer.MIN_VALUE)) {
                return false;
            }
        }
        return true;
    }

    private static int[] intArray(JSONArray values) {
        int[] output = new int[values.length()];
        for (int index = 0; index < values.length(); index++) {
            output[index] = values.optInt(index, Integer.MIN_VALUE);
        }
        return output;
    }

    private static boolean isFiniteNumber(Object value) {
        if (!(value instanceof Number)) {
            return false;
        }
        double number = ((Number) value).doubleValue();
        return !Double.isNaN(number) && !Double.isInfinite(number);
    }

    private static String[] stringArray(JSONArray array) {
        if (array == null) {
            return new String[0];
        }
        String[] output = new String[array.length()];
        for (int i = 0; i < array.length(); i++) {
            output[i] = array.optString(i, "");
        }
        return output;
    }

    private static Result invalid(String reason) {
        return new Result(false, reason);
    }

    private static final class Tensor {
        final int[] shape;

        Tensor(int[] shape) {
            this.shape = shape;
        }
    }
}
