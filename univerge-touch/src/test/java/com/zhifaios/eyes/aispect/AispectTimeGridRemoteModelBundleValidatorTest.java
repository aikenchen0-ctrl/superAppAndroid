package com.zhifaios.eyes.aispect;

import com.zhifa.univerge.eyes.aispect.AispectCausalPressFeatureBuilder;
import com.zhifa.univerge.eyes.aispect.AispectModelBundleValidator;
import com.zhifa.univerge.eyes.aispect.AispectRemoteModelAssignment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class AispectTimeGridRemoteModelBundleValidatorTest {
    private static final String DELTA_GRAVITY =
            "causal_time_grid_imu11_touch7_mask5_delta_gravity_v1";
    private static final String NO_GRAVITY =
            "causal_time_grid_touch7_mask5_no_gravity_v1";

    @Test
    public void acceptsPublishedDeltaGravityBundleThroughExistingRemoteProtocol() throws Exception {
        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                assignment(DELTA_GRAVITY),
                weights(23),
                scaler(DELTA_GRAVITY)
        );

        Assert.assertTrue(result.reason, result.valid);
    }

    @Test
    public void acceptsPublishedNoGravityBundleThroughExistingRemoteProtocol() throws Exception {
        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                assignment(NO_GRAVITY),
                weights(20),
                scaler(NO_GRAVITY)
        );

        Assert.assertTrue(result.reason, result.valid);
    }

    @Test
    public void rejectsChannelsThatDoNotMatchTimeGridFeatureContract() throws Exception {
        AispectModelBundleValidator.Result result = AispectModelBundleValidator.validate(
                assignment(DELTA_GRAVITY),
                weights(20),
                scalerWithFeatureCount(DELTA_GRAVITY, 20)
        );

        Assert.assertFalse(result.valid);
        Assert.assertEquals("time_grid_contract_mismatch", result.reason);
    }

    private static AispectRemoteModelAssignment assignment(String featureContract) throws Exception {
        JSONObject model = new JSONObject()
                .put("id", "remote_time_grid_" + featureContract)
                .put("version", "1.0.0")
                .put("platform", "android")
                .put("featureSchemaId", "aispect-time-grid-groupnorm-v1")
                .put("modelType", "time_grid_groupnorm_cnn_v1")
                .put("classCount", 4)
                .put("labelOrder", labels())
                .put("weights", artifact("weights.json", 'a'))
                .put("scaler", artifact("scaler.json", 'b'));
        return AispectRemoteModelAssignment.fromJson(new JSONObject()
                .put("schemaVersion", 1)
                .put("assignmentId", "time-grid-contract")
                .put("model", model));
    }

    private static JSONObject scaler(String featureContract) throws Exception {
        String[] names = AispectCausalPressFeatureBuilder.featureNames(featureContract);
        return scaler(featureContract, names);
    }

    private static JSONObject scalerWithFeatureCount(String featureContract, int count) throws Exception {
        String[] expected = AispectCausalPressFeatureBuilder.featureNames(featureContract);
        String[] names = new String[count];
        for (int index = 0; index < count; index++) {
            names[index] = index < expected.length ? expected[index] : "unexpected_" + index;
        }
        return scaler(featureContract, names);
    }

    private static JSONObject scaler(String featureContract, String[] names) throws Exception {
        JSONArray featureNames = new JSONArray();
        JSONArray channels = new JSONArray();
        for (String name : names) {
            featureNames.put(name);
            channels.put(new JSONObject()
                    .put("name", name)
                    .put("strategy", "identity")
                    .put("center", 0.0)
                    .put("scale", 1.0)
                    .put("requiresTouchAvailability", false));
        }
        return new JSONObject()
                .put("runtimeArchitecture", "time_grid_groupnorm_cnn_v1")
                .put("featureContract", featureContract)
                .put("featureNames", featureNames)
                .put("labelOrder", labels())
                .put("classCount", 4)
                .put("frameCount", 9)
                .put("matrixShape", new JSONArray().put(9).put(names.length))
                .put("channels", channels);
    }

    private static JSONObject weights(int channels) throws Exception {
        JSONObject output = new JSONObject();
        putTensor(output, "conv1.weight", new int[]{32, channels, 3});
        putTensor(output, "conv1.bias", new int[]{32});
        putTensor(output, "norm1.weight", new int[]{32});
        putTensor(output, "norm1.bias", new int[]{32});
        putTensor(output, "conv2.weight", new int[]{32, 32, 3});
        putTensor(output, "conv2.bias", new int[]{32});
        putTensor(output, "norm2.weight", new int[]{32});
        putTensor(output, "norm2.bias", new int[]{32});
        putTensor(output, "output.weight", new int[]{4, 32});
        putTensor(output, "output.bias", new int[]{4});
        return output;
    }

    private static void putTensor(JSONObject weights, String key, int[] shape) throws Exception {
        int valueCount = 1;
        JSONArray shapeArray = new JSONArray();
        for (int dimension : shape) {
            shapeArray.put(dimension);
            valueCount *= dimension;
        }
        JSONArray values = new JSONArray();
        for (int index = 0; index < valueCount; index++) {
            values.put(0.0);
        }
        weights.put(key, new JSONObject().put("shape", shapeArray).put("values", values));
    }

    private static JSONObject artifact(String path, char checksumCharacter) throws Exception {
        return new JSONObject()
                .put("url", "https://example.com/" + path)
                .put("sha256", repeat(checksumCharacter, 64))
                .put("sizeBytes", 1);
    }

    private static JSONArray labels() {
        return new JSONArray()
                .put("thumb_light")
                .put("thumb_heavy")
                .put("index_light")
                .put("index_heavy");
    }

    private static String repeat(char value, int count) {
        StringBuilder output = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
