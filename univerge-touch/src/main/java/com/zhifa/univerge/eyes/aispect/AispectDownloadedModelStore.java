package com.zhifa.univerge.eyes.aispect;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

final class AispectDownloadedModelStore {
    private static final String CURRENT_FILE = "current.json";
    private static final String PREVIOUS_FILE = "previous.json";
    private static final String ACTIVATION_FILE = "activation.json";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String WEIGHTS_FILE = "weights.json";
    private static final String SCALER_FILE = "scaler.json";

    private final File rootDirectory;
    private final File currentFile;
    private final File previousFile;
    private final File activationFile;

    AispectDownloadedModelStore(File rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.currentFile = new File(rootDirectory, CURRENT_FILE);
        this.previousFile = new File(rootDirectory, PREVIOUS_FILE);
        this.activationFile = new File(rootDirectory, ACTIVATION_FILE);
    }

    JSONArray readCatalog() {
        JSONObject current = readCurrent();
        if (current == null || !hasVerifiedPointerResources(current)) {
            return new JSONArray();
        }
        return new JSONArray().put(current);
    }

    JSONObject readCurrent() {
        recoverActivationQuietly();
        return readPointer(currentFile);
    }

    JSONObject readPrevious() {
        recoverActivationQuietly();
        return readPointer(previousFile);
    }

    boolean isCurrentVersionUsable(AispectRemoteModelAssignment assignment) {
        if (assignment == null) {
            return false;
        }
        JSONObject current = readCurrent();
        if (!matchesAssignmentMetadata(current, assignment)) {
            return false;
        }
        try {
            return matchesImmutableVersion(
                    versionDirectory(assignment.modelId, assignment.version),
                    assignment
            );
        } catch (IOException error) {
            return false;
        }
    }

    void stage(
            AispectRemoteModelAssignment assignment,
            byte[] weightsBytes,
            byte[] scalerBytes
    ) throws IOException, JSONException {
        if (assignment == null || !assignment.isUsable() || weightsBytes == null || scalerBytes == null) {
            throw new IOException("remote model rejected");
        }
        validatePathSegment(assignment.modelId);
        validatePathSegment(assignment.version);
        ensureDirectory(rootDirectory);

        File modelDirectory = childDirectory(rootDirectory, assignment.modelId);
        ensureDirectory(modelDirectory);
        File versionDirectory = childDirectory(modelDirectory, assignment.version);
        if (versionDirectory.exists()) {
            boolean protectedVersion = pointsTo(readCurrent(), assignment.modelId, assignment.version)
                    || pointsTo(readPrevious(), assignment.modelId, assignment.version);
            if (protectedVersion && !matchesImmutableMetadata(versionDirectory, assignment)) {
                throw new IOException("remote model version conflict");
            }
            if (matchesImmutableVersion(versionDirectory, assignment)) {
                return;
            }
        }

        File stagingDirectory = childDirectory(
                modelDirectory,
                "." + assignment.version + ".staging-" + UUID.randomUUID()
        );
        ensureDirectory(stagingDirectory);
        boolean installed = false;
        try {
            writeBytesSynced(new File(stagingDirectory, WEIGHTS_FILE), weightsBytes);
            writeBytesSynced(new File(stagingDirectory, SCALER_FILE), scalerBytes);
            JSONObject manifest = manifestFor(assignment);
            writeBytesSynced(
                    new File(stagingDirectory, MANIFEST_FILE),
                    manifest.toString(2).getBytes(StandardCharsets.UTF_8)
            );
            if (versionDirectory.exists()) {
                replaceDirectory(stagingDirectory, versionDirectory);
            } else {
                moveDirectory(stagingDirectory, versionDirectory);
            }
            installed = true;
        } finally {
            if (!installed && stagingDirectory.exists()) {
                deleteDirectoryQuietly(stagingDirectory);
            }
        }
    }

