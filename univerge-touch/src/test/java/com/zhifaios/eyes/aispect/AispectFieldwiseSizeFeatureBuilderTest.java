package com.zhifaios.eyes.aispect;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class AispectFieldwiseSizeFeatureBuilderTest {
    @Test
    public void buildsThirteenSlotTwentyTwoChannelWindow() throws Exception {
        long anchor = 1_000_000_000L;
        AispectModels.ImpactFrame[] frames = new AispectModels.ImpactFrame[20];
        for (int index = 0; index < frames.length; index++) {
            long timestamp = anchor - 27_000_000L + index * 4_100_000L;
            frames[index] = new AispectModels.ImpactFrame(
                    0.0,
                    0.1,
                    0.2,
                    0.3,
                    0.01,
                    0.02,
                    0.03,
                    0.0,
                    0.0,
                    9.8,
                    0.4,
                    0.5,
                    (timestamp - anchor) / 1_000_000_000.0,
                    (timestamp - anchor) / 1_000_000_000.0,
                    timestamp / 1_000_000_000.0,
                    timestamp,
                    timestamp
            );
        }

        List<AispectTouchFrame> touchFrames = new ArrayList<>();
        touchFrames.add(AispectTouchFrame.forTesting(
                anchor - 26_000_000L,
                anchor - 25_000_000L,
                100.0f,
                200.0f,
                1000,
                2000,
                0.5f,
                0.20f,
                18.0f,
                9.0f,
                0.0f,
                1,
                false
        ));
        touchFrames.add(AispectTouchFrame.forTesting(
                anchor + 34_000_000L,
                anchor + 35_000_000L,
                100.0f,
                200.0f,
                1000,
                2000,
                0.5f,
                0.30f,
                24.0f,
                12.0f,
                0.0f,
                1,
                false
        ));

        double[][] output = AispectFieldwiseSizeFeatureBuilder.build(
                frames,
                touchFrames,
                anchor,
                "fieldwise_size_causal_w13_v1",
                scaler(),
                "unknown-device"
        );

        Assert.assertNotNull(output);
        Assert.assertEquals(13, output.length);
        Assert.assertEquals(22, output[0].length);
        Assert.assertEquals(1.0, output[0][20], 0.000001);
        Assert.assertEquals(1.0, output[12][20], 0.000001);
        Assert.assertTrue(AispectFieldwiseSizeFeatureBuilder.hasTimeGridSupport(
                frames,
                anchor,
                13,
                35L
        ));
    }

    @Test
    public void buildsWhenImuFramesExactlyMatchGridOffsets() throws Exception {
        long anchor = 1_000_000_000L;
        AispectModels.ImpactFrame[] frames = exactGridFrames(anchor);
        List<AispectTouchFrame> touchFrames = new ArrayList<>();
        touchFrames.add(touchFrame(anchor - 25_000_000L));

        double[][] output = AispectFieldwiseSizeFeatureBuilder.build(
                frames,
                touchFrames,
                anchor,
                "fieldwise_size_causal_w13_v1",
                scaler(),
                "unknown-device"
        );

        Assert.assertNotNull(output);
        Assert.assertEquals(13, output.length);
    }

    @Test
    public void rejectsNonCanonicalGridOffsets() throws Exception {
        JSONObject invalidScaler = scaler();
        invalidScaler.getJSONArray("gridOffsetsMs").put(4, -4);

        double[][] output = AispectFieldwiseSizeFeatureBuilder.build(
                exactGridFrames(1_000_000_000L),
                new ArrayList<AispectTouchFrame>(),
                1_000_000_000L,
                "fieldwise_size_causal_w13_v1",
                invalidScaler,
                "unknown-device"
        );

        Assert.assertNull(output);
    }

    private static AispectModels.ImpactFrame[] exactGridFrames(long anchor) {
        AispectModels.ImpactFrame[] frames = new AispectModels.ImpactFrame[13];
        for (int index = 0; index < frames.length; index++) {
            long timestamp = anchor + (-25L + index * 5L) * 1_000_000L;
            frames[index] = new AispectModels.ImpactFrame(
                    0.0,
                    0.1, 0.2, 0.3,
                    0.01, 0.02, 0.03,
                    0.0, 0.0, 9.8,
                    0.4, 0.5,
                    (timestamp - anchor) / 1_000_000_000.0,
                    (timestamp - anchor) / 1_000_000_000.0,
                    timestamp / 1_000_000_000.0,
                    timestamp,
                    timestamp
            );
        }
        return frames;
    }

    private static AispectTouchFrame touchFrame(long timestamp) {
        return AispectTouchFrame.forTesting(
                timestamp,
                timestamp,
                100.0f,
                200.0f,
                1000,
                2000,
                0.5f,
                0.20f,
                18.0f,
                9.0f,
                0.0f,
                1,
                false
        );
    }

    private static JSONObject scaler() throws Exception {
        JSONArray center = new JSONArray();
        JSONArray scale = new JSONArray();
        for (int index = 0; index < 22; index++) {
            center.put(0.0);
            scale.put(1.0);
        }
        JSONArray indices = new JSONArray();
        JSONArray offsets = new JSONArray();
        for (int index = 0; index < 13; index++) {
            indices.put(index);
            offsets.put(-25 + index * 5);
        }
        return new JSONObject()
                .put("runtimeArchitecture", "fieldwise_size_cnn_v1")
                .put("featureContract", "fieldwise_size_causal_w13_v1")
                .put("featureNames", new JSONArray(AispectFieldwiseSizeFeatureBuilder.featureNames()))
                .put("frameCount", 13)
                .put("frameIndices", indices)
                .put("gridOffsetsMs", offsets)
                .put("matrixShape", new JSONArray().put(13).put(22))
                .put("windowMode", "press")
                .put("captureDelayMs", 35)
                .put("center", center)
                .put("scale", scale);
    }
}
