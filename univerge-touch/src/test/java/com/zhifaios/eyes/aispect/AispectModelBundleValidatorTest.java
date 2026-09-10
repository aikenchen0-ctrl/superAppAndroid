package com.zhifaios.eyes.aispect;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class AispectModelBundleValidatorTest {
    @Test
    public void acceptsCompatibleFourClassBundle() throws Exception {
        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                validAssignment(),
                validWeights(),
                validScaler()
        );

        Assert.assertTrue(result.reason, result.valid);
    }

    @Test
    public void rejectsPressScalerContract() throws Exception {
        JSONObject scaler = validScaler();
        scaler.put("windowMode", "press");
        scaler.put("captureDelayMs", 100);

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                validAssignment(), validWeights(), scaler
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("release_contract_mismatch", result.reason);
    }

    @Test
    public void rejectsUnsupportedFeatureName() throws Exception {
        JSONObject scaler = validScaler();
        scaler.getJSONArray("featureNames").put(1, "unsupported.feature");

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                validAssignment(),
                validWeights(),
                scaler
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("feature_unsupported", result.reason);
    }

    @Test
    public void rejectsMismatchedScalerLengths() throws Exception {
        JSONObject scaler = validScaler();
        scaler.put("scale", new JSONArray().put(1.0));

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                validAssignment(),
                validWeights(),
                scaler
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("scaler_length_mismatch", result.reason);
    }

    @Test
    public void rejectsIncompatibleTensorShape() throws Exception {
        JSONObject weights = validWeights();
        putTensor(weights, "features.4.weight", new int[]{2, 3, 3});

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                validAssignment(),
                weights,
                validScaler()
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("tensor_shape_mismatch", result.reason);
    }

    @Test
    public void rejectsInvalidScaleValue() throws Exception {
        JSONObject scaler = validScaler();
        scaler.getJSONArray("scale").put(1, "NaN");

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                validAssignment(),
                validWeights(),
                scaler
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("scale_invalid", result.reason);
    }

    @Test
    public void rejectsIncorrectOutputClassCount() throws Exception {
        JSONObject scaler = validScaler();
        scaler.put("classCount", 2);
        scaler.put("labelOrder", new JSONArray().put("heavy").put("light"));

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                validAssignment(),
                validWeights(),
                scaler
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("output_schema_mismatch", result.reason);
    }

    @Test
    public void rejectsNonCanonicalSupportedFeature() throws Exception {
        JSONObject scaler = validScaler();
        scaler.getJSONArray("featureNames").put(1, "gaussian.map.0");

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                validAssignment(),
                validWeights(),
                scaler
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("release_contract_mismatch", result.reason);
    }

    @Test
    public void rejectsOutOfWindowFrameIndex() throws Exception {
        JSONObject scaler = validScaler();
        scaler.getJSONArray("frameIndices").put(14, 16);

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                validAssignment(),
                validWeights(),
                scaler
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("frame_indices_invalid", result.reason);
    }

    @Test
    public void rejectsUnorderedFrameIndices() throws Exception {
        JSONObject scaler = validScaler();
        scaler.getJSONArray("frameIndices").put(6, 2);

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                validAssignment(),
                validWeights(),
                scaler
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("frame_indices_invalid", result.reason);
    }

    @Test
    public void acceptsCausalTouchRelativeLegacyCnnBundle() throws Exception {
        AispectRemoteModelAssignment assignment = causalTouchRelativeAssignment();
        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                assignment,
                causalTouchRelativeWeights(),
                causalTouchRelativeScaler()
        );

        Assert.assertTrue(result.reason, result.valid);
    }

    @Test
    public void acceptsFieldwiseSizeBundleWithTwentyTwoChannels() throws Exception {
        AispectRemoteModelAssignment assignment = fieldwiseSizeAssignment(13);
        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                assignment,
                fieldwiseSizeWeights(13),
                fieldwiseSizeScaler(13)
        );

        Assert.assertTrue(result.reason, result.valid);
    }

    @Test
    public void rejectsFieldwiseSizeBundleWithShiftedGrid() throws Exception {
        JSONObject scaler = fieldwiseSizeScaler(9);
        JSONArray offsets = scaler.getJSONArray("gridOffsetsMs");
        for (int index = 0; index < offsets.length(); index++) {
            offsets.put(index, offsets.getInt(index) + 5);
        }
        scaler.put("captureDelayMs", 30);

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                fieldwiseSizeAssignment(9),
                fieldwiseSizeWeights(9),
                scaler
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("fieldwise_size_contract_mismatch", result.reason);
    }

    @Test
    public void rejectsCausalTouchRelativeBundleWithWrongFrameIndices() throws Exception {
        AispectRemoteModelAssignment assignment = causalTouchRelativeAssignment();
        JSONObject scaler = causalTouchRelativeScaler();
        scaler.getJSONArray("frameIndices").put(0, 0);

        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                assignment,
                causalTouchRelativeWeights(),
                scaler
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("causal_contract_mismatch", result.reason);
    }

    private static AispectRemoteModelAssignment validAssignment() throws Exception {
        return AispectRemoteModelAssignment.fromJson(new JSONObject("{"
                + "\"schemaVersion\":1,"
                + "\"assignmentId\":\"assign-validator\","
                + "\"expiresAt\":1790000000,"
                + "\"model\":{"
                + "\"id\":\"android_validator_model\","
                + "\"version\":\"1.0.0\","
                + "\"platform\":\"android\","
                + "\"featureSchemaId\":\"android-touch-cnn-v1\","
                + "\"classCount\":4,"
                + "\"labelOrder\":[\"thumb_light\",\"thumb_heavy\",\"index_light\",\"index_heavy\"],"
                + "\"weights\":{\"url\":\"https://example.com/weights.json\",\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"sizeBytes\":1},"
                + "\"scaler\":{\"url\":\"https://example.com/scaler.json\",\"sha256\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\"sizeBytes\":1}"
                + "}"
                + "}"));
    }

    private static JSONObject validScaler() throws Exception {
        return new JSONObject()
                .put("center", repeatedNumbers(0.0, 21))
                .put("scale", repeatedNumbers(1.0, 21))
                .put("featureNames", new JSONArray(AispectCanonicalModelContract.featureNames()))
                .put("frameIndices", new JSONArray(AispectCanonicalModelContract.frameIndices()))
                .put("frameCount", 15)
                .put("windowMode", "release")
                .put("captureDelayMs", 0)
                .put("classCount", 4)
                .put("labelOrder", new JSONArray()
                        .put("thumb_light")
                        .put("thumb_heavy")
                        .put("index_light")
                        .put("index_heavy"));
    }

    private static JSONObject validWeights() throws Exception {
        JSONObject weights = new JSONObject();
        putTensor(weights, "features.0.weight", new int[]{2, 21, 3});
        putTensor(weights, "features.0.bias", new int[]{2});
        putBatchNorm(weights, "features.1", 2);
        putTensor(weights, "features.4.weight", new int[]{2, 2, 3});
        putTensor(weights, "features.4.bias", new int[]{2});
        putBatchNorm(weights, "features.5", 2);
        putTensor(weights, "features.9.weight", new int[]{2, 2, 3});
        putTensor(weights, "features.9.bias", new int[]{2});
        putBatchNorm(weights, "features.10", 2);
        putTensor(weights, "classifier.0.weight", new int[]{64, 6});
        putTensor(weights, "classifier.0.bias", new int[]{64});
        putTensor(weights, "classifier.3.weight", new int[]{4, 64});
        putTensor(weights, "classifier.3.bias", new int[]{4});
        return weights;
    }

    private static AispectRemoteModelAssignment causalTouchRelativeAssignment() throws Exception {
        return AispectRemoteModelAssignment.fromJson(new JSONObject("{"
                + "\"schemaVersion\":1,"
                + "\"assignmentId\":\"assign-causal\","
                + "\"expiresAt\":1790000000,"
                + "\"model\":{"
                + "\"id\":\"causal-touch-model\","
                + "\"version\":\"2026.09.04\","
                + "\"platform\":\"android\","
                + "\"featureSchemaId\":\"aispect-causal-touch-cnn-v1\","
                + "\"modelType\":\"cnn_json\","
                + "\"classCount\":4,"
                + "\"labelOrder\":[\"thumb_light\",\"thumb_heavy\",\"index_light\",\"index_heavy\"],"
                + "\"weights\":{\"url\":\"https://example.com/weights.json\",\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"sizeBytes\":1},"
                + "\"scaler\":{\"url\":\"https://example.com/scaler.json\",\"sha256\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\"sizeBytes\":1}"
                + "}}"));
    }

    private static JSONObject causalTouchRelativeScaler() throws Exception {
        String[] names = AispectCausalPressFeatureBuilder.featureNames("causal_touch_relative_v1");
        JSONArray featureNames = new JSONArray();
        JSONArray center = new JSONArray();
        JSONArray scale = new JSONArray();
        for (String name : names) {
            featureNames.put(name);
            center.put(0.0);
            scale.put(1.0);
        }
        return new JSONObject()
                .put("center", center)
                .put("scale", scale)
                .put("featureNames", featureNames)
                .put("frameIndices", new JSONArray().put(-3).put(-2).put(-1).put(0).put(1).put(2).put(3).put(4).put(5))
                .put("frameCount", 9)
                .put("featureContract", "causal_touch_relative_v1")
                .put("runtimeArchitecture", "causal_touch_cnn_v1")
                .put("windowMode", "press")
                .put("captureDelayMs", 25)
                .put("classCount", 4)
                .put("labelOrder", new JSONArray()
                        .put("thumb_light")
                        .put("thumb_heavy")
                        .put("index_light")
                        .put("index_heavy"));
    }

    private static JSONObject causalTouchRelativeWeights() throws Exception {
        JSONObject weights = new JSONObject();
        putTensor(weights, "features.0.weight", new int[]{32, 19, 3});
        putTensor(weights, "features.0.bias", new int[]{32});
        putBatchNorm(weights, "features.1", 32);
        putTensor(weights, "features.1.num_batches_tracked", new int[]{});
        putTensor(weights, "features.4.weight", new int[]{64, 32, 3});
        putTensor(weights, "features.4.bias", new int[]{64});
        putBatchNorm(weights, "features.5", 64);
        putTensor(weights, "features.5.num_batches_tracked", new int[]{});
        putTensor(weights, "features.9.weight", new int[]{128, 64, 3});
        putTensor(weights, "features.9.bias", new int[]{128});
        putBatchNorm(weights, "features.10", 128);
        putTensor(weights, "features.10.num_batches_tracked", new int[]{});
        putTensor(weights, "classifier.0.weight", new int[]{64, 256});
        putTensor(weights, "classifier.0.bias", new int[]{64});
        putTensor(weights, "classifier.3.weight", new int[]{4, 64});
        putTensor(weights, "classifier.3.bias", new int[]{4});
        return weights;
    }

    private static AispectRemoteModelAssignment fieldwiseSizeAssignment(int frameCount) throws Exception {
        return AispectRemoteModelAssignment.fromJson(new JSONObject("{"
                + "\"schemaVersion\":1,"
                + "\"assignmentId\":\"assign-fieldwise\","
                + "\"expiresAt\":1790000000,"
                + "\"model\":{"
                + "\"id\":\"fieldwise-size-model\","
                + "\"version\":\"1.0.0\","
                + "\"platform\":\"android\","
                + "\"modelType\":\"aispect-fieldwise-size-json-v1\","
                + "\"featureSchemaId\":\"aispect-fieldwise-size-cnn-v1\","
                + "\"classCount\":4,"
                + "\"labelOrder\":[\"thumb_light\",\"thumb_heavy\",\"index_light\",\"index_heavy\"],"
                + "\"weights\":{\"url\":\"https://example.com/weights.json\",\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"sizeBytes\":1},"
                + "\"scaler\":{\"url\":\"https://example.com/scaler.json\",\"sha256\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\"sizeBytes\":1}"
                + "}}"));
    }

    private static JSONObject fieldwiseSizeScaler(int frameCount) throws Exception {
        JSONArray featureNames = new JSONArray();
        JSONArray center = new JSONArray();
        JSONArray scale = new JSONArray();
        for (String featureName : AispectFieldwiseSizeFeatureBuilder.featureNames()) {
            featureNames.put(featureName);
            center.put(0.0);
            scale.put(1.0);
        }
        JSONArray frameIndices = new JSONArray();
        JSONArray offsets = new JSONArray();
        int firstOffset = -15 - ((frameCount - 9) / 2) * 5;
        for (int index = 0; index < frameCount; index++) {
            frameIndices.put(index);
            offsets.put(firstOffset + index * 5);
        }
        return new JSONObject()
                .put("runtimeArchitecture", "fieldwise_size_cnn_v1")
                .put("featureContract", "fieldwise_size_causal_w" + String.format(java.util.Locale.US, "%02d", frameCount) + "_v1")
                .put("featureNames", featureNames)
                .put("center", center)
                .put("scale", scale)
                .put("frameIndices", frameIndices)
                .put("gridOffsetsMs", offsets)
                .put("frameCount", frameCount)
                .put("matrixShape", new JSONArray().put(frameCount).put(22))
                .put("windowMode", "press")
                .put("captureDelayMs", offsets.getInt(frameCount - 1))
                .put("classCount", 4)
                .put("labelOrder", new JSONArray()
                        .put("thumb_light")
                        .put("thumb_heavy")
                        .put("index_light")
                        .put("index_heavy"));
    }

    private static JSONObject fieldwiseSizeWeights(int frameCount) throws Exception {
        JSONObject weights = new JSONObject();
        putTensor(weights, "features.0.weight", new int[]{32, 22, 3});
        putTensor(weights, "features.0.bias", new int[]{32});
        putBatchNorm(weights, "features.1", 32);
        putTensor(weights, "features.1.num_batches_tracked", new int[]{});
        putTensor(weights, "features.4.weight", new int[]{64, 32, 3});
        putTensor(weights, "features.4.bias", new int[]{64});
        putBatchNorm(weights, "features.5", 64);
        putTensor(weights, "features.5.num_batches_tracked", new int[]{});
        putTensor(weights, "features.9.weight", new int[]{128, 64, 3});
        putTensor(weights, "features.9.bias", new int[]{128});
        putBatchNorm(weights, "features.10", 128);
        putTensor(weights, "features.10.num_batches_tracked", new int[]{});
        putTensor(weights, "classifier.0.weight", new int[]{64, 128 * (frameCount / 4)});
        putTensor(weights, "classifier.0.bias", new int[]{64});
        putTensor(weights, "classifier.3.weight", new int[]{4, 64});
        putTensor(weights, "classifier.3.bias", new int[]{4});
        return weights;
    }

    private static JSONArray repeatedNumbers(double value, int count) throws Exception {
        JSONArray output = new JSONArray();
        for (int index = 0; index < count; index++) {
            output.put(value);
        }
        return output;
    }

    private static void putBatchNorm(JSONObject weights, String prefix, int size) throws Exception {
        putTensor(weights, prefix + ".weight", new int[]{size});
        putTensor(weights, prefix + ".bias", new int[]{size});
        putTensor(weights, prefix + ".running_mean", new int[]{size});
        putTensor(weights, prefix + ".running_var", new int[]{size});
    }

    private static void putTensor(JSONObject weights, String key, int[] shape) throws Exception {
        int valueCount = 1;
        JSONArray shapeArray = new JSONArray();
        for (int dimension : shape) {
            shapeArray.put(dimension);
            valueCount *= dimension;
        }
        JSONArray values = new JSONArray();
        for (int i = 0; i < valueCount; i++) {
            values.put(0.0);
        }
        weights.put(key, new JSONObject().put("shape", shapeArray).put("values", values));
    }
}