    void activate(String modelId, String version) throws IOException, JSONException {
        recoverActivationIfNeeded();
        validatePathSegment(modelId);
        validatePathSegment(version);
        File versionDirectory = versionDirectory(modelId, version);
        JSONObject manifest = readManifest(versionDirectory);
        if (!modelId.equals(manifest.optString("id", ""))
                || !version.equals(manifest.optString("version", ""))
                || !hasVerifiedManifestResources(manifest)) {
            throw new IOException("staged model incomplete");
        }

        JSONObject next = new JSONObject(manifest.toString());
        next.put("activatedAt", System.currentTimeMillis() / 1000.0);
        JSONObject current = readPointer(currentFile);
        JSONObject previous = readPointer(previousFile);
        boolean rotatePrevious = !pointsTo(current, modelId, version);
        ensureDirectory(rootDirectory);
        writeActivationJournal(current, previous, modelId, version, rotatePrevious);
        try {
            if (rotatePrevious) {
                writePointer(previousFile, current);
            }
            writeJsonAtomically(currentFile, next);
        } catch (IOException | JSONException error) {
            try {
                recoverActivationIfNeeded();
            } catch (IOException | JSONException recoveryError) {
                error.addSuppressed(recoveryError);
            }
            throw error;
        }
        deleteQuietly(activationFile);
        if (rotatePrevious) {
            removeUnreferencedVersion(previous, next, current);
        }
    }

    void install(
            AispectRemoteModelAssignment assignment,
            byte[] weightsBytes,
            byte[] scalerBytes
    ) throws IOException, JSONException {
        stage(assignment, weightsBytes, scalerBytes);
        activate(assignment.modelId, assignment.version);
    }

    void removeStaged(String modelId, String version) throws IOException {
        validatePathSegment(modelId);
        validatePathSegment(version);
        if (pointsTo(readCurrent(), modelId, version) || pointsTo(readPrevious(), modelId, version)) {
            throw new IOException("remote model version protected");
        }
        File directory = versionDirectory(modelId, version);
        if (directory.exists()) {
            deleteDirectory(directory);
        }
        deleteEmptyParent(directory.getParentFile());
    }

    void rollbackActivation(String modelId, String version) throws IOException, JSONException {
        recoverActivationIfNeeded();
        validatePathSegment(modelId);
        validatePathSegment(version);
        JSONObject current = readPointer(currentFile);
        if (!pointsTo(current, modelId, version)) {
            throw new IOException("active model mismatch");
        }
        JSONObject previous = readPointer(previousFile);
        if (previous != null && hasVerifiedPointerResources(previous)) {
            writeJsonAtomically(currentFile, previous);
        } else {
            deleteQuietly(currentFile);
        }
        deleteQuietly(previousFile);
        File rejectedDirectory = versionDirectory(modelId, version);
        if (rejectedDirectory.exists()) {
            deleteDirectoryQuietly(rejectedDirectory);
        }
        deleteEmptyParent(rejectedDirectory.getParentFile());
    }

    void remove(String modelId) {
        if (!isSafePathSegment(modelId)) {
            return;
        }
        JSONObject current = readCurrent();
        JSONObject previous = readPrevious();
        try {
            if (pointsTo(current, modelId, value(current, "version"))) {
                deleteQuietly(currentFile);
            }
            if (pointsTo(previous, modelId, value(previous, "version"))) {
                deleteQuietly(previousFile);
            }
            File modelDirectory = childDirectory(rootDirectory, modelId);
            if (modelDirectory.exists()) {
                deleteDirectory(modelDirectory);
            }
        } catch (IOException ignored) {
            // 删除失败时保留其余状态，内置模型仍可兜底。
        }
    }

    String readResourceText(String resourceName) throws IOException {
        File file = resourceFile(resourceName);
        if (!file.isFile()) {
            throw new IOException("downloaded model resource missing");
        }
        return readText(file);
    }

    boolean hasResource(String resourceName) {
        try {
            return resourceFile(resourceName).isFile();
        } catch (IOException error) {
            return false;
        }
    }

