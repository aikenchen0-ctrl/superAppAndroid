package com.zhifa.univerge.eyes.touch;

public final class AispectTouchConfig {
    public boolean enableCnnDiagnostics = true;
    public long postTouchCaptureDelayMs = 0L;
    public float maximumPressTravelPx = 36f;
    public long minimumSampleIntervalMs = 4L;
    public float dragActivationRadiusPx = 28f;
    public float heavyTouchAreaThresholdPx2 = 40f;
    public String remoteModelBaseUrl = "";
    public String remoteModelAppId = "";
    public String remoteModelAppVersion = "";
    public String remoteModelDeviceId = "";
    public String[] remoteModelAllowedHosts = new String[0];
    public boolean allowInsecureRemoteModelTransport = false;

    public static AispectTouchConfig defaultConfig() {
        return new AispectTouchConfig();
    }
}
