package com.zhifaios.eyes.aispect;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class AispectCausalRuntimeContractTest {
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
