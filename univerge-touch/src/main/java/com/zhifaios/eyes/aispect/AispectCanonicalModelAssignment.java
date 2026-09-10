package com.zhifaios.eyes.aispect;

import org.json.JSONArray;
import org.json.JSONObject;

final class AispectCanonicalModelAssignment {
    static final class Result {
        final boolean accepted;
        final AispectRemoteModelAssignment assignment;
        final String reason;

        private Result(boolean accepted, AispectRemoteModelAssignment assignment, String reason) {
            this.accepted = accepted;
            this.assignment = assignment;
            this.reason = reason == null ? "" : reason;
        }
    }

    private static final String PLATFORM_ANDROID = "android";
    private static final String MODEL_TYPE_CNN_JSON = "cnn_json";
    private static final String MODEL_TYPE_TIME_GRID_GROUPNORM = "time_grid_groupnorm_cnn_v1";
    private static final String MODEL_TYPE_CAUSAL_TOUCH = "causal_touch_cnn_v1";
    private static final String MODEL_TYPE_CAUSAL_TOUCH_JSON = "aispect-causal-touch-json-v1";
    private static final String MODEL_TYPE_FIELDWISE_SIZE = "aispect-fieldwise-size-json-v1";
    private static final String FEATURE_SCHEMA_ANDROID_V1 = "android-touch-cnn-v1";
    private static final String FEATURE_SCHEMA_TIME_GRID_GROUPNORM = "aispect-time-grid-groupnorm-v1";
    private static final String FEATURE_SCHEMA_CAUSAL_TOUCH = "aispect-causal-touch-cnn-v1";
    private static final String FEATURE_SCHEMA_FIELDWISE_SIZE = "aispect-fieldwise-size-cnn-v1";

    private AispectCanonicalModelAssignment() {
    }

    static Result parse(JSONObject root, String sdkVersion) {
        if (root == null || root.optInt("schemaVersion", 0) != 1) {
            return reject("schema_version_unsupported");
        }
        if (!PLATFORM_ANDROID.equals(root.optString("platform", ""))) {
            return reject("assignment_platform_mismatch");
        }
        String selectedModelId = root.optString("selectedModelId", "");
        JSONArray models = root.optJSONArray("models");
        if (selectedModelId.isEmpty() || models == null) {
            return reject("selected_model_missing");
        }
        JSONObject selected = null;
        for (int index = 0; index < models.length(); index++) {
            JSONObject candidate = models.optJSONObject(index);
            if (candidate != null && selectedModelId.equals(candidate.optString("id", ""))) {
                if (selected != null) {
                    return reject("selected_model_ambiguous");
                }
                selected = candidate;
            }
        }
        if (selected == null) {
            return reject("selected_model_missing");
        }
        if (!PLATFORM_ANDROID.equals(selected.optString("platform", ""))) {
            return reject("model_platform_mismatch");
        }
        String modelType = selected.optString("modelType", "");
        boolean legacyModel = MODEL_TYPE_CNN_JSON.equals(modelType);
        boolean timeGridModel = MODEL_TYPE_TIME_GRID_GROUPNORM.equals(modelType);
        boolean causalModel = MODEL_TYPE_CAUSAL_TOUCH.equals(modelType)
                || MODEL_TYPE_CAUSAL_TOUCH_JSON.equals(modelType);
        boolean fieldwiseSizeModel = MODEL_TYPE_FIELDWISE_SIZE.equals(modelType);
        if (!legacyModel && !timeGridModel && !causalModel && !fieldwiseSizeModel) {
            return reject("model_type_unsupported");
        }
        String featureSchemaId = selected.optString("featureSchemaId", "");
        if (legacyModel && !FEATURE_SCHEMA_ANDROID_V1.equals(featureSchemaId)
                && !FEATURE_SCHEMA_CAUSAL_TOUCH.equals(featureSchemaId)) {
            return reject("feature_schema_unsupported");
        }
        if (timeGridModel && !FEATURE_SCHEMA_TIME_GRID_GROUPNORM.equals(featureSchemaId)) {
            return reject("feature_schema_unsupported");
        }
        if (causalModel && !FEATURE_SCHEMA_CAUSAL_TOUCH.equals(featureSchemaId)) {
            return reject("feature_schema_unsupported");
        }
        if (fieldwiseSizeModel && !FEATURE_SCHEMA_FIELDWISE_SIZE.equals(featureSchemaId)) {
            return reject("feature_schema_unsupported");
        }
        if (!hasCanonicalLabels(selected.optJSONArray("labelOrder"))) {
            return reject("label_order_invalid");
        }
        String minimumSdkVersion = selected.isNull("minimumSdkVersion")
                ? ""
                : selected.optString("minimumSdkVersion", "");
        if (!minimumSdkVersion.isEmpty() && compareVersions(sdkVersion, minimumSdkVersion) < 0) {
            return reject("minimum_sdk_version_incompatible");
        }
        JSONObject inputContract = selected.optJSONObject("inputContract");
        String[] featureNames = stringArray(inputContract == null ? null : inputContract.optJSONArray("featureNames"));
        int[] frameIndices = intArray(inputContract == null ? null : inputContract.optJSONArray("frameIndices"));
        String windowMode = inputContract == null ? "" : inputContract.optString("windowMode", "");
        int captureDelayMs = inputContract == null ? -1 : inputContract.optInt("captureDelayMs", -1);
        if (causalModel || (legacyModel && FEATURE_SCHEMA_CAUSAL_TOUCH.equals(featureSchemaId))) {
            if (!matchesCausalContract(inputContract, featureNames, frameIndices)) {
                return reject("causal_contract_mismatch");
            }
        } else if (fieldwiseSizeModel) {
            if (!matchesFieldwiseSizeContract(inputContract, featureNames, frameIndices)) {
                return reject("fieldwise_size_contract_mismatch");
            }
        } else if (legacyModel) {
            if (!AispectCanonicalModelContract.matches(windowMode, captureDelayMs, frameIndices, featureNames)) {
                return reject("release_contract_mismatch");
            }
            if (!hasStrictAscendingFrames(frameIndices)) {
                return reject("input_contract_invalid");
            }
        } else {
            if (!matchesTimeGridContract(inputContract, featureNames)) {
                return reject("time_grid_contract_mismatch");
            }
            frameIndices = new int[0];
            windowMode = "";
            captureDelayMs = -1;
        }
        AispectRemoteModelAssignment assignment = AispectRemoteModelAssignment.fromCanonicalModel(
                root,
                selected,
                featureNames,
                frameIndices,
                windowMode,
                captureDelayMs
        );
        if (!assignment.isUsable()) {
            return reject("assignment_rejected");
        }
        return new Result(true, assignment, "valid");
    }