    private JSONObject manifestFor(AispectRemoteModelAssignment assignment) throws JSONException {
        String directory = assignment.modelId + "/" + assignment.version + "/";
        JSONObject manifest = new JSONObject();
        manifest.put("schemaVersion", assignment.schemaVersion);
        manifest.put("assignmentId", assignment.assignmentId);
        manifest.put("id", assignment.modelId);
        manifest.put("version", assignment.version);
        manifest.put("displayName", assignment.displayName.isEmpty() ? assignment.modelId : assignment.displayName);
        manifest.put("platform", assignment.platform);
        manifest.put("featureSchemaId", assignment.featureSchemaId);
        manifest.put("classCount", assignment.classCount);
        manifest.put("labelOrder", new JSONArray(assignment.labelOrder));
        manifest.put("weightsSha256", assignment.weightsSha256);
        manifest.put("scalerSha256", assignment.scalerSha256);
        manifest.put("weightsSizeBytes", assignment.weightsSizeBytes);
        manifest.put("scalerSizeBytes", assignment.scalerSizeBytes);
        manifest.put("weightsResourceName", directory + WEIGHTS_FILE);
        manifest.put("scalerResourceName", directory + SCALER_FILE);
        manifest.put("source", "downloaded");
        manifest.put("stagedAt", System.currentTimeMillis() / 1000.0);
        return manifest;
    }

    private JSONObject readManifest(File versionDirectory) throws IOException, JSONException {
        File manifestFile = new File(versionDirectory, MANIFEST_FILE);
        if (!versionDirectory.isDirectory() || !manifestFile.isFile() || !isInsideRoot(manifestFile)) {
            throw new IOException("staged model missing");
        }
        return new JSONObject(readText(manifestFile));
    }

    private boolean matchesImmutableVersion(
            File versionDirectory,
            AispectRemoteModelAssignment assignment
    ) {
        return matchesImmutableMetadata(versionDirectory, assignment)
                && hasVerifiedResources(versionDirectory, assignment);
    }

    private boolean matchesImmutableMetadata(
            File versionDirectory,
            AispectRemoteModelAssignment assignment
    ) {
        try {
            JSONObject manifest = readManifest(versionDirectory);
            return matchesAssignmentMetadata(manifest, assignment);
        } catch (IOException | JSONException error) {
            return false;
        }
    }

