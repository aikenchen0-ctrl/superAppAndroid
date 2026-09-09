package com.zhifaios.eyes.aispect;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.zhifa.univerge.eyes.aispect.AispectCausalPressFeatureBuilder;
import com.zhifa.univerge.eyes.aispect.AispectImpactCNNClassifier;
import com.zhifa.univerge.eyes.aispect.AispectTimeGridGroupNormRuntime;

public final class AispectCausalRuntimeContractTest {
    @Test
    public void deltaGravityTransformationPreservesRowsAndAvoidsSemanticDrift() {
        double[][] source = new double[][]{
                {0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3},
                {0, 0, 0, 0, 0, 0, 0, 0, 2, 5, 4},
                {0, 0, 0, 0, 0, 0, 0, 0, 1, 4, 9}
        };

        double[][] output = AispectCausalPressFeatureBuilder.applyDeltaGravity(source);

        assertEquals(3, output.length);
        assertEquals(0.0, output[0][8], 0.000001);
        assertEquals(0.0, output[0][9], 0.000001);
        assertEquals(0.0, output[0][10], 0.000001);
        assertEquals(1.0, output[1][8], 0.000001);
        assertEquals(3.0, output[1][9], 0.000001);
        assertEquals(1.0, output[1][10], 0.000001);
        assertEquals(-1.0, output[2][8], 0.000001);
        assertEquals(-1.0, output[2][9], 0.000001);
        assertEquals(5.0, output[2][10], 0.000001);
    }

    @Test
    public void supportsBothPublishedCausalTimeGridContracts() {
        String deltaGravity = "causal_time_grid_imu11_touch7_mask5_delta_gravity_v1";
        String noGravity = "causal_time_grid_touch7_mask5_no_gravity_v1";

        assertTrue(AispectTimeGridGroupNormRuntime.isSupportedArchitecture("time_grid_groupnorm_cnn_v1"));
        assertTrue(AispectCausalPressFeatureBuilder.isTimeGridFeatureContract(deltaGravity));
        assertTrue(AispectCausalPressFeatureBuilder.isTimeGridFeatureContract(noGravity));
        assertEquals(23, AispectCausalPressFeatureBuilder.featureNames(deltaGravity).length);
        assertEquals(20, AispectCausalPressFeatureBuilder.featureNames(noGravity).length);
    }

    @Test
    public void supportsPublishedCausalTouchRelativeContract() {
        String contract = "causal_touch_relative_v1";

        assertTrue(AispectCausalPressFeatureBuilder.isCausalFeatureContract(contract));
        assertTrue(AispectCausalPressFeatureBuilder.isTimeGridFeatureContract(contract));
        assertEquals(19, AispectCausalPressFeatureBuilder.featureNames(contract).length);
    }

    @Test
    public void classifierExplicitlyRecognizesCausalContractsWithoutChannelHeuristics() throws Exception {
        Method method = AispectImpactCNNClassifier.class.getMethod(
                "usesCausalPressFeatureContract",
                String.class
        );

        assertTrue((Boolean) method.invoke(
                null,
                "causal_time_grid_imu11_touch7_mask5_delta_gravity_v1"
        ));
        assertTrue((Boolean) method.invoke(
                null,
                "causal_time_grid_touch7_mask5_no_gravity_v1"
        ));
        assertTrue(!(Boolean) method.invoke(null, "android-touch-cnn-v1"));
    }
}
