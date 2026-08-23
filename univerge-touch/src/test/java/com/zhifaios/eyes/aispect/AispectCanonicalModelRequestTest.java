package com.zhifaios.eyes.aispect;

import org.junit.Assert;
import org.junit.Test;

public final class AispectCanonicalModelRequestTest {
    @Test
    public void buildsEncodedCanonicalAssignmentUrl() {
        String url = AispectCanonicalModelRequest.build(
                "https://models.example.com/root/",
                "com.example host",
                "device/one",
                "1.0.0",
                "2.4 beta",
                "model id",
                "1.2.0"
        );

        Assert.assertEquals(
                "https://models.example.com/root/api/v1/model-assignment"
                        + "?appId=com.example%20host"
                        + "&deviceId=device%2Fone"
                        + "&sdkVersion=1.0.0"
                        + "&platform=android"
                        + "&currentModelId=model%20id"
                        + "&currentModelVersion=1.2.0"
                        + "&appVersion=2.4%20beta",
                url
        );
    }

    @Test
    public void rejectsMissingIdentityOrInsecureBaseUrl() {
        Assert.assertEquals("", AispectCanonicalModelRequest.build(
                "https://models.example.com",
                "",
                "device-a",
                "1.0.0",
                "",
                "",
                ""
        ));
        Assert.assertEquals("", AispectCanonicalModelRequest.build(
                "http://models.example.com",
                "com.example.host",
                "device-a",
                "1.0.0",
                "",
                "",
                ""
        ));
    }

    @Test
    public void buildsHttpAssignmentUrlOnlyWhenExplicitlyAllowed() {
        Assert.assertEquals(
                "http://192.168.31.39:8000/api/v1/model-assignment"
                        + "?appId=com.example.host"
                        + "&deviceId=device-a"
                        + "&sdkVersion=1.0.0"
                        + "&platform=android",
                AispectCanonicalModelRequest.build(
                        "http://192.168.31.39:8000",
                        "com.example.host",
                        "device-a",
                        "1.0.0",
                        "",
                        "",
                        "",
                        true
                )
        );
    }
}
