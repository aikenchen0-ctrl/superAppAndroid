package com.zhifaios.eyes.aispect;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

public final class AispectRemoteModelUpdater {
    static final int MAX_ASSIGNMENT_BYTES = 256 * 1024;
    static final int MAX_WEIGHTS_BYTES = 64 * 1024 * 1024;
    static final int MAX_SCALER_BYTES = 4 * 1024 * 1024;

    public enum State {
        NO_CHANGE,
        STAGED,
        ACTIVATED,
        DEFERRED,
        REJECTED
    }

    public static final class Result {
        public final State state;
        public final boolean updated;
        public final String modelId;
        public final String version;
        public final String activeModelId;
        public final String activeModelVersion;
        public final String reason;

        Result(
                State state,
                String modelId,
                String version,
                String activeModelId,
                String activeModelVersion,
                String reason
        ) {
            this.state = state == null ? State.REJECTED : state;
            this.updated = this.state == State.ACTIVATED;
            this.modelId = safe(modelId);
            this.version = safe(version);
            this.activeModelId = safe(activeModelId);
            this.activeModelVersion = safe(activeModelVersion);
            this.reason = safe(reason);
        }

        Result deferred() {
            return new Result(
                    State.DEFERRED,
                    modelId,
                    version,
                    activeModelId,
                    activeModelVersion,
                    "touch_active"
            );
        }
    }

    private final AispectImpactCNNClassifier classifier;
    private final AispectDownloadedModelStore store;
    private final AispectModelHttpClient httpClient;
    private final boolean requireHttps;
    private final Set<String> allowedHosts;

    public AispectRemoteModelUpdater(AispectImpactCNNClassifier classifier) {
        this(
                classifier,
                classifier == null ? null : classifier.downloadedModelStore(),
                new AispectUrlConnectionModelHttpClient(),
                true
        );
    }

    public AispectRemoteModelUpdater(AispectImpactCNNClassifier classifier, boolean requireHttps) {
        this(classifier, requireHttps, new String[0]);
    }

    public AispectRemoteModelUpdater(AispectImpactCNNClassifier classifier, boolean requireHttps, String[] allowedHosts) {
        this(
                classifier,
                classifier == null ? null : classifier.downloadedModelStore(),
                new AispectUrlConnectionModelHttpClient(),
                requireHttps,
                allowedHosts
        );
    }

    AispectRemoteModelUpdater(
            AispectDownloadedModelStore store,
            AispectModelHttpClient httpClient,
            boolean requireHttps
    ) {
        this(null, store, httpClient, requireHttps);
    }

    AispectRemoteModelUpdater(
            AispectImpactCNNClassifier classifier,
            AispectDownloadedModelStore store,
            AispectModelHttpClient httpClient,
            boolean requireHttps
    ) {
        this(classifier, store, httpClient, requireHttps, new String[0]);
    }

    AispectRemoteModelUpdater(
            AispectImpactCNNClassifier classifier,
            AispectDownloadedModelStore store,
            AispectModelHttpClient httpClient,
            boolean requireHttps,
            String[] allowedHosts
    ) {
        this.classifier = classifier;
        this.store = store;
        this.httpClient = httpClient;
        this.requireHttps = requireHttps;
        this.allowedHosts = new HashSet<>();
        if (allowedHosts != null) {
            for (String host : allowedHosts) {
                if (host != null && !host.trim().isEmpty()) {
                    this.allowedHosts.add(host.trim().toLowerCase(Locale.US));
                }
            }
        }
    }

    public Result refresh(String assignmentUrl, String deviceId) throws IOException, JSONException {
        Result prepared = prepare(assignmentUrl, deviceId);
        if (prepared.state != State.STAGED) {
            return prepared;
        }
        return activate(prepared.modelId, prepared.version);
    }

    public Result refreshCanonical(String assignmentUrl, String sdkVersion) throws IOException, JSONException {
        Result prepared = prepareCanonical(assignmentUrl, sdkVersion);
        if (prepared.state != State.STAGED) {
            return prepared;
        }
        return activate(prepared.modelId, prepared.version);
    }

    Result prepare(String assignmentUrl, String deviceId) throws IOException, JSONException {
        return prepareInternal(assignmentUrl, deviceId, false);
    }

    Result prepareCanonical(String assignmentUrl, String sdkVersion) throws IOException, JSONException {
        return prepareInternal(assignmentUrl, sdkVersion, true);
    }