    private boolean matchesAssignmentMetadata(
            JSONObject metadata,
            AispectRemoteModelAssignment assignment
    ) {
        if (metadata == null || assignment == null) {
            return false;
        }
        String directory = assignment.modelId + "/" + assignment.version + "/";
        if (assignment.schemaVersion != metadata.optInt("schemaVersion", -1)
                || !assignment.modelId.equals(metadata.optString("id", ""))
                || !assignment.version.equals(metadata.optString("version", ""))
                || !assignment.platform.equals(metadata.optString("platform", ""))
                || !assignment.featureSchemaId.equals(metadata.optString("featureSchemaId", ""))
                || assignment.classCount != metadata.optInt("classCount", -1)
                || !assignment.weightsSha256.equalsIgnoreCase(metadata.optString("weightsSha256", ""))
                || !assignment.scalerSha256.equalsIgnoreCase(metadata.optString("scalerSha256", ""))
                || assignment.weightsSizeBytes != metadata.optLong("weightsSizeBytes", -1L)
                || assignment.scalerSizeBytes != metadata.optLong("scalerSizeBytes", -1L)
                || !(directory + WEIGHTS_FILE).equals(metadata.optString("weightsResourceName", ""))
                || !(directory + SCALER_FILE).equals(metadata.optString("scalerResourceName", ""))
                || !"downloaded".equals(metadata.optString("source", ""))) {
            return false;
        }
        JSONArray labels = metadata.optJSONArray("labelOrder");
        if (labels == null || labels.length() != assignment.labelOrder.length) {
            return false;
        }
        for (int index = 0; index < assignment.labelOrder.length; index++) {
            if (!assignment.labelOrder[index].equals(labels.optString(index, ""))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasVerifiedResources(
            File versionDirectory,
            AispectRemoteModelAssignment assignment
    ) {
        return hasArtifactIntegrity(
                new File(versionDirectory, WEIGHTS_FILE),
                assignment.weightsSizeBytes,
                assignment.weightsSha256
        ) && hasArtifactIntegrity(
                new File(versionDirectory, SCALER_FILE),
                assignment.scalerSizeBytes,
                assignment.scalerSha256
        );
    }

    private boolean hasArtifactIntegrity(File file, long declaredSize, String declaredSha256) {
        if (!file.isFile() || !isInsideRoot(file)) {
            return false;
        }
        if (declaredSize > 0L && file.length() != declaredSize) {
            return false;
        }
        try {
            return sha256Hex(file).equalsIgnoreCase(declaredSha256);
        } catch (IOException error) {
            return false;
        }
    }

    private JSONObject readPointer(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            return new JSONObject(readText(file));
        } catch (IOException | JSONException error) {
            return null;
        }
    }

    private void writeActivationJournal(
            JSONObject originalCurrent,
            JSONObject originalPrevious,
            String targetModelId,
            String targetVersion,
            boolean rotatePrevious
    ) throws IOException, JSONException {
        JSONObject journal = new JSONObject();
        journal.put("targetModelId", targetModelId);
        journal.put("targetVersion", targetVersion);
        journal.put("rotatePrevious", rotatePrevious);
        journal.put("hadCurrent", originalCurrent != null);
        journal.put("hadPrevious", originalPrevious != null);
        journal.put("originalCurrent", originalCurrent == null ? JSONObject.NULL : originalCurrent);
        journal.put("originalPrevious", originalPrevious == null ? JSONObject.NULL : originalPrevious);
        writeJsonAtomically(activationFile, journal);
    }

    private void recoverActivationQuietly() {
        try {
            recoverActivationIfNeeded();
        } catch (IOException | JSONException ignored) {
            // 恢复失败时保留事务日志，下一次访问继续尝试。
        }
    }

    private void recoverActivationIfNeeded() throws IOException, JSONException {
        if (!activationFile.isFile()) {
            return;
        }
        JSONObject journal = readPointer(activationFile);
        if (journal == null) {
            deleteFile(activationFile);
            return;
        }
        JSONObject originalCurrent = journal.optJSONObject("originalCurrent");
        JSONObject originalPrevious = journal.optJSONObject("originalPrevious");
        boolean hadCurrent = journal.has("hadCurrent")
                ? journal.optBoolean("hadCurrent", false)
                : originalCurrent != null;
        boolean hadPrevious = journal.has("hadPrevious")
                ? journal.optBoolean("hadPrevious", false)
                : originalPrevious != null;
        boolean rotatePrevious = journal.optBoolean("rotatePrevious", true);
        String targetModelId = journal.optString("targetModelId", "");
        String targetVersion = journal.optString("targetVersion", "");
        JSONObject current = readPointer(currentFile);
        JSONObject previous = readPointer(previousFile);
        boolean previousCommitted = rotatePrevious
                ? pointersMatch(previous, originalCurrent, hadCurrent)
                : pointersMatch(previous, originalPrevious, hadPrevious);
        if (pointsTo(current, targetModelId, targetVersion) && previousCommitted) {
            deleteFile(activationFile);
            return;
        }
        writePointer(currentFile, hadCurrent ? originalCurrent : null);
        writePointer(previousFile, hadPrevious ? originalPrevious : null);
        deleteFile(activationFile);
    }

    private static boolean pointersMatch(
            JSONObject actual,
            JSONObject expected,
            boolean expectedPresent
    ) {
        if (!expectedPresent) {
            return actual == null;
        }
        return expected != null && pointsTo(
                actual,
                expected.optString("id", ""),
                expected.optString("version", "")
        );
    }

    private static void writePointer(File file, JSONObject pointer) throws IOException, JSONException {
        if (pointer == null) {
            deleteFile(file);
        } else {
            writeJsonAtomically(file, pointer);
        }
    }

    private boolean hasVerifiedPointerResources(JSONObject pointer) {
        if (pointer == null) {
            return false;
        }
        String modelId = pointer.optString("id", "");
        String version = pointer.optString("version", "");
        if (!isSafePathSegment(modelId) || !isSafePathSegment(version)) {
            return false;
        }
        try {
            JSONObject manifest = readManifest(versionDirectory(modelId, version));
            return matchesStoredMetadata(pointer, manifest)
                    && hasVerifiedManifestResources(manifest);
        } catch (IOException | JSONException error) {
            return false;
        }
    }

    private boolean hasVerifiedManifestResources(JSONObject manifest) {
        if (manifest == null) {
            return false;
        }
        String modelId = manifest.optString("id", "");
        String version = manifest.optString("version", "");
        if (!isSafePathSegment(modelId) || !isSafePathSegment(version)) {
            return false;
        }
        String directory = modelId + "/" + version + "/";
        String weightsResourceName = manifest.optString("weightsResourceName", "");
        String scalerResourceName = manifest.optString("scalerResourceName", "");
        if (!(directory + WEIGHTS_FILE).equals(weightsResourceName)
                || !(directory + SCALER_FILE).equals(scalerResourceName)
                || !"downloaded".equals(manifest.optString("source", ""))) {
            return false;
        }
        try {
            return hasArtifactIntegrity(
                    resourceFile(weightsResourceName),
                    manifest.optLong("weightsSizeBytes", -1L),
                    manifest.optString("weightsSha256", "")
            ) && hasArtifactIntegrity(
                    resourceFile(scalerResourceName),
                    manifest.optLong("scalerSizeBytes", -1L),
                    manifest.optString("scalerSha256", "")
            );
        } catch (IOException error) {
            return false;
        }
    }

    private static boolean matchesStoredMetadata(JSONObject pointer, JSONObject manifest) {
        if (pointer == null || manifest == null
                || pointer.optInt("schemaVersion", -1) != manifest.optInt("schemaVersion", -1)
                || pointer.optInt("classCount", -1) != manifest.optInt("classCount", -1)
                || pointer.optLong("weightsSizeBytes", -1L) != manifest.optLong("weightsSizeBytes", -1L)
                || pointer.optLong("scalerSizeBytes", -1L) != manifest.optLong("scalerSizeBytes", -1L)) {
            return false;
        }
        String[] keys = new String[]{
                "id",
                "version",
                "platform",
                "featureSchemaId",
                "weightsSha256",
                "scalerSha256",
                "weightsResourceName",
                "scalerResourceName",
                "source"
        };
        for (String key : keys) {
            if (!pointer.optString(key, "").equals(manifest.optString(key, ""))) {
                return false;
            }
        }
        JSONArray pointerLabels = pointer.optJSONArray("labelOrder");
        JSONArray manifestLabels = manifest.optJSONArray("labelOrder");
        if (pointerLabels == null
                || manifestLabels == null
                || pointerLabels.length() != manifestLabels.length()) {
            return false;
        }
        for (int index = 0; index < pointerLabels.length(); index++) {
            if (!pointerLabels.optString(index, "").equals(manifestLabels.optString(index, ""))) {
                return false;
            }
        }
        return true;
    }

    private File versionDirectory(String modelId, String version) throws IOException {
        return childDirectory(childDirectory(rootDirectory, modelId), version);
    }

    private File resourceFile(String resourceName) throws IOException {
        if (resourceName == null || resourceName.isEmpty()) {
            throw new IOException("downloaded model resource rejected");
        }
        File relative = new File(resourceName);
        if (relative.isAbsolute()) {
            throw new IOException("downloaded model resource rejected");
        }
        File file = new File(rootDirectory, resourceName);
        if (!isInsideRoot(file)) {
            throw new IOException("downloaded model resource rejected");
        }
        return file;
    }

    private File childDirectory(File parent, String segment) throws IOException {
        validatePathSegment(segment);
        File child = new File(parent, segment);
        if (!isInsideRoot(child) || child.equals(rootDirectory)) {
            throw new IOException("remote model path rejected");
        }
        return child;
    }

    private void validatePathSegment(String value) throws IOException {
        if (!isSafePathSegment(value)) {
            throw new IOException("remote model path rejected");
        }
    }

    static boolean isSafePathSegment(String value) {
        if (value == null || value.isEmpty() || value.length() > 128 || ".".equals(value) || "..".equals(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            boolean allowed = character >= 'a' && character <= 'z'
                    || character >= 'A' && character <= 'Z'
                    || character >= '0' && character <= '9'
                    || character == '_'
                    || character == '-'
                    || character == '.'
                    || character == '+';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private void ensureDirectory(File directory) throws IOException {
        if (!isInsideRoot(directory)) {
            throw new IOException("downloaded model directory rejected");
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("downloaded model directory create failed");
        }
        if (!directory.isDirectory()) {
            throw new IOException("downloaded model directory invalid");
        }
    }

    private boolean isInsideRoot(File file) {
        try {
            String root = rootDirectory.getCanonicalPath();
            String child = file.getCanonicalPath();
            return child.equals(root) || child.startsWith(root + File.separator);
        } catch (IOException error) {
            return false;
        }
    }

    private static boolean pointsTo(JSONObject pointer, String modelId, String version) {
        return pointer != null
                && modelId != null
                && version != null
                && modelId.equals(pointer.optString("id", ""))
                && version.equals(pointer.optString("version", ""));
    }

    private void removeUnreferencedVersion(
            JSONObject candidate,
            JSONObject nextCurrent,
            JSONObject nextPrevious
    ) {
        String modelId = value(candidate, "id");
        String version = value(candidate, "version");
        if (!isSafePathSegment(modelId)
                || !isSafePathSegment(version)
                || pointsTo(nextCurrent, modelId, version)
                || pointsTo(nextPrevious, modelId, version)) {
            return;
        }
        try {
            File directory = versionDirectory(modelId, version);
            deleteDirectoryQuietly(directory);
            deleteEmptyParent(directory.getParentFile());
        } catch (IOException ignored) {
            // 历史版本清理失败不能影响已经完成的模型激活。
        }
    }

    private static String value(JSONObject object, String key) {
        return object == null ? "" : object.optString(key, "");
    }

    private static String readText(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeJsonAtomically(File target, JSONObject object) throws IOException, JSONException {
        File parent = target.getParentFile();
        File temporary = new File(parent, "." + target.getName() + ".tmp-" + UUID.randomUUID());
        boolean moved = false;
        try {
            writeBytesSynced(temporary, object.toString(2).getBytes(StandardCharsets.UTF_8));
            replaceFile(temporary, target);
            moved = true;
        } finally {
            if (!moved) {
                deleteQuietly(temporary);
            }
        }
    }

    private static void writeBytesSynced(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static void moveDirectory(File source, File target) throws IOException {
        if (target.exists() || !source.renameTo(target)) {
            throw new IOException("staged model move failed");
        }
    }

    private static void replaceDirectory(File source, File target) throws IOException {
        File backup = new File(
                target.getParentFile(),
                "." + target.getName() + ".backup-" + UUID.randomUUID()
        );
        if (!target.renameTo(backup)) {
            throw new IOException("model directory backup failed");
        }
        if (source.renameTo(target)) {
            deleteDirectoryQuietly(backup);
            return;
        }
        backup.renameTo(target);
        throw new IOException("model directory replace failed");
    }

    private static String sha256Hex(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                int unsigned = value & 0xff;
                if (unsigned < 0x10) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(unsigned));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("sha256 unavailable", error);
        }
    }

    private static void replaceFile(File source, File target) throws IOException {
        if (tryAndroidAtomicRename(source, target)) {
            return;
        }
        File backup = new File(target.getParentFile(), "." + target.getName() + ".backup-" + UUID.randomUUID());
        boolean hadTarget = target.exists();
        if (hadTarget && !target.renameTo(backup)) {
            throw new IOException("model pointer backup failed");
        }
        if (source.renameTo(target)) {
            deleteQuietly(backup);
            return;
        }
        if (hadTarget) {
            backup.renameTo(target);
        }
        throw new IOException("model pointer replace failed");
    }

    private static boolean tryAndroidAtomicRename(File source, File target) throws IOException {
        try {
            Class<?> osClass = Class.forName("android.system.Os");
            Method rename = osClass.getMethod("rename", String.class, String.class);
            rename.invoke(null, source.getAbsolutePath(), target.getAbsolutePath());
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException error) {
            return false;
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause != null && "android.system.ErrnoException".equals(cause.getClass().getName())) {
                throw new IOException("model pointer replace failed", cause);
            }
            return false;
        } catch (LinkageError error) {
            return false;
        }
    }

    private static void deleteDirectory(File directory) throws IOException {
        if (!deleteDirectoryQuietly(directory)) {
            throw new IOException("downloaded model delete failed");
        }
    }

    private static boolean deleteDirectoryQuietly(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteDirectoryQuietly(child)) {
                    return false;
                }
            }
        }
        return file.delete();
    }

    private static void deleteEmptyParent(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] children = directory.listFiles();
        if (children != null && children.length == 0) {
            directory.delete();
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.isFile()) {
            file.delete();
        }
    }

    private static void deleteFile(File file) throws IOException {
        if (file != null && file.exists() && !file.delete()) {
            throw new IOException("model pointer delete failed");
        }
    }
}