    private static boolean hasCanonicalLabels(JSONArray labels) {
        if (labels == null) {
            return false;
        }
        String[] values = new String[labels.length()];
        for (int index = 0; index < labels.length(); index++) {
            values[index] = labels.optString(index, "");
        }
        return AispectCanonicalModelContract.matchesLabels(values);
    }

    private static boolean matchesTimeGridContract(JSONObject inputContract, String[] featureNames) {
        if (inputContract == null) {
            return false;
        }
        String featureContract = inputContract.optString("featureContract", "");
        if (!AispectCausalPressFeatureBuilder.isTimeGridFeatureContract(featureContract)
                || inputContract.optInt("frameCount", 0) != 9
                || !"press".equals(inputContract.optString("windowMode", ""))
                || inputContract.optInt("captureDelayMs", -1) != 25) {
            return false;
        }
        String[] expectedNames = AispectCausalPressFeatureBuilder.featureNames(featureContract);
        if (featureNames.length != expectedNames.length) {
            return false;
        }
        for (int index = 0; index < expectedNames.length; index++) {
            if (!expectedNames[index].equals(featureNames[index])) {
                return false;
            }
        }
        JSONArray matrixShape = inputContract.optJSONArray("matrixShape");
        return matrixShape != null
                && matrixShape.length() == 2
                && matrixShape.optInt(0, -1) == 9
                && matrixShape.optInt(1, -1) == expectedNames.length;
    }

