package com.zhifaios.eyes.aispect;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public final class AispectDeviceRegistrarTest {
    @Test
    public void postsSdkDeviceIdentityToRegistrationEndpoint() throws Exception {
        RecordingHttpClient client = new RecordingHttpClient();

        AispectDeviceRegistrar.registerWithClient(
                client,
                "http://127.0.0.1:8765",
                "com.example.host",
                "device-123",
                "1.0.0",
                "2.4.0",
                "touch-model",
                "3.0.0",
                true
        );

        Assert.assertEquals("http://127.0.0.1:8765/api/v1/devices/register", client.url);
        JSONObject body = new JSONObject(new String(client.body, StandardCharsets.UTF_8));
        Assert.assertEquals("android", body.getString("platform"));
        Assert.assertEquals("com.example.host", body.getString("appId"));
        Assert.assertEquals("device-123", body.getString("deviceId"));
        Assert.assertEquals("1.0.0", body.getString("sdkVersion"));
        Assert.assertEquals("2.4.0", body.getString("appVersion"));
        Assert.assertEquals("touch-model", body.getString("currentModelId"));
        Assert.assertEquals("3.0.0", body.getString("currentModelVersion"));
    }

    @Test
    public void postsOptionalDeviceNameWhenProvided() throws Exception {
        RecordingHttpClient client = new RecordingHttpClient();

        AispectDeviceRegistrar.registerWithClient(
                client,
                "https://models.example.com",
                "com.example.host",
                "device-123",
                "1.1.0",
                "2.4.0",
                "touch-model",
                "3.0.0",
                false,
                "OnePlus PHK110"
        );

        JSONObject body = new JSONObject(new String(client.body, StandardCharsets.UTF_8));
        Assert.assertEquals("OnePlus PHK110", body.getString("deviceName"));
    }

    private static final class RecordingHttpClient implements AispectModelHttpClient {
        String url;
        byte[] body;

        @Override
        public byte[] get(String ignored, int maxBytes) {
            return new byte[0];
        }

        @Override
        public void post(String url, byte[] body, int maxBytes) {
            this.url = url;
            this.body = body;
        }
    }
}
