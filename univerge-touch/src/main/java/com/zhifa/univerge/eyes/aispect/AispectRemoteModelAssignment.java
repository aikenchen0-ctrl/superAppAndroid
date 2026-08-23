package com.zhifa.univerge.eyes.aispect;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AispectRemoteModelAssignment {
    private static final String PLATFORM_ANDROID = "android";
    private static final String FEATURE_SCHEMA_ANDROID_V1 = "android-touch-cnn-v1";
    static final String FEATURE_SCHEMA_TIME_GRID_GROUPNORM_V1 = "aispect-time-grid-groupnorm-v1";
    static final String MODEL_TYPE_TIME_GRID_GROUPNORM_V1 = "time_grid_groupnorm_cnn_v1";

    public final int schemaVersion;
    public final String assignmentId;
    public final String deviceId;
    public final String modelId;
    public final String version;
    public final String displayName;
    public final String platform;
    public final String featureSchemaId;
    public final String modelType;
    public final String minimumSdkVersion;
    public final int classCount;
    public final String[] labelOrder;
    public final String[] inputFeatureNames;
    public final int[] inputFrameIndices;
    public final String inputWindowMode;
    public final int inputCaptureDelayMs;
    public final String weightsUrl;
    public final String scalerUrl;
    public final String weightsSha256;
    public final String scalerSha256;
    public final long weightsSizeBytes;
    public final long scalerSizeBytes;
    public final long expiresAtMillis;

    private AispectRemoteModelAssignment(
            int schemaVersion,
            String assignmentId,
            String deviceId,
            String modelId,
            String version,
            String displayName,
            String platform,
            String featureSchemaId,
            String modelType,
            String minimumSdkVersion,
            int classCount,
            String[] labelOrder,
            String[] inputFeatureNames,
            int[] inputFrameIndices,
            String inputWindowMode,
            int inputCaptureDelayMs,
            String weightsUrl,
            String scalerUrl,
            String weightsSha256,
            String scalerSha256,
            long weightsSizeBytes,
            long scalerSizeBytes,
            long expiresAtMillis
    ) {
        this.schemaVersion = schemaVersion;
        this.assignmentId = safe(assignmentId);
        this.deviceId = safe(deviceId);
        this.modelId = safe(modelId);
        this.version = safe(version);
        this.displayName = safe(displayName);
        this.platform = safe(platform);
        this.featureSchemaId = safe(featureSchemaId);
        this.modelType = safe(modelType);
        this.minimumSdkVersion = safe(minimumSdkVersion);
        this.classCount = classCount;
        this.labelOrder = labelOrder == null ? new String[0] : labelOrder.clone();
        this.inputFeatureNames = inputFeatureNames == null ? new String[0] : inputFeatureNames.clone();
        this.inputFrameIndices = inputFrameIndices == null ? new int[0] : inputFrameIndices.clone();
        this.inputWindowMode = safe(inputWindowMode);
        this.inputCaptureDelayMs = inputCaptureDelayMs;
        this.weightsUrl = safe(weightsUrl);
        this.scalerUrl = safe(scalerUrl);
        this.weightsSha256 = safe(weightsSha256);
        this.scalerSha256 = safe(scalerSha256);
        this.weightsSizeBytes = weightsSizeBytes;
        this.scalerSizeBytes = scalerSizeBytes;
        this.expiresAtMillis = expiresAtMillis;
    }

    public static AispectRemoteModelAssignment fromJson(JSONObject root) throws JSONException {
        JSONObject model = root.optJSONObject("model");
        if (model == null) {
            model = root;
        }
        int schemaVersion = root.optInt("schemaVersion", 0);
        JSONObject weights = model.optJSONObject("weights");
        JSONObject scaler = model.optJSONObject("scaler");
        return new AispectRemoteModelAssignment(
                schemaVersion,
                root.optString("assignmentId", ""),
                root.optString("deviceId", ""),
                first(model, "id", "modelId"),
                model.optString("version", ""),
                model.optString("displayName", first(model, "id", "modelId")),
                model.optString("platform", ""),
                model.optString("featureSchemaId", ""),
                model.optString("modelType", ""),
                model.optString("minimumSdkVersion", ""),
                model.optInt("classCount", 0),
                stringArray(model.optJSONArray("labelOrder")),
                new String[0],
                new int[0],
                "",
                -1,
                artifactValue(weights, "url", first(model, "weightsUrl", "weightsURL")),
                artifactValue(scaler, "url", first(model, "scalerUrl", "scalerURL")),
                artifactValue(weights, "sha256", first(model, "weightsSha256", "weightsSHA256")),
                artifactValue(scaler, "sha256", first(model, "scalerSha256", "scalerSHA256")),
                artifactSize(weights),
                artifactSize(scaler),
                expiresAtMillis(root)
        );
    }

    static AispectRemoteModelAssignment fromCanonicalModel(
            JSONObject root,
            JSONObject model,
            String[] inputFeatureNames,
            int[] inputFrameIndices,
            String inputWindowMode,
            int inputCaptureDelayMs
    ) {
        String modelId = first(model, "id", "modelId");
        String version = model.optString("version", "");
        String assignmentId = root.optString("assignmentId", "");
        if (assignmentId.isEmpty()) {
            assignmentId = modelId + "@" + version;
        }
        return new AispectRemoteModelAssignment(
                root.optInt("schemaVersion", 0),
                assignmentId,
                root.optString("deviceId", ""),
                modelId,
                version,
                model.optString("displayName", modelId),
                model.optString("platform", root.optString("platform", "")),
                model.optString("featureSchemaId", ""),
                model.optString("modelType", ""),
                model.optString("minimumSdkVersion", ""),
                model.optJSONArray("labelOrder") == null ? 0 : model.optJSONArray("labelOrder").length(),
                stringArray(model.optJSONArray("labelOrder")),
                inputFeatureNames,
                inputFrameIndices,
                inputWindowMode,
                inputCaptureDelayMs,
                first(model, "weightsURL", "weightsUrl"),
                first(model, "scalerURL", "scalerUrl"),
                first(model, "weightsSHA256", "weightsSha256"),
                first(model, "scalerSHA256", "scalerSha256"),
                Math.max(0L, model.optLong("weightsSizeBytes", 0L)),
                Math.max(0L, model.optLong("scalerSizeBytes", 0L)),
                0L
        );
    }

    public boolean hasRequiredIntegrity() {
        return isSha256(weightsSha256) && isSha256(scalerSha256);
    }

    public boolean isUsable() {
        return !modelId.isEmpty()
                && !version.isEmpty()
                && !weightsUrl.isEmpty()
                && !scalerUrl.isEmpty()
                && hasRequiredIntegrity()
                && hasSupportedSchemaVersion()
                && hasCompatiblePlatform()
                && hasCompatibleFeatureSchema()
                && hasCompatibleLabels()
                && hasDeclaredV1Sizes()
                && !isExpired();
    }

    public boolean isExpired() {
        return expiresAtMillis > 0 && System.currentTimeMillis() > expiresAtMillis;
    }

    private static long expiresAtMillis(JSONObject root) {
        double seconds = root.optDouble("expiresAt", 0);
        if (seconds <= 0) {
            return 0L;
        }
        return Math.round(seconds * 1000.0);
    }

    private static String first(JSONObject object, String firstKey, String secondKey) {
        String value = object.optString(firstKey, "");
        if (value == null || value.isEmpty()) {
            return object.optString(secondKey, "");
        }
        return value;
    }

    private boolean hasSupportedSchemaVersion() {
        return schemaVersion == 0 || schemaVersion == 1;
    }

    private boolean hasCompatiblePlatform() {
        return platform.isEmpty() ? schemaVersion == 0 : PLATFORM_ANDROID.equals(platform);
    }

    private boolean hasCompatibleFeatureSchema() {
        return featureSchemaId.isEmpty()
                ? schemaVersion == 0
                : FEATURE_SCHEMA_ANDROID_V1.equals(featureSchemaId)
                || FEATURE_SCHEMA_TIME_GRID_GROUPNORM_V1.equals(featureSchemaId);
    }

    private boolean hasCompatibleLabels() {
        if (labelOrder.length == 0 && classCount == 0) {
            return schemaVersion == 0;
        }
        return classCount == labelOrder.length && AispectCanonicalModelContract.matchesLabels(labelOrder);
    }

    private boolean hasDeclaredV1Sizes() {
        return schemaVersion == 0 || (weightsSizeBytes > 0 && scalerSizeBytes > 0);
    }

    private static String artifactValue(JSONObject artifact, String key, String fallback) {
        if (artifact == null) {
            return safe(fallback);
        }
        return safe(artifact.optString(key, fallback));
    }

    private static long artifactSize(JSONObject artifact) {
        return artifact == null ? 0L : Math.max(0L, artifact.optLong("sizeBytes", 0L));
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

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
