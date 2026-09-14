package com.zhifaios.eyes.aispect;

import com.zhifa.univerge.eyes.aispect.AispectRemoteModelAssignment;
import com.zhifa.univerge.eyes.aispect.AispectRemoteModelUpdater;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class AispectRemoteModelAssignmentTest {
    @Test
    public void parsesV1Assignment() throws Exception {
        JSONObject json = new JSONObject("{"
                + "\"schemaVersion\":1,"
                + "\"assignmentId\":\"assign-v1\","
                + "\"deviceId\":\"device-a\","
                + "\"expiresAt\":1790000000,"
                + "\"model\":{"
                + "\"id\":\"android_oneplus_4class_v1\","
                + "\"version\":\"1.0.0\","
                + "\"displayName\":\"OnePlus 4-class v1\","
                + "\"platform\":\"android\","
                + "\"featureSchemaId\":\"android-touch-cnn-v1\","
                + "\"classCount\":4,"
                + "\"labelOrder\":[\"thumb_light\",\"thumb_heavy\",\"index_light\",\"index_heavy\"],"
                + "\"weights\":{"
                + "\"url\":\"https://example.com/model/weights.json\","
                + "\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"sizeBytes\":123"
                + "},"
                + "\"scaler\":{"
                + "\"url\":\"https://example.com/model/scaler.json\","
                + "\"sha256\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\","
                + "\"sizeBytes\":456"
                + "}"
                + "}"
                + "}");

        AispectRemoteModelAssignment assignment = AispectRemoteModelAssignment.fromJson(json);

        Assert.assertTrue(assignment.isUsable());
        Assert.assertEquals("https://example.com/model/weights.json", assignment.weightsUrl);
        Assert.assertEquals("https://example.com/model/scaler.json", assignment.scalerUrl);
    }

    @Test
    public void parsesNestedAssignmentResponse() throws Exception {
        JSONObject json = new JSONObject("{"
                + "\"assignmentId\":\"assign-1\","
                + "\"deviceId\":\"device-a\","
                + "\"model\":{"
                + "\"id\":\"android_oneplus_4class_v1\","
                + "\"version\":\"1.0.0\","
                + "\"displayName\":\"OnePlus 4-class v1\","
                + "\"weightsUrl\":\"https://example.com/model/weights.json\","
                + "\"scalerUrl\":\"https://example.com/model/scaler.json\","
                + "\"weightsSha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"scalerSha256\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\""
                + "}"
                + "}");

        AispectRemoteModelAssignment assignment = AispectRemoteModelAssignment.fromJson(json);

        Assert.assertEquals("assign-1", assignment.assignmentId);
        Assert.assertEquals("device-a", assignment.deviceId);
        Assert.assertEquals("android_oneplus_4class_v1", assignment.modelId);
        Assert.assertEquals("1.0.0", assignment.version);
        Assert.assertEquals("OnePlus 4-class v1", assignment.displayName);
        Assert.assertTrue(assignment.hasRequiredIntegrity());
    }

    @Test
    public void rejectsMissingIntegrityHashes() throws Exception {
        JSONObject json = new JSONObject("{"
                + "\"assignmentId\":\"assign-2\","
                + "\"model\":{"
                + "\"id\":\"android_oneplus_4class_v2\","
                + "\"version\":\"2.0.0\","
                + "\"displayName\":\"OnePlus 4-class v2\","
                + "\"weightsUrl\":\"https://example.com/model/weights.json\","
                + "\"scalerUrl\":\"https://example.com/model/scaler.json\""
                + "}"
                + "}");

        AispectRemoteModelAssignment assignment = AispectRemoteModelAssignment.fromJson(json);

        Assert.assertFalse(assignment.hasRequiredIntegrity());
    }

    @Test
    public void acceptsLegacyFlatAssignment() throws Exception {
        AispectRemoteModelAssignment assignment = AispectRemoteModelAssignment.fromJson(new JSONObject(legacyModelJson(
                "android",
                "[\"thumb_light\",\"thumb_heavy\",\"index_light\",\"index_heavy\"]"
        )));

        Assert.assertTrue(assignment.isUsable());
    }

    @Test
    public void rejectsNonAndroidAssignment() throws Exception {
        AispectRemoteModelAssignment assignment = AispectRemoteModelAssignment.fromJson(new JSONObject(legacyModelJson(
                "ios",
                "[\"thumb_light\",\"thumb_heavy\",\"index_light\",\"index_heavy\"]"
        )));

        Assert.assertFalse(assignment.isUsable());
    }

    @Test
    public void rejectsNonCanonicalFourClassLabels() throws Exception {
        AispectRemoteModelAssignment assignment = AispectRemoteModelAssignment.fromJson(new JSONObject(legacyModelJson(
                "android",
                "[\"heavy\",\"light\"]"
        )));

        Assert.assertFalse(assignment.isUsable());
    }

    @Test
    public void rejectsAssignmentWithoutModelVersion() throws Exception {
        JSONObject json = new JSONObject(legacyModelJson(
                "android",
                "[\"thumb_light\",\"thumb_heavy\",\"index_light\",\"index_heavy\"]"
        ));
        json.getJSONObject("model").remove("version");

        AispectRemoteModelAssignment assignment = AispectRemoteModelAssignment.fromJson(json);

        Assert.assertFalse(assignment.isUsable());
    }

    @Test
    public void computesSha256Hex() throws Exception {
        byte[] bytes = "abc".getBytes("UTF-8");

        Assert.assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                AispectRemoteModelUpdater.sha256Hex(bytes)
        );
    }

    private static String legacyModelJson(String platform, String labelOrder) {
        return "{"
                + "\"assignmentId\":\"assign-legacy\","
                + "\"model\":{"
                + "\"id\":\"android_oneplus_4class_legacy\","
                + "\"version\":\"1.0.0\","
                + "\"platform\":\"" + platform + "\","
                + "\"featureSchemaId\":\"android-touch-cnn-v1\","
                + "\"classCount\":4,"
                + "\"labelOrder\":" + labelOrder + ","
                + "\"weightsUrl\":\"https://example.com/model/weights.json\","
                + "\"scalerUrl\":\"https://example.com/model/scaler.json\","
                + "\"weightsSha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"scalerSha256\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\""
                + "}"
                + "}";
    }
}