    private Result prepareInternal(
            String assignmentUrl,
            String identity,
            boolean canonical
    ) throws IOException, JSONException {
        throwIfCancelled();
        if (store == null || httpClient == null) {
            return result(State.REJECTED, "", "", "classifier_missing");
        }
        if (assignmentUrl == null || assignmentUrl.isEmpty()) {
            return result(State.REJECTED, "", "", "assignment_url_empty");
        }
        String resolvedAssignmentUrl = canonical ? assignmentUrl : appendDeviceId(assignmentUrl, identity);
        String assignmentUrlError = endpointError(resolvedAssignmentUrl, "assignment");
        if (!assignmentUrlError.isEmpty()) {
            return result(State.REJECTED, "", "", assignmentUrlError);
        }

        byte[] assignmentBytes;
        try {
            assignmentBytes = httpClient.get(resolvedAssignmentUrl, MAX_ASSIGNMENT_BYTES);
        } catch (AispectModelHttpException error) {
            if (error.statusCode == 404) {
                return result(State.NO_CHANGE, "", "", "no_assignment");
            }
            if (error.statusCode == 409) {
                return result(State.REJECTED, "", "", "server_artifact_integrity");
            }
            throw error;
        }
        throwIfCancelled();
        if (assignmentBytes.length > MAX_ASSIGNMENT_BYTES) {
            return result(State.REJECTED, "", "", "assignment_size_exceeded");
        }
        AispectRemoteModelAssignment assignment;
        try {
            JSONObject assignmentJson = new JSONObject(new String(assignmentBytes, StandardCharsets.UTF_8));
            if (canonical) {
                AispectCanonicalModelAssignment.Result parsed = AispectCanonicalModelAssignment.parse(
                        assignmentJson,
                        identity
                );
                if (!parsed.accepted) {
                    return result(State.REJECTED, "", "", parsed.reason);
                }
                assignment = parsed.assignment;
            } else {
                assignment = AispectRemoteModelAssignment.fromJson(assignmentJson);
            }
        } catch (JSONException error) {
            return result(State.REJECTED, "", "", "assignment_json_invalid");
        }
        if (!assignment.isUsable()) {
            return result(State.REJECTED, assignment.modelId, assignment.version, "assignment_rejected");
        }
        if (!AispectDownloadedModelStore.isSafePathSegment(assignment.modelId)
                || !AispectDownloadedModelStore.isSafePathSegment(assignment.version)) {
            return result(State.REJECTED, assignment.modelId, assignment.version, "assignment_path_rejected");
        }
        if (!canonical
                && !assignment.deviceId.isEmpty()
                && identity != null
                && !identity.isEmpty()
                && !assignment.deviceId.equals(identity)) {
            return result(State.REJECTED, assignment.modelId, assignment.version, "assignment_device_mismatch");
        }
        JSONObject current = store.readCurrent();
        if (isSameVersion(current, assignment) && store.isCurrentVersionUsable(assignment)) {
            return result(State.NO_CHANGE, assignment.modelId, assignment.version, "no_change");
        }

        String weightsUrlError = endpointError(assignment.weightsUrl, "weights");
        if (!weightsUrlError.isEmpty()) {
            return result(State.REJECTED, assignment.modelId, assignment.version, weightsUrlError);
        }
        String scalerUrlError = endpointError(assignment.scalerUrl, "scaler");
        if (!scalerUrlError.isEmpty()) {
            return result(State.REJECTED, assignment.modelId, assignment.version, scalerUrlError);
        }
        if (assignment.weightsSizeBytes > MAX_WEIGHTS_BYTES) {
            return result(State.REJECTED, assignment.modelId, assignment.version, "weights_size_exceeded");
        }
        if (assignment.scalerSizeBytes > MAX_SCALER_BYTES) {
            return result(State.REJECTED, assignment.modelId, assignment.version, "scaler_size_exceeded");
        }

        byte[] weights;
        try {
            weights = httpClient.get(assignment.weightsUrl, MAX_WEIGHTS_BYTES);
        } catch (AispectModelHttpException error) {
            if (error.statusCode == 409) {
                return result(State.REJECTED, assignment.modelId, assignment.version, "server_artifact_integrity");
            }
            throw error;
        }
        throwIfCancelled();
        if (hasSizeMismatch(assignment.schemaVersion, assignment.weightsSizeBytes, weights.length)) {
            return result(State.REJECTED, assignment.modelId, assignment.version, "weights_size_mismatch");
        }
        if (!sha256Hex(weights).equalsIgnoreCase(assignment.weightsSha256)) {
            return result(State.REJECTED, assignment.modelId, assignment.version, "weights_sha256_mismatch");
        }
        byte[] scaler;
        try {
            scaler = httpClient.get(assignment.scalerUrl, MAX_SCALER_BYTES);
        } catch (AispectModelHttpException error) {
            if (error.statusCode == 409) {
                return result(State.REJECTED, assignment.modelId, assignment.version, "server_artifact_integrity");
            }
            throw error;
        }
        throwIfCancelled();
        if (hasSizeMismatch(assignment.schemaVersion, assignment.scalerSizeBytes, scaler.length)) {
            return result(State.REJECTED, assignment.modelId, assignment.version, "scaler_size_mismatch");
        }
        if (!sha256Hex(scaler).equalsIgnoreCase(assignment.scalerSha256)) {
            return result(State.REJECTED, assignment.modelId, assignment.version, "scaler_sha256_mismatch");
        }

        throwIfCancelled();
        store.stage(assignment, weights, scaler);
        JSONObject weightsJson;
        JSONObject scalerJson;
        try {
            weightsJson = new JSONObject(new String(weights, StandardCharsets.UTF_8));
            scalerJson = new JSONObject(new String(scaler, StandardCharsets.UTF_8));
        } catch (JSONException | RuntimeException error) {
            store.removeStaged(assignment.modelId, assignment.version);
            return result(State.REJECTED, assignment.modelId, assignment.version, "model_json_invalid");
        }
        AispectModelBundleValidator.Result validation = AispectModelBundleValidator.validate(
                assignment,
                weightsJson,
                scalerJson
        );
        if (!validation.valid) {
            store.removeStaged(assignment.modelId, assignment.version);
            return result(State.REJECTED, assignment.modelId, assignment.version, validation.reason);
        }
        return result(State.STAGED, assignment.modelId, assignment.version, "staged");
    }

