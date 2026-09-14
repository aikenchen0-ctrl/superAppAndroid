package com.zhifaios.eyes.aispect;

import com.zhifa.univerge.eyes.aispect.AispectImpactCNNClassifier;
import com.zhifa.univerge.eyes.aispect.AispectModels;
import com.zhifa.univerge.eyes.aispect.AispectSignalWindowBuilder;
import com.zhifa.univerge.eyes.aispect.AispectTouchFrame;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class AispectImpactCNNClassifierCausalFeatureTest {
    @Test
    public void buildsCausalModelFeaturesFromTimeGridAndScaler() {
        long anchor = 1_000_000_000L;
        AispectModels.ImpactFrame[] frames = new AispectModels.ImpactFrame[12];
        for (int index = 0; index < frames.length; index++) {
            long timestamp = anchor - 20_000_000L + index * 5_000_000L;
            frames[index] = new AispectModels.ImpactFrame(
                    0.0,
                    index,
                    index + 1.0,
                    index + 2.0,
                    0.1,
                    0.2,
                    0.3,
                    0.0,
                    0.0,
                    9.8,
                    0.0,
                    0.0,
                    (timestamp - anchor) / 1_000_000_000.0,
                    (timestamp - anchor) / 1_000_000_000.0,
                    timestamp / 1_000_000_000.0,
                    timestamp,
                    timestamp
            );
        }
        List<AispectTouchFrame> touchFrames = new ArrayList<>();
        touchFrames.add(AispectTouchFrame.forTesting(
                anchor,
                anchor,
                200.0f,
                400.0f,
                1000,
                2000,
                0.5f,
                0.25f,
                18.0f,
                9.0f,
                0.0f,
                1,
                false
        ));
        touchFrames.add(AispectTouchFrame.forTesting(
                anchor + 25_000_000L,
                anchor + 25_000_000L,
                220.0f,
                420.0f,
                1000,
                2000,
                0.5f,
                0.30f,
                24.0f,
                10.0f,
                0.0f,
                1,
                false
        ));

        int[] frameIndices = new int[]{-3, -2, -1, 0, 1, 2, 3, 4, 5};
        double[] center = new double[19];
        double[] scale = new double[19];
        for (int index = 0; index < scale.length; index++) {
            scale[index] = 1.0;
        }

        double[][] features = AispectImpactCNNClassifier.buildCausalModelFeatures(
                new AispectSignalWindowBuilder.Window(
                        frames,
                        0,
                        anchor / 1_000_000_000.0,
                        200.0,
                        0.0,
                        frames.length
                ),
                touchFrames,
                "causal_touch_relative_v1",
                frameIndices,
                center,
                scale
        );

        Assert.assertNotNull(features);
        Assert.assertEquals(9, features.length);
        Assert.assertEquals(19, features[0].length);
        Assert.assertEquals(0.2, features[3][0], 0.000001);
        Assert.assertEquals(1.0, features[features.length - 1][18], 0.000001);
    }

    @Test
    public void rejectsCausalModelFeaturesWithWrongFrameIndices() {
        long anchor = 1_000_000_000L;
        AispectModels.ImpactFrame[] frames = new AispectModels.ImpactFrame[12];
        for (int index = 0; index < frames.length; index++) {
            long timestamp = anchor - 20_000_000L + index * 5_000_000L;
            frames[index] = new AispectModels.ImpactFrame(
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    timestamp / 1_000_000_000.0, timestamp, timestamp
            );
        }
        int[] frameIndices = new int[]{-2, -1, 0, 1, 2, 3, 4, 5, 6};
        double[] center = new double[19];
        double[] scale = new double[19];
        for (int index = 0; index < scale.length; index++) {
            scale[index] = 1.0;
        }
        double[][] result = AispectImpactCNNClassifier.buildCausalModelFeatures(
                new AispectSignalWindowBuilder.Window(
                        frames,
                        0,
                        anchor / 1_000_000_000.0,
                        200.0,
                        0.0,
                        frames.length
                ),
                new ArrayList<AispectTouchFrame>(),
                "causal_touch_relative_v1",
                frameIndices,
                center,
                scale
        );
        Assert.assertNull(result);
    }

    @Test
    public void causalBuilderUsesNewestEventWhenTouchReceiptOrderIsReversed() {
        long anchor = 1_000_000_000L;
        AispectModels.ImpactFrame[] frames = new AispectModels.ImpactFrame[9];
        for (int index = 0; index < frames.length; index++) {
            long timestamp = anchor - 15_000_000L + index * 5_000_000L;
            frames[index] = new AispectModels.ImpactFrame(
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    timestamp / 1_000_000_000.0, timestamp, timestamp
            );
        }
        List<AispectTouchFrame> touchFrames = new ArrayList<>();
        touchFrames.add(AispectTouchFrame.forTesting(
                anchor - 10L,
                anchor - 1L,
                100.0f,
                200.0f,
                1000,
                2000,
                0.0f,
                0.25f,
                18.0f,
                9.0f,
                0.0f,
                1,
                false
        ));
        touchFrames.add(AispectTouchFrame.forTesting(
                anchor - 5L,
                anchor - 2L,
                800.0f,
                1600.0f,
                1000,
                2000,
                0.0f,
                0.25f,
                18.0f,
                9.0f,
                0.0f,
                1,
                false
        ));

        double[][] features = AispectImpactCNNClassifier.buildCausalModelFeatures(
                new AispectSignalWindowBuilder.Window(
                        frames,
                        0,
                        anchor / 1_000_000_000.0,
                        200.0,
                        0.0,
                        1
                ),
                touchFrames,
                "causal_touch_relative_v1",
                new int[]{-3, -2, -1, 0, 1, 2, 3, 4, 5},
                new double[19],
                ones(19)
        );

        Assert.assertNotNull(features);
        Assert.assertEquals(0.8, features[3][0], 0.000001);
        Assert.assertEquals(0.8, features[3][1], 0.000001);
    }

    private static double[] ones(int count) {
        double[] values = new double[count];
        for (int index = 0; index < count; index++) {
            values[index] = 1.0;
        }
        return values;
    }
}
