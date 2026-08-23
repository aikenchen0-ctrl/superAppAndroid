package com.zhifaios.eyes.aispect;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class AispectCanonicalModelAssignmentTest {
    @Test
    public void selectsTheUniqueCanonicalAndroidModel() throws Exception {
        AispectCanonicalModelAssignment.Result result = AispectCanonicalModelAssignment.parse(
                canonicalManifest(),
                "1.0.0"
        );

        Assert.assertTrue(result.accepted);
        Assert.assertEquals("android-touch-model", result.assignment.modelId);
        Assert.assertEquals("1.2.0", result.assignment.version);
        Assert.assertEquals("cnn_json", result.assignment.modelType);
        Assert.assertArrayEquals(featureNames(), result.assignment.inputFeatureNames);
        Assert.assertArrayEquals(frameIndices(), result.assignment.inputFrameIndices);
        Assert.assertEquals("release", result.assignment.inputWindowMode);
        Assert.assertEquals(0, result.assignment.inputCaptureDelayMs);
    }

    @Test
    public void rejectsMissingOrDuplicateSelectedModel() throws Exception {
        JSONObject missing = canonicalManifest();
        missing.put("selectedModelId", "not-present");
        Assert.assertEquals(
                "selected_model_missing",
                AispectCanonicalModelAssignment.parse(missing, "1.0.0").reason
        );

        JSONObject duplicate = canonicalManifest();
        duplicate.getJSONArray("models").put(duplicate.getJSONArray("models").getJSONObject(0));
        Assert.assertEquals(
                "selected_model_ambiguous",
                AispectCanonicalModelAssignment.parse(duplicate, "1.0.0").reason
        );
    }

    @Test
    public void rejectsUnsupportedCanonicalContract() throws Exception {
        JSONObject wrongPlatform = canonicalManifest();
        wrongPlatform.put("platform", "ios");
        Assert.assertEquals(
                "assignment_platform_mismatch",
                AispectCanonicalModelAssignment.parse(wrongPlatform, "1.0.0").reason
        );

        JSONObject wrongType = canonicalManifest();
        wrongType.getJSONArray("models").getJSONObject(0).put("modelType", "tflite");
        Assert.assertEquals(
                "model_type_unsupported",
                AispectCanonicalModelAssignment.parse(wrongType, "1.0.0").reason
        );

        JSONObject unsupportedAlias = canonicalManifest();
        unsupportedAlias.getJSONArray("models").getJSONObject(0).put("modelType", "aispect-json-cnn1d-v1");
        Assert.assertEquals(
                "model_type_unsupported",
                AispectCanonicalModelAssignment.parse(unsupportedAlias, "1.0.0").reason
        );

        JSONObject incompatibleSdk = canonicalManifest();
        incompatibleSdk.getJSONArray("models").getJSONObject(0).put("minimumSdkVersion", "1.1.0");
        Assert.assertEquals(
                "minimum_sdk_version_incompatible",
                AispectCanonicalModelAssignment.parse(incompatibleSdk, "1.0.0").reason
        );

        JSONObject wrongInput = canonicalManifest();
        wrongInput.getJSONArray("models").getJSONObject(0)
                .getJSONObject("inputContract")
                .put("frameIndices", new JSONArray().put(0).put(0));
        Assert.assertEquals(
                "release_contract_mismatch",
                AispectCanonicalModelAssignment.parse(wrongInput, "1.0.0").reason
        );

        JSONObject pressWindow = canonicalManifest();
        pressWindow.getJSONArray("models").getJSONObject(0)
                .getJSONObject("inputContract").put("windowMode", "press");
        Assert.assertEquals(
                "release_contract_mismatch",
                AispectCanonicalModelAssignment.parse(pressWindow, "1.0.0").reason
        );

        JSONObject wrongCaptureDelay = canonicalManifest();
        wrongCaptureDelay.getJSONArray("models").getJSONObject(0)
                .getJSONObject("inputContract").put("captureDelayMs", 100);
        Assert.assertEquals(
                "release_contract_mismatch",
                AispectCanonicalModelAssignment.parse(wrongCaptureDelay, "1.0.0").reason
        );

        JSONObject missingReleaseFrame = canonicalManifest();
        missingReleaseFrame.getJSONArray("models").getJSONObject(0)
                .getJSONObject("inputContract").put("frameIndices", new JSONArray()
                        .put(-5).put(-4).put(-3).put(-2).put(-1).put(0).put(1)
                        .put(2).put(3).put(4).put(5).put(6).put(7).put(8));
        Assert.assertEquals(
                "release_contract_mismatch",
                AispectCanonicalModelAssignment.parse(missingReleaseFrame, "1.0.0").reason
        );
    }

    @Test
    public void acceptsAndroidRuntimeNullMinimumSdkVersionAsUnspecified() throws Exception {
        JSONObject manifest = canonicalManifest();
        manifest.getJSONArray("models").getJSONObject(0).put("minimumSdkVersion", JSONObject.NULL);

        Assert.assertTrue(AispectCanonicalModelAssignment.parse(
                withAndroidNullStringBehavior(manifest),
                "1.0.0"
        ).accepted);
    }

    @Test
    public void acceptsMissingMinimumSdkVersionAsUnspecified() throws Exception {
        JSONObject manifest = canonicalManifest();
        manifest.getJSONArray("models").getJSONObject(0).remove("minimumSdkVersion");

        Assert.assertTrue(AispectCanonicalModelAssignment.parse(manifest, "1.0.0").accepted);
    }

    @Test
    public void acceptsPublishedTimeGridGroupNormCanonicalModel() throws Exception {
        JSONObject manifest = canonicalManifest();
        JSONObject model = manifest.getJSONArray("models").getJSONObject(0);
        String featureContract = "causal_time_grid_touch7_mask5_no_gravity_v1";
        model.put("modelType", "time_grid_groupnorm_cnn_v1");
        model.put("featureSchemaId", "aispect-time-grid-groupnorm-v1");
        model.put("inputContract", new JSONObject()
                .put("featureContract", featureContract)
                .put("featureNames", new JSONArray(
                        AispectCausalPressFeatureBuilder.featureNames(featureContract)
                ))
                .put("frameCount", 9)
                .put("matrixShape", new JSONArray().put(9).put(20)));

        AispectCanonicalModelAssignment.Result result = AispectCanonicalModelAssignment.parse(
                manifest,
                "1.0.0"
        );

        Assert.assertTrue(result.reason, result.accepted);
        Assert.assertEquals("time_grid_groupnorm_cnn_v1", result.assignment.modelType);
        Assert.assertEquals("aispect-time-grid-groupnorm-v1", result.assignment.featureSchemaId);
    }

    private static JSONObject withAndroidNullStringBehavior(JSONObject manifest) throws Exception {
        JSONObject sourceModel = manifest.getJSONArray("models").getJSONObject(0);
        JSONObject model = new JSONObject(sourceModel.toString()) {
            @Override
            public String optString(String name, String fallback) {
                if ("minimumSdkVersion".equals(name) && opt(name) == JSONObject.NULL) {
                    return "null";
                }
                return super.optString(name, fallback);
            }
        };
        JSONArray models = new JSONArray().put(model);
        return new JSONObject(manifest.toString()) {
            @Override
            public JSONArray optJSONArray(String name) {
                return "models".equals(name) ? models : super.optJSONArray(name);
            }
        };
    }

    static JSONObject canonicalManifest() throws Exception {
        JSONObject model = new JSONObject()
                .put("id", "android-touch-model")
                .put("version", "1.2.0")
                .put("displayName", "Android touch model")
                .put("platform", "android")
                .put("modelType", "cnn_json")
                .put("minimumSdkVersion", "1.0.0")
                .put("featureSchemaId", "android-touch-cnn-v1")
                .put("labelOrder", labels())
                .put("inputContract", new JSONObject()
                        .put("featureNames", new JSONArray(featureNames()))
                        .put("frameIndices", new JSONArray(frameIndices()))
                        .put("windowMode", "release")
                        .put("captureDelayMs", 0))
                .put("weightsURL", "https://models.example.com/api/v1/artifacts/android/android-touch-model/1.2.0/weights")
                .put("scalerURL", "https://models.example.com/api/v1/artifacts/android/android-touch-model/1.2.0/scaler")
                .put("weightsSHA256", repeat('a', 64))
                .put("scalerSHA256", repeat('b', 64))
                .put("weightsSizeBytes", 123L)
                .put("scalerSizeBytes", 456L);
        return new JSONObject()
                .put("schemaVersion", 1)
                .put("generatedAt", "2026-07-13T00:00:00Z")
                .put("platform", "android")
                .put("selectedModelId", "android-touch-model")
                .put("assignmentReason", "device_exact")
                .put("models", new JSONArray().put(model));
    }

    private static JSONArray labels() {
        return new JSONArray()
                .put("thumb_light")
                .put("thumb_heavy")
                .put("index_light")
                .put("index_heavy");
    }

    private static String[] featureNames() {
        return new String[]{
                "x_norm", "y_norm",
                "userAcceleration.x", "userAcceleration.y", "userAcceleration.z",
                "rotationRate.x", "rotationRate.y", "rotationRate.z",
                "gravity.x", "gravity.y", "gravity.z",
                "time_since_touch_down", "time_since_anchor",
                "contactPatch.cov_11", "contactPatch.cov_12", "contactPatch.cov_22",
                "contactPatch.sigmaX", "contactPatch.sigmaY",
                "touchNormalization.rawMajorPx", "touchNormalization.rawMinorPx",
                "touchNormalization.areaValue"
        };
    }

    private static int[] frameIndices() {
        return new int[]{-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    }

    private static String repeat(char value, int count) {
        StringBuilder output = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