    Result activate(String modelId, String version) throws IOException, JSONException {
        throwIfCancelled();
        if (store == null) {
            return result(State.REJECTED, modelId, version, "classifier_missing");
        }
        store.activate(modelId, version);
        if (classifier != null) {
            classifier.reloadModelCatalog();
            if (classifier.modelInfoForId(modelId) == null || !classifier.isModelLoadable(modelId)) {
                try {
                    store.rollbackActivation(modelId, version);
                } finally {
                    classifier.reloadModelCatalog();
                }
                return result(State.REJECTED, modelId, version, "model_load_rejected");
            }
        }
        return result(State.ACTIVATED, modelId, version, "updated");
    }

    static String sha256Hex(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("sha256 unavailable", error);
        }
    }

    private String endpointError(String endpoint, String artifactName) {
        try {
            URL url = new URL(endpoint);
            String protocol = url.getProtocol();
            if (!"https".equalsIgnoreCase(protocol) && requireHttps) {
                return artifactName + "_url_insecure";
            }
            if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
                return artifactName + "_url_invalid";
            }
            if (!allowedHosts.isEmpty() && !allowedHosts.contains(url.getHost().toLowerCase(Locale.US))) {
                return artifactName + "_host_rejected";
            }
            return "";
        } catch (MalformedURLException error) {
            return artifactName + "_url_invalid";
        }
    }

    private Result result(State state, String modelId, String version, String reason) {
        JSONObject current = store == null ? null : store.readCurrent();
        return new Result(
                state,
                modelId,
                version,
                value(current, "id"),
                value(current, "version"),
                reason
        );
    }

    private static boolean hasSizeMismatch(int schemaVersion, long declaredSize, int actualSize) {
        return schemaVersion > 0 && declaredSize != actualSize;
    }

    private static boolean isSameVersion(JSONObject current, AispectRemoteModelAssignment assignment) {
        return current != null
                && assignment.modelId.equals(current.optString("id", ""))
                && assignment.version.equals(current.optString("version", ""));
    }

    private static String appendDeviceId(String assignmentUrl, String deviceId) {
        if (deviceId == null || deviceId.isEmpty() || assignmentUrl.contains("deviceId=")) {
            return assignmentUrl;
        }
        String separator = assignmentUrl.contains("?") ? "&" : "?";
        return assignmentUrl + separator + "deviceId=" + urlEncode(deviceId);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException error) {
            return "";
        }
    }

    private static String value(JSONObject object, String key) {
        return object == null ? "" : object.optString(key, "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void throwIfCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("model update cancelled");
        }
    }
}
