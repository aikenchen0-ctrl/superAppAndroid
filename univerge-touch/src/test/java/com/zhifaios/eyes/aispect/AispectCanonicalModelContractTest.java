package com.zhifaios.eyes.aispect;

import org.junit.Assert;
import org.junit.Test;

public final class AispectCanonicalModelContractTest {
    @Test
    public void returnsDefensiveCopiesOfCanonicalArrays() {
        int[] frames = AispectCanonicalModelContract.frameIndices();
        String[] features = AispectCanonicalModelContract.featureNames();
        String[] labels = AispectCanonicalModelContract.labelOrder();

        frames[0] = 99;
        features[0] = "changed";
        labels[0] = "changed";

        Assert.assertArrayEquals(
                new int[]{-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
                AispectCanonicalModelContract.frameIndices()
        );
        Assert.assertEquals("x_norm", AispectCanonicalModelContract.featureNames()[0]);
        Assert.assertEquals("thumb_light", AispectCanonicalModelContract.labelOrder()[0]);
    }
}
