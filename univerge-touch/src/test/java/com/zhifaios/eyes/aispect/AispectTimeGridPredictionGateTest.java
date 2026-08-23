package com.zhifaios.eyes.aispect;

import com.zhifa.univerge.eyes.aispect.AispectCollectionController;
import com.zhifa.univerge.eyes.aispect.AispectModels;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

public final class AispectTimeGridPredictionGateTest {
    @Test
    public void allowsPredictionOnlyAfterTheCompletePhysicalTimeGridExists() throws Exception {
        long downNanos = 1_000_000_000L;
        Method method = AispectCollectionController.class.getDeclaredMethod(
                "canPublishCausalTimeGridPrediction",
                AispectModels.ImpactFrame[].class,
                long.class,
                long.class
        );
        method.setAccessible(true);

        boolean allowed = (Boolean) method.invoke(
                null,
                frames(downNanos),
                downNanos,
                0L
        );

        Assert.assertTrue(allowed);
    }

    @Test
    public void rejectsTouchesThatLiftBeforeTheCausalPositiveEndpoint() throws Exception {
        long downNanos = 1_000_000_000L;
        Method method = AispectCollectionController.class.getDeclaredMethod(
                "canPublishCausalTimeGridPrediction",
                AispectModels.ImpactFrame[].class,
                long.class,
                long.class
        );
        method.setAccessible(true);

        boolean allowed = (Boolean) method.invoke(
                null,
                frames(downNanos),
                downNanos,
                downNanos + 24_000_000L
        );

        Assert.assertFalse(allowed);
    }

    private static AispectModels.ImpactFrame[] frames(long downNanos) {
        AispectModels.ImpactFrame[] frames = new AispectModels.ImpactFrame[11];
        for (int index = 0; index < frames.length; index++) {
            long timestamp = downNanos - 25_000_000L + index * 5_000_000L;
            frames[index] = new AispectModels.ImpactFrame(
                    0.0,
                    0.1,
                    0.2,
                    0.3,
                    0.4,
                    0.5,
                    0.6,
                    0.7,
                    0.8,
                    0.9,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    timestamp / 1_000_000_000.0,
                    timestamp,
                    timestamp + 1L
            );
        }
        return frames;
    }
}
