package com.zhifaios.eyes.aispect;

import org.junit.Assert;
import org.junit.Test;

public final class AispectAccelerationNormalizerTest {
    @Test
    public void keepsLinearAccelerationUnchanged() {
        AispectAccelerationNormalizer.Sample sample = AispectAccelerationNormalizer.normalize(
                true,
                false,
                1.0,
                -2.0,
                3.0,
                0.0,
                0.0,
                0.0
        );

        Assert.assertTrue(sample.reliable);
        Assert.assertEquals(1.0, sample.x, 0.000001);
        Assert.assertEquals(-2.0, sample.y, 0.000001);
        Assert.assertEquals(3.0, sample.z, 0.000001);
    }

    @Test
    public void subtractsGravityFromRawAccelerometer() {
        AispectAccelerationNormalizer.Sample sample = AispectAccelerationNormalizer.normalize(
                false,
                true,
                1.5,
                -1.0,
                12.0,
                0.5,
                1.0,
                9.5
        );

        Assert.assertTrue(sample.reliable);
        Assert.assertEquals(1.0, sample.x, 0.000001);
        Assert.assertEquals(-2.0, sample.y, 0.000001);
        Assert.assertEquals(2.5, sample.z, 0.000001);
    }

    @Test
    public void rejectsRawAccelerometerBeforeGravityIsAvailable() {
        AispectAccelerationNormalizer.Sample sample = AispectAccelerationNormalizer.normalize(
                false,
                false,
                1.0,
                2.0,
                3.0,
                0.0,
                0.0,
                0.0
        );

        Assert.assertFalse(sample.reliable);
    }
}
