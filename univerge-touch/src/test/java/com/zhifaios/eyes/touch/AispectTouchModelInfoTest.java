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
    }

}