    private static boolean matchesCausalContract(JSONObject inputContract, String[] featureNames, int[] frameIndices) {
        if (inputContract == null
                || !"causal_touch_relative_v1".equals(inputContract.optString("featureContract", ""))
                || inputContract.optInt("frameCount", 0) != 9
                || !"press".equals(inputContract.optString("windowMode", ""))
                || inputContract.optInt("captureDelayMs", -1) != 25) {
            return false;
        }
        String[] expectedNames = AispectCausalPressFeatureBuilder.featureNames("causal_touch_relative_v1");
        if (featureNames.length != expectedNames.length || frameIndices.length != 9) {
            return false;
        }
        for (int index = 0; index < expectedNames.length; index++) {
            if (!expectedNames[index].equals(featureNames[index])) {
                return false;
            }
        }
        int[] expectedFrames = new int[]{-3, -2, -1, 0, 1, 2, 3, 4, 5};
        if (!java.util.Arrays.equals(expectedFrames, frameIndices)) {
            return false;
        }
        JSONArray matrixShape = inputContract.optJSONArray("matrixShape");
        return matrixShape != null
                && matrixShape.length() == 2
                && matrixShape.optInt(0, -1) == 9
                && matrixShape.optInt(1, -1) == expectedNames.length;
    }

    private static boolean matchesFieldwiseSizeContract(
            JSONObject inputContract,
            String[] featureNames,
            int[] frameIndices
    ) {
        if (inputContract == null
                || !AispectFieldwiseSizeFeatureBuilder.isFeatureContract(
                inputContract.optString("featureContract", ""))
                || !"press".equals(inputContract.optString("windowMode", ""))) {
            return false;
        }
        int frameCount = inputContract.optInt("frameCount", 0);
        if (frameCount != 9 && frameCount != 13 && frameCount != 17
                && frameCount != 21 && frameCount != 25) {
            return false;
        }
        if (featureNames.length != AispectFieldwiseSizeFeatureBuilder.featureNames().length
                || frameIndices.length != frameCount) {
            return false;
        }
        if (!java.util.Arrays.equals(
                AispectFieldwiseSizeFeatureBuilder.featureNames(), featureNames)) {
            return false;
        }
        for (int index = 0; index < frameCount; index++) {
            if (frameIndices[index] != index) {
                return false;
            }
        }
        JSONArray offsets = inputContract.optJSONArray("gridOffsetsMs");
        if (!AispectFieldwiseSizeFeatureBuilder.hasValidTimeGrid(
                offsets,
                frameCount,
                inputContract.optLong("captureDelayMs", -1L)
        )) {
            return false;
        }
        JSONArray matrixShape = inputContract.optJSONArray("matrixShape");
        return matrixShape != null
                && matrixShape.length() == 2
                && matrixShape.optInt(0, -1) == frameCount
                && matrixShape.optInt(1, -1) == featureNames.length;
    }

    private static boolean hasStrictAscendingFrames(int[] frameIndices) {
        int previous = Integer.MIN_VALUE;
        for (int frameIndex : frameIndices) {
            if (frameIndex < AispectSignalWindowBuilder.MIN_FRAME_INDEX
                    || frameIndex > AispectSignalWindowBuilder.MAX_FRAME_INDEX
                    || frameIndex <= previous) {
                return false;
            }
            previous = frameIndex;
        }
        return true;
    }

    private static String[] stringArray(JSONArray values) {
        if (values == null) {
            return new String[0];
        }
        String[] output = new String[values.length()];
        for (int index = 0; index < values.length(); index++) {
            output[index] = values.optString(index, "");
            if (output[index].isEmpty()) {
                return new String[0];
            }
        }
        return output;
    }

    private static int[] intArray(JSONArray values) {
        if (values == null) {
            return new int[0];
        }
        int[] output = new int[values.length()];
        for (int index = 0; index < values.length(); index++) {
            Object value = values.opt(index);
            if (!(value instanceof Number)) {
                return new int[0];
            }
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || Math.rint(number) != number) {
                return new int[0];
            }
            output[index] = (int) number;
        }
        return output;
    }

    private static int compareVersions(String current, String required) {
        int[] currentParts = versionParts(current);
        int[] requiredParts = versionParts(required);
        if (currentParts == null || requiredParts == null) {
            return -1;
        }
        for (int index = 0; index < currentParts.length; index++) {
            if (currentParts[index] != requiredParts[index]) {
                return currentParts[index] - requiredParts[index];
            }
        }
        return 0;
    }

    private static int[] versionParts(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        String[] parts = normalized.split("\\.");
        if (parts.length == 0 || parts.length > 3) {
            return null;
        }
        int[] output = new int[]{0, 0, 0};
        try {
            for (int index = 0; index < parts.length; index++) {
                if (parts[index].isEmpty()) {
                    return null;
                }
                output[index] = Integer.parseInt(parts[index]);
                if (output[index] < 0) {
                    return null;
                }
            }
            return output;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static Result reject(String reason) {
        return new Result(false, null, reason);
    }
}
