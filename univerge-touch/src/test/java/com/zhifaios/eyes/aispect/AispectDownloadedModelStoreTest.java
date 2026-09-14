package com.zhifaios.eyes.aispect;

import com.zhifa.univerge.eyes.aispect.AispectDownloadedModelStore;
import com.zhifa.univerge.eyes.aispect.AispectRemoteModelAssignment;
import com.zhifa.univerge.eyes.aispect.AispectRemoteModelUpdater;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class AispectDownloadedModelStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void stagesNewVersionWithoutChangingCurrent() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());

        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());

        Assert.assertEquals(0, store.readCatalog().length());
        Assert.assertTrue(new File(temporaryFolder.getRoot(), "model-a/1.0.0/weights.json").isFile());
        Assert.assertTrue(new File(temporaryFolder.getRoot(), "model-a/1.0.0/scaler.json").isFile());
    }

    @Test
    public void activatesStagedVersionAtomically() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());
        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());

        store.activate("model-a", "1.0.0");

        JSONArray catalog = store.readCatalog();
        Assert.assertEquals(1, catalog.length());
        Assert.assertEquals("model-a", catalog.getJSONObject(0).getString("id"));
        Assert.assertEquals("1.0.0", catalog.getJSONObject(0).getString("version"));
        Assert.assertEquals("1.0.0", store.readCurrent().getString("version"));
    }

    @Test
    public void keepsPreviousVersionAfterActivation() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());
        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());
        store.activate("model-a", "1.0.0");
        store.stage(assignment("model-a", "2.0.0"), bytes(), bytes());

        store.activate("model-a", "2.0.0");

        Assert.assertEquals("2.0.0", store.readCurrent().getString("version"));
        Assert.assertEquals("1.0.0", store.readPrevious().getString("version"));
        Assert.assertTrue(new File(temporaryFolder.getRoot(), "model-a/1.0.0/weights.json").isFile());
    }

    @Test
    public void removesOnlyRejectedStagingDirectory() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());
        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());
        store.activate("model-a", "1.0.0");
        store.stage(assignment("model-a", "2.0.0"), bytes(), bytes());

        store.removeStaged("model-a", "2.0.0");

        Assert.assertEquals("1.0.0", store.readCurrent().getString("version"));
        Assert.assertFalse(new File(temporaryFolder.getRoot(), "model-a/2.0.0").exists());
        Assert.assertTrue(new File(temporaryFolder.getRoot(), "model-a/1.0.0").isDirectory());
    }

    @Test
    public void restoresPreviousAfterFailedActivation() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());
        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());
        store.activate("model-a", "1.0.0");
        store.stage(assignment("model-a", "2.0.0"), bytes(), bytes());
        store.activate("model-a", "2.0.0");

        store.rollbackActivation("model-a", "2.0.0");

        Assert.assertEquals("1.0.0", store.readCurrent().getString("version"));
        Assert.assertNull(store.readPrevious());
        Assert.assertFalse(new File(temporaryFolder.getRoot(), "model-a/2.0.0").exists());
        Assert.assertTrue(new File(temporaryFolder.getRoot(), "model-a/1.0.0").isDirectory());
    }

    @Test
    public void excludesCorruptCurrentResourceFromCatalog() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());
        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());
        store.activate("model-a", "1.0.0");
        java.nio.file.Files.write(
                new File(temporaryFolder.getRoot(), "model-a/1.0.0/weights.json").toPath(),
                "corrupt".getBytes(StandardCharsets.UTF_8)
        );

        JSONArray catalog = store.readCatalog();

        Assert.assertEquals(0, catalog.length());
    }

    @Test
    public void skipsCorruptPreviousDuringFailedActivationRollback() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());
        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());
        store.activate("model-a", "1.0.0");
        store.stage(assignment("model-a", "2.0.0"), bytes(), bytes());
        store.activate("model-a", "2.0.0");
        java.nio.file.Files.write(
                new File(temporaryFolder.getRoot(), "model-a/1.0.0/weights.json").toPath(),
                "corrupt".getBytes(StandardCharsets.UTF_8)
        );

        store.rollbackActivation("model-a", "2.0.0");

        Assert.assertNull(store.readCurrent());
        Assert.assertNull(store.readPrevious());
    }

    @Test
    public void reactivatesPreviousVersionForServerRollback() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());
        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());
        store.activate("model-a", "1.0.0");
        store.stage(assignment("model-a", "2.0.0"), bytes(), bytes());
        store.activate("model-a", "2.0.0");

        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());
        store.activate("model-a", "1.0.0");

        Assert.assertEquals("1.0.0", store.readCurrent().getString("version"));
        Assert.assertEquals("2.0.0", store.readPrevious().getString("version"));
        Assert.assertTrue(new File(temporaryFolder.getRoot(), "model-a/1.0.0/weights.json").isFile());
        Assert.assertTrue(new File(temporaryFolder.getRoot(), "model-a/2.0.0/weights.json").isFile());
    }

    @Test
    public void removesVersionOlderThanPreviousAfterThirdActivation() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());
        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());
        store.activate("model-a", "1.0.0");
        store.stage(assignment("model-a", "2.0.0"), bytes(), bytes());
        store.activate("model-a", "2.0.0");
        store.stage(assignment("model-a", "3.0.0"), bytes(), bytes());

        store.activate("model-a", "3.0.0");

        Assert.assertEquals("3.0.0", store.readCurrent().getString("version"));
        Assert.assertEquals("2.0.0", store.readPrevious().getString("version"));
        Assert.assertFalse(new File(temporaryFolder.getRoot(), "model-a/1.0.0").exists());
        Assert.assertTrue(new File(temporaryFolder.getRoot(), "model-a/2.0.0").isDirectory());
        Assert.assertTrue(new File(temporaryFolder.getRoot(), "model-a/3.0.0").isDirectory());
    }

    @Test
    public void restoresPointersAfterInterruptedActivation() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());
        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());
        store.activate("model-a", "1.0.0");
        store.stage(assignment("model-a", "2.0.0"), bytes(), bytes());
        store.activate("model-a", "2.0.0");
        store.stage(assignment("model-a", "3.0.0"), bytes(), bytes());
        JSONObject originalCurrent = store.readCurrent();
        JSONObject originalPrevious = store.readPrevious();
        writeActivationJournal(originalCurrent, originalPrevious, "model-a", "3.0.0");
        writeJson(new File(temporaryFolder.getRoot(), "previous.json"), originalCurrent);

        AispectDownloadedModelStore recovered = new AispectDownloadedModelStore(temporaryFolder.getRoot());

        Assert.assertEquals("2.0.0", recovered.readCurrent().getString("version"));
        Assert.assertEquals("1.0.0", recovered.readPrevious().getString("version"));
        Assert.assertFalse(new File(temporaryFolder.getRoot(), "activation.json").exists());
    }

    @Test
    public void keepsCommittedPointersWhenJournalCleanupWasInterrupted() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());
        store.stage(assignment("model-a", "1.0.0"), bytes(), bytes());
        store.activate("model-a", "1.0.0");
        store.stage(assignment("model-a", "2.0.0"), bytes(), bytes());
        store.activate("model-a", "2.0.0");
        store.stage(assignment("model-a", "3.0.0"), bytes(), bytes());
        JSONObject originalCurrent = store.readCurrent();
        JSONObject originalPrevious = store.readPrevious();
        writeActivationJournal(originalCurrent, originalPrevious, "model-a", "3.0.0");
        writeJson(new File(temporaryFolder.getRoot(), "previous.json"), originalCurrent);
        JSONObject next = new JSONObject(new String(
                java.nio.file.Files.readAllBytes(
                        new File(temporaryFolder.getRoot(), "model-a/3.0.0/manifest.json").toPath()
                ),
                StandardCharsets.UTF_8
        ));
        writeJson(new File(temporaryFolder.getRoot(), "current.json"), next);

        AispectDownloadedModelStore recovered = new AispectDownloadedModelStore(temporaryFolder.getRoot());

        Assert.assertEquals("3.0.0", recovered.readCurrent().getString("version"));
        Assert.assertEquals("2.0.0", recovered.readPrevious().getString("version"));
        Assert.assertFalse(new File(temporaryFolder.getRoot(), "activation.json").exists());
    }

    @Test
    public void rejectsPathTraversalInModelIdOrVersion() throws Exception {
        AispectDownloadedModelStore store = new AispectDownloadedModelStore(temporaryFolder.getRoot());

        assertStageRejected(store, assignment("../model-a", "1.0.0"));
        assertStageRejected(store, assignment("model-a", "../1.0.0"));
    }

    private static void assertStageRejected(
            AispectDownloadedModelStore store,
            AispectRemoteModelAssignment assignment
    ) throws Exception {
        try {
            store.stage(assignment, bytes(), bytes());
            Assert.fail("Expected stage to reject unsafe path segment");
        } catch (IOException expected) {
            Assert.assertEquals("remote model path rejected", expected.getMessage());
        }
    }

    private void writeActivationJournal(
            JSONObject originalCurrent,
            JSONObject originalPrevious,
            String targetModelId,
            String targetVersion
    ) throws Exception {
        JSONObject journal = new JSONObject();
        journal.put("targetModelId", targetModelId);
        journal.put("targetVersion", targetVersion);
        journal.put("originalCurrent", originalCurrent);
        journal.put("originalPrevious", originalPrevious);
        writeJson(new File(temporaryFolder.getRoot(), "activation.json"), journal);
    }

    private static void writeJson(File file, JSONObject object) throws Exception {
        java.nio.file.Files.write(file.toPath(), object.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private static AispectRemoteModelAssignment assignment(String modelId, String version) throws Exception {
        String sha256 = AispectRemoteModelUpdater.sha256Hex(bytes());
        return AispectRemoteModelAssignment.fromJson(new JSONObject("{"
                + "\"schemaVersion\":1,"
                + "\"assignmentId\":\"assign-" + version + "\","
                + "\"model\":{"
                + "\"id\":\"" + modelId + "\","
                + "\"version\":\"" + version + "\","
                + "\"displayName\":\"Model " + version + "\","
                + "\"platform\":\"android\","
                + "\"featureSchemaId\":\"android-touch-cnn-v1\","
                + "\"classCount\":4,"
                + "\"labelOrder\":[\"thumb_light\",\"thumb_heavy\",\"index_light\",\"index_heavy\"],"
                + "\"weights\":{\"url\":\"https://example.com/weights.json\",\"sha256\":\"" + sha256 + "\",\"sizeBytes\":2},"
                + "\"scaler\":{\"url\":\"https://example.com/scaler.json\",\"sha256\":\"" + sha256 + "\",\"sizeBytes\":2}"
                + "}"
                + "}"));
    }

    private static byte[] bytes() {
        return "{}".getBytes(StandardCharsets.UTF_8);
    }
}
