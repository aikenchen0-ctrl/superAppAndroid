package com.zhifaios.eyes.touch;

import com.zhifa.univerge.eyes.touch.AispectTouchModelInfo;

import org.junit.Assert;
import org.junit.Test;

public final class AispectTouchModelInfoTest {
    @Test
    public void clonesMutableMetadataArrays() {
        int[] frameIndices = new int[]{-4, 0, 9};
        String[] featureNames = new String[]{"delta", "x"};
        String[] labelOrder = new String[]{"thumb_light", "thumb_heavy", "index_light", "index_heavy"};

        AispectTouchModelInfo info = new AispectTouchModelInfo(
                "model-a",
                "2.1.0",
                "Model A",
                2,
                3,
                frameIndices,
                featureNames,
                4,
                labelOrder
        );
        frameIndices[0] = 99;
        featureNames[0] = "changed";
        labelOrder[0] = "changed";

        Assert.assertEquals(-4, info.frameIndices[0]);
        Assert.assertEquals("2.1.0", info.version);
        Assert.assertEquals("delta", info.featureNames[0]);
        Assert.assertEquals("thumb_light", info.labelOrder[0]);
        Assert.assertEquals("release", info.windowMode);
        Assert.assertEquals(0L, info.captureDelayMs);
        Assert.assertEquals("", info.featureContract);
        Assert.assertEquals("", info.runtimeArchitecture);
    }

    @Test
    public void exposesCausalRuntimeContractMetadata() {
        AispectTouchModelInfo info = new AispectTouchModelInfo(
                "causal-model",
                "2026.09.04",
                "Causal touch",
                19,
                9,
                new int[]{-3, -2, -1, 0, 1, 2, 3, 4, 5},
                new String[]{"x_norm"},
                4,
                new String[]{"thumb_light", "thumb_heavy", "index_light", "index_heavy"},
                "press",
                25L,
                "causal_touch_relative_v1",
                "causal_touch_cnn_v1"
        );

        Assert.assertEquals("press", info.windowMode);
        Assert.assertEquals(25L, info.captureDelayMs);
        Assert.assertEquals("causal_touch_relative_v1", info.featureContract);
        Assert.assertEquals("causal_touch_cnn_v1", info.runtimeArchitecture);
    }

}
