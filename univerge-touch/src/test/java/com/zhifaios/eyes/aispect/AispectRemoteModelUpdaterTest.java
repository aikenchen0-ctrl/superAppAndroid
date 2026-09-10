package com.zhifaios.eyes.aispect;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class AispectRemoteModelUpdaterTest {
    private static final String ASSIGNMENT_URL = "https://example.com/v1/model-assignment";
    private static final String CANONICAL_ASSIGNMENT_URL = "https://example.com/api/v1/model-assignment";
    private static final String WEIGHTS_URL = "https://example.com/v1/weights";
    private static final String SCALER_URL = "https://example.com/v1/scaler";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void activatesValidBundle() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "device-a");

        Assert.assertTrue(result.reason, result.updated);
        Assert.assertEquals("model-a", result.modelId);
        Assert.assertEquals("1.0.0", store.readCurrent().getString("version"));
        Assert.assertEquals(3, httpClient.requestCount);
    }

    @Test
    public void preparesValidBundleWithoutActivatingIt() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result prepared = updater.prepare(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.STAGED, prepared.state);
        Assert.assertNull(store.readCurrent());
        Assert.assertTrue(versionDirectory("model-a", "1.0.0").isDirectory());

        AispectRemoteModelUpdater.Result activated = updater.activate(prepared.modelId, prepared.version);

        Assert.assertEquals(AispectRemoteModelUpdater.State.ACTIVATED, activated.state);
        Assert.assertEquals("1.0.0", store.readCurrent().getString("version"));
    }

    @Test
    public void preparesCanonicalBundleWithoutUsingLegacyParser() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        FakeHttpClient httpClient = client(bundle);
        httpClient.responses.put(CANONICAL_ASSIGNMENT_URL, bytes(bundle.canonicalAssignmentJson()));
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result prepared = updater.prepareCanonical(
                CANONICAL_ASSIGNMENT_URL,
                "1.0.0"
        );

        Assert.assertEquals(AispectRemoteModelUpdater.State.STAGED, prepared.state);
        Assert.assertEquals("model-a", prepared.modelId);
        Assert.assertNull(store.readCurrent());
    }

    @Test
    public void mapsArtifactIntegrityConflictToRejectedResult() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        FakeHttpClient httpClient = new FakeHttpClient();
        httpClient.failure = new AispectModelHttpException(409);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.REJECTED, result.state);
        Assert.assertEquals("server_artifact_integrity", result.reason);
        assertOldModelRemains(store);
    }

    @Test
    public void mapsWeightsIntegrityConflictToRejectedResult() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        Bundle bundle = validBundle("model-a", "2.0.0");
        FakeHttpClient httpClient = client(bundle);
        httpClient.failures.put(WEIGHTS_URL, new AispectModelHttpException(409));
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.REJECTED, result.state);
        Assert.assertEquals("server_artifact_integrity", result.reason);
        Assert.assertEquals(2, httpClient.requestCount);
        assertOldModelRemains(store);
    }

    @Test
    public void mapsScalerIntegrityConflictToRejectedResult() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        Bundle bundle = validBundle("model-a", "2.0.0");
        FakeHttpClient httpClient = client(bundle);
        httpClient.failures.put(SCALER_URL, new AispectModelHttpException(409));
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.REJECTED, result.state);
        Assert.assertEquals("server_artifact_integrity", result.reason);
        Assert.assertEquals(3, httpClient.requestCount);
        assertOldModelRemains(store);
    }

    @Test
    public void rejectsCanonicalBundleWhenScalerDiffersFromInputContract() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        JSONObject manifest = bundle.canonicalAssignmentJson();
        manifest.getJSONArray("models").getJSONObject(0).getJSONObject("inputContract")
                .put("featureNames", new JSONArray().put("delta").put("y"));
        FakeHttpClient httpClient = client(bundle);
        httpClient.responses.put(CANONICAL_ASSIGNMENT_URL, bytes(manifest));
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.prepareCanonical(CANONICAL_ASSIGNMENT_URL, "1.0.0");

        Assert.assertEquals(AispectRemoteModelUpdater.State.REJECTED, result.state);
        Assert.assertEquals("release_contract_mismatch", result.reason);
        Assert.assertNull(store.readCurrent());
    }

    @Test
    public void rejectsCanonicalBundleWhenScalerUsesReleaseAnchorFeature() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        JSONObject scaler = new JSONObject(text(bundle.scaler));
        scaler.getJSONArray("featureNames").put(1, "time_since_anchor");
        bundle.scaler = bytes(scaler);
        bundle.refreshIntegrity();
        FakeHttpClient httpClient = client(bundle);
        JSONObject manifest = bundle.canonicalAssignmentJson();
        manifest.getJSONArray("models").getJSONObject(0).getJSONObject("inputContract")
                .getJSONArray("featureNames").put(1, "time_since_anchor");
        httpClient.responses.put(CANONICAL_ASSIGNMENT_URL, bytes(manifest));
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.prepareCanonical(CANONICAL_ASSIGNMENT_URL, "1.0.0");

        Assert.assertEquals(AispectRemoteModelUpdater.State.REJECTED, result.state);
        Assert.assertEquals("release_contract_mismatch", result.reason);
        Assert.assertNull(store.readCurrent());
    }

    @Test
    public void rejectsCanonicalBundleWhenScalerCaptureDelayDiffersFromInputContract() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        JSONObject scaler = new JSONObject(text(bundle.scaler));
        scaler.put("captureDelayMs", 101);
        bundle.scaler = bytes(scaler);
        bundle.refreshIntegrity();
        FakeHttpClient httpClient = client(bundle);
        httpClient.responses.put(CANONICAL_ASSIGNMENT_URL, bytes(bundle.canonicalAssignmentJson()));
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.prepareCanonical(CANONICAL_ASSIGNMENT_URL, "1.0.0");

        Assert.assertEquals(AispectRemoteModelUpdater.State.REJECTED, result.state);
        Assert.assertEquals("release_contract_mismatch", result.reason);
        Assert.assertNull(store.readCurrent());
    }

    @Test
    public void returnsNoChangeWithoutDownloadingArtifacts() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        store.install(bundle.assignment(), bundle.weights, bundle.scaler);
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertFalse(result.updated);
        Assert.assertEquals("no_change", result.reason);
        Assert.assertEquals(1, httpClient.requestCount);
        Assert.assertEquals("1.0.0", store.readCurrent().getString("version"));
    }

    @Test
    public void keepsCurrentWhenWeightsHashDoesNotMatch() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        Bundle bundle = validBundle("model-a", "2.0.0");
        bundle.weightsSha256 = repeat('0', 64);
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertFalse(result.updated);
        Assert.assertEquals("weights_sha256_mismatch", result.reason);
        assertOldModelRemains(store);
        Assert.assertFalse(versionDirectory("model-a", "2.0.0").exists());
    }

    @Test
    public void keepsCurrentWhenDeclaredSizeDoesNotMatch() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        Bundle bundle = validBundle("model-a", "2.0.0");
        bundle.weightsSizeBytes += 1;
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertFalse(result.updated);
        Assert.assertEquals("weights_size_mismatch", result.reason);
        assertOldModelRemains(store);
        Assert.assertFalse(versionDirectory("model-a", "2.0.0").exists());
    }

    @Test
    public void removesRejectedStagingWithoutChangingCurrent() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        Bundle bundle = validBundle("model-a", "2.0.0");
        JSONObject scaler = new JSONObject(text(bundle.scaler));
        scaler.getJSONArray("featureNames").put(1, "unsupported.feature");
        bundle.scaler = bytes(scaler);
        bundle.refreshIntegrity();
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertFalse(result.updated);
        Assert.assertEquals("feature_unsupported", result.reason);
        assertOldModelRemains(store);
        Assert.assertFalse(versionDirectory("model-a", "2.0.0").exists());
    }

    @Test
    public void rejectsInsecureAssignmentUrlBeforeTransport() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(
                "http://example.com/v1/model-assignment",
                ""
        );

        Assert.assertFalse(result.updated);
        Assert.assertEquals("assignment_url_insecure", result.reason);
        Assert.assertEquals(0, httpClient.requestCount);
        Assert.assertNull(store.readCurrent());
    }

    @Test
    public void rejectsArtifactLargerThanConfiguredLimit() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        bundle.weightsSizeBytes = AispectRemoteModelUpdater.MAX_WEIGHTS_BYTES + 1L;
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertFalse(result.updated);
        Assert.assertEquals("weights_size_exceeded", result.reason);
        Assert.assertEquals(1, httpClient.requestCount);
        Assert.assertNull(store.readCurrent());
    }

    @Test
    public void rejectsMalformedAssignmentJson() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        FakeHttpClient httpClient = new FakeHttpClient();
        httpClient.responses.put(ASSIGNMENT_URL, bytes("not-json"));
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.REJECTED, result.state);
        Assert.assertEquals("assignment_json_invalid", result.reason);
        assertOldModelRemains(store);
    }

    @Test
    public void rejectsMalformedModelJsonAfterIntegrityChecks() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        Bundle bundle = validBundle("model-a", "2.0.0");
        bundle.weights = bytes("not-json");
        bundle.refreshIntegrity();
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.REJECTED, result.state);
        Assert.assertEquals("model_json_invalid", result.reason);
        assertOldModelRemains(store);
        Assert.assertFalse(versionDirectory("model-a", "2.0.0").exists());
    }

    @Test
    public void treatsMissingAssignmentAsNoChange() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        FakeHttpClient httpClient = new FakeHttpClient();
        httpClient.failure = new AispectModelHttpException(404);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.NO_CHANGE, result.state);
        Assert.assertEquals("no_assignment", result.reason);
        assertOldModelRemains(store);
    }

    @Test
    public void rejectsAssignmentForDifferentDevice() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        Bundle bundle = validBundle("model-a", "2.0.0");
        FakeHttpClient httpClient = client(bundle);
        JSONObject assignment = bundle.assignmentJson().put("deviceId", "device-b");
        httpClient.responses.put(ASSIGNMENT_URL + "?deviceId=device-a", bytes(assignment));
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "device-a");

        Assert.assertEquals(AispectRemoteModelUpdater.State.REJECTED, result.state);
        Assert.assertEquals("assignment_device_mismatch", result.reason);
        assertOldModelRemains(store);
        Assert.assertEquals(1, httpClient.requestCount);
    }

    @Test
    public void encodesHostDeviceIdAsUtf8QueryValue() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        FakeHttpClient httpClient = client(bundle);
        httpClient.responses.put(
                ASSIGNMENT_URL + "?deviceId=device%2F%C3%A4",
                bytes(bundle.assignmentJson())
        );
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.prepare(ASSIGNMENT_URL, "device/\u00e4");

        Assert.assertEquals(AispectRemoteModelUpdater.State.STAGED, result.state);
        Assert.assertEquals(3, httpClient.requestCount);
    }

    @Test
    public void cancelsInterruptedRefreshBeforeStaging() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        Bundle bundle = validBundle("model-a", "2.0.0");
        FakeHttpClient httpClient = client(bundle);
        httpClient.interruptAfterRequestCount = 3;
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        try {
            updater.prepare(ASSIGNMENT_URL, "");
            Assert.fail("Expected interrupted refresh to stop");
        } catch (java.io.IOException expected) {
            Assert.assertEquals("model update cancelled", expected.getMessage());
        } finally {
            Thread.interrupted();
        }

        assertOldModelRemains(store);
        Assert.assertFalse(versionDirectory("model-a", "2.0.0").exists());
    }

    @Test
    public void rejectsUnsafeModelPathBeforeArtifactDownload() throws Exception {
        AispectDownloadedModelStore store = storeWithActiveModel();
        Bundle bundle = validBundle("../model-a", "2.0.0");
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.REJECTED, result.state);
        Assert.assertEquals("assignment_path_rejected", result.reason);
        Assert.assertEquals(1, httpClient.requestCount);
        assertOldModelRemains(store);
    }

    @Test
    public void repairsCorruptCurrentInsteadOfReturningNoChange() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        store.install(bundle.assignment(), bundle.weights, bundle.scaler);
        java.io.File weightsFile = versionFile("model-a", "1.0.0", "weights.json");
        java.nio.file.Files.write(weightsFile.toPath(), bytes("corrupt"));
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.ACTIVATED, result.state);
        Assert.assertArrayEquals(bundle.weights, java.nio.file.Files.readAllBytes(weightsFile.toPath()));
        Assert.assertEquals(3, httpClient.requestCount);
    }

    @Test
    public void repairsCorruptCurrentPointerInsteadOfReturningNoChange() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle bundle = validBundle("model-a", "1.0.0");
        store.install(bundle.assignment(), bundle.weights, bundle.scaler);
        JSONObject current = store.readCurrent();
        current.put("weightsResourceName", current.getString("scalerResourceName"));
        java.nio.file.Files.write(
                new java.io.File(temporaryFolder.getRoot(), "current.json").toPath(),
                current.toString(2).getBytes(StandardCharsets.UTF_8)
        );
        FakeHttpClient httpClient = client(bundle);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.ACTIVATED, result.state);
        Assert.assertEquals(
                "model-a/1.0.0/weights.json",
                store.readCurrent().getString("weightsResourceName")
        );
        Assert.assertEquals(3, httpClient.requestCount);
    }

    @Test
    public void repairsCorruptPreviousBeforeServerRollback() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle first = validBundle("model-a", "1.0.0");
        Bundle second = validBundle("model-a", "2.0.0");
        store.install(first.assignment(), first.weights, first.scaler);
        store.install(second.assignment(), second.weights, second.scaler);
        java.io.File firstWeights = versionFile("model-a", "1.0.0", "weights.json");
        java.nio.file.Files.write(firstWeights.toPath(), bytes("corrupt"));
        FakeHttpClient httpClient = client(first);
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, httpClient, true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.ACTIVATED, result.state);
        Assert.assertEquals("1.0.0", store.readCurrent().getString("version"));
        Assert.assertEquals("2.0.0", store.readPrevious().getString("version"));
        Assert.assertArrayEquals(first.weights, java.nio.file.Files.readAllBytes(firstWeights.toPath()));
    }

    @Test
    public void repairingCurrentPreservesPreviousVersion() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle first = validBundle("model-a", "1.0.0");
        Bundle second = validBundle("model-a", "2.0.0");
        store.install(first.assignment(), first.weights, first.scaler);
        store.install(second.assignment(), second.weights, second.scaler);
        java.io.File secondWeights = versionFile("model-a", "2.0.0", "weights.json");
        java.nio.file.Files.write(secondWeights.toPath(), bytes("corrupt"));
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(store, client(second), true);

        AispectRemoteModelUpdater.Result result = updater.refresh(ASSIGNMENT_URL, "");

        Assert.assertEquals(AispectRemoteModelUpdater.State.ACTIVATED, result.state);
        Assert.assertEquals("2.0.0", store.readCurrent().getString("version"));
        Assert.assertEquals("1.0.0", store.readPrevious().getString("version"));
    }

    private AispectDownloadedModelStore store() {
        return new AispectDownloadedModelStore(temporaryFolder.getRoot());
    }

    private AispectDownloadedModelStore storeWithActiveModel() throws Exception {
        AispectDownloadedModelStore store = store();
        Bundle oldBundle = validBundle("model-a", "1.0.0");
        store.install(oldBundle.assignment(), oldBundle.weights, oldBundle.scaler);
        return store;
    }

    private void assertOldModelRemains(AispectDownloadedModelStore store) throws Exception {
        Assert.assertEquals("model-a", store.readCurrent().getString("id"));
        Assert.assertEquals("1.0.0", store.readCurrent().getString("version"));
    }

    private java.io.File versionDirectory(String modelId, String version) {
        return new java.io.File(temporaryFolder.getRoot(), modelId + "/" + version);
    }

    private java.io.File versionFile(String modelId, String version, String name) {
        return new java.io.File(versionDirectory(modelId, version), name);
    }

    private static FakeHttpClient client(Bundle bundle) throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        client.responses.put(ASSIGNMENT_URL, bytes(bundle.assignmentJson()));
        client.responses.put(ASSIGNMENT_URL + "?deviceId=device-a", bytes(bundle.assignmentJson()));
        client.responses.put(WEIGHTS_URL, bundle.weights);
        client.responses.put(SCALER_URL, bundle.scaler);
        return client;
    }

    private static Bundle validBundle(String modelId, String version) throws Exception {
        JSONObject scaler = new JSONObject()
                .put("center", repeatedNumbers(0.0, 21))
                .put("scale", repeatedNumbers(1.0, 21))
                .put("featureNames", new JSONArray(AispectCanonicalModelContract.featureNames()))
                .put("frameIndices", new JSONArray(AispectCanonicalModelContract.frameIndices()))
                .put("frameCount", 15)
                .put("windowMode", "release")
                .put("captureDelayMs", 0)
                .put("classCount", 4)
                .put("labelOrder", labels());
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
        return new Bundle(modelId, version, bytes(weights), bytes(scaler));
    }

    private static JSONArray labels() {
        return new JSONArray()
                .put("thumb_light")
                .put("thumb_heavy")
                .put("index_light")
                .put("index_heavy");
    }

    private static JSONArray repeatedNumbers(double value, int count) throws Exception {
        JSONArray values = new JSONArray();
        for (int index = 0; index < count; index++) {
            values.put(value);
        }
        return values;
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

    private static byte[] bytes(JSONObject object) {
        return object.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String repeat(char character, int count) {
        StringBuilder output = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            output.append(character);
        }
        return output.toString();
    }

    private static final class Bundle {
        final String modelId;
        final String version;
        byte[] weights;
        byte[] scaler;
        String weightsSha256;
        String scalerSha256;
        long weightsSizeBytes;
        long scalerSizeBytes;

        Bundle(String modelId, String version, byte[] weights, byte[] scaler) throws Exception {
            this.modelId = modelId;
            this.version = version;
            this.weights = weights;
            this.scaler = scaler;
            refreshIntegrity();
        }

        void refreshIntegrity() throws Exception {
            weightsSha256 = AispectRemoteModelUpdater.sha256Hex(weights);
            scalerSha256 = AispectRemoteModelUpdater.sha256Hex(scaler);
            weightsSizeBytes = weights.length;
            scalerSizeBytes = scaler.length;
        }

        JSONObject assignmentJson() throws Exception {
            return new JSONObject()
                    .put("schemaVersion", 1)
                    .put("assignmentId", "assign-" + version)
                    .put("model", new JSONObject()
                            .put("id", modelId)
                            .put("version", version)
                            .put("displayName", modelId)
                            .put("platform", "android")
                            .put("featureSchemaId", "android-touch-cnn-v1")
                            .put("classCount", 4)
                            .put("labelOrder", labels())
                            .put("weights", new JSONObject()
                                    .put("url", WEIGHTS_URL)
                                    .put("sha256", weightsSha256)
                                    .put("sizeBytes", weightsSizeBytes))
                            .put("scaler", new JSONObject()
                                    .put("url", SCALER_URL)
                                    .put("sha256", scalerSha256)
                                    .put("sizeBytes", scalerSizeBytes)));
        }

        JSONObject canonicalAssignmentJson() throws Exception {
            return new JSONObject()
                    .put("schemaVersion", 1)
                    .put("platform", "android")
                    .put("selectedModelId", modelId)
                    .put("models", new JSONArray().put(new JSONObject()
                            .put("id", modelId)
                            .put("version", version)
                            .put("displayName", modelId)
                            .put("platform", "android")
                            .put("modelType", "cnn_json")
                            .put("minimumSdkVersion", "1.0.0")
                            .put("featureSchemaId", "android-touch-cnn-v1")
                            .put("labelOrder", labels())
                            .put("inputContract", new JSONObject()
                                    .put("featureNames", new JSONArray(AispectCanonicalModelContract.featureNames()))
                                    .put("frameIndices", new JSONArray(AispectCanonicalModelContract.frameIndices()))
                                    .put("windowMode", "release")
                                    .put("captureDelayMs", 0))
                            .put("weightsURL", WEIGHTS_URL)
                            .put("scalerURL", SCALER_URL)
                            .put("weightsSHA256", weightsSha256)
                            .put("scalerSHA256", scalerSha256)
                            .put("weightsSizeBytes", weightsSizeBytes)
                            .put("scalerSizeBytes", scalerSizeBytes)));
        }

        AispectRemoteModelAssignment assignment() throws Exception {
            return AispectRemoteModelAssignment.fromJson(assignmentJson());
        }
    }

    private static final class FakeHttpClient implements AispectModelHttpClient {
        final Map<String, byte[]> responses = new HashMap<>();
        final Map<String, java.io.IOException> failures = new HashMap<>();
        int requestCount;
        java.io.IOException failure;
        int interruptAfterRequestCount;

        @Override
        public byte[] get(String url, int maxBytes) throws java.io.IOException {
            requestCount += 1;
            if (failure != null) {
                throw failure;
            }
            java.io.IOException requestFailure = failures.get(url);
            if (requestFailure != null) {
                throw requestFailure;
            }
            byte[] response = responses.get(url);
            if (response == null) {
                throw new java.io.IOException("missing fake response");
            }
            if (response.length > maxBytes) {
                throw new java.io.IOException("response too large");
            }
            if (requestCount == interruptAfterRequestCount) {
                Thread.currentThread().interrupt();
            }
            return response;
        }
    }
}
