package com.zhifaios.eyes.aispect;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class AispectTouchModelSelectorTest {
    @Test
    public void recommendsNonContactModelForLevelCDevice() {
        List<AispectImpactCNNClassifier.ModelInfo> models = Arrays.asList(
                model("oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel"),
                model("oneplus_four_class_hard_core_old13")
        );

        String modelId = AispectTouchModelSelector.recommendedModelId(level(AispectDeviceCapabilityProfiler.Level.C), models);

        Assert.assertEquals("oneplus_four_class_hard_core_old13", modelId);
    }

    @Test
    public void keepsContactRichModelForLevelADevice() {
        List<AispectImpactCNNClassifier.ModelInfo> models = Arrays.asList(
                model("oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel"),
                model("oneplus_four_class_hard_core_old13")
        );

        String modelId = AispectTouchModelSelector.recommendedModelId(level(AispectDeviceCapabilityProfiler.Level.A), models);

        Assert.assertEquals("oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel", modelId);
    }

    @Test
    public void prefersPublishedLemonTaoModelForFreshInstall() {
        List<AispectImpactCNNClassifier.ModelInfo> models = Arrays.asList(
                model("lemon_tao_full4_enhanced_patch21_no_confidence"),
                model("oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel")
        );

        String modelId = AispectTouchModelSelector.recommendedModelId(
                level(AispectDeviceCapabilityProfiler.Level.A),
                models
        );

        Assert.assertEquals("lemon_tao_full4_enhanced_patch21_no_confidence", modelId);
    }

    @Test
    public void usesRecommendedModelWhenSelectionIsAutomatic() {
        List<AispectImpactCNNClassifier.ModelInfo> models = Arrays.asList(
                model("oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel"),
                model("oneplus_four_class_hard_core_old13")
        );

        String modelId = AispectTouchModelSelector.predictionModelId(
                "oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel",
                false,
                level(AispectDeviceCapabilityProfiler.Level.C),
                models
        );

        Assert.assertEquals("oneplus_four_class_hard_core_old13", modelId);
    }

    @Test
    public void keepsCurrentModelWhenSelectionIsManual() {
        List<AispectImpactCNNClassifier.ModelInfo> models = Arrays.asList(
                model("oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel"),
                model("oneplus_four_class_hard_core_old13")
        );

        String modelId = AispectTouchModelSelector.predictionModelId(
                "oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel",
                true,
                level(AispectDeviceCapabilityProfiler.Level.C),
                models
        );

        Assert.assertEquals("oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel", modelId);
    }

    @Test
    public void prefersActiveDownloadedModelAfterProcessRestart() {
        List<AispectImpactCNNClassifier.ModelInfo> models = Arrays.asList(
                model("server_assigned_model"),
                model("oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel"),
                model("oneplus_four_class_hard_core_old13")
        );

        String modelId = AispectTouchModelSelector.initialModelId(
                "server_assigned_model",
                level(AispectDeviceCapabilityProfiler.Level.C),
                models
        );

        Assert.assertEquals("server_assigned_model", modelId);
    }

    private static AispectImpactCNNClassifier.ModelInfo model(String id) {
        return new AispectImpactCNNClassifier.ModelInfo(
                id,
                id,
                "weights_" + id,
                "scaler_" + id,
                13,
                15,
                new int[]{-5, 9},
                new String[]{"x_norm"},
                4,
                new String[]{"thumb_light", "thumb_heavy", "index_light", "index_heavy"}
        );
    }

    private static AispectDeviceCapabilityProfiler.Snapshot level(AispectDeviceCapabilityProfiler.Level level) {
        return new AispectDeviceCapabilityProfiler.Snapshot(
                level,
                level != AispectDeviceCapabilityProfiler.Level.C,
                level == AispectDeviceCapabilityProfiler.Level.A,
                level == AispectDeviceCapabilityProfiler.Level.A,
                level == AispectDeviceCapabilityProfiler.Level.A,
                level == AispectDeviceCapabilityProfiler.Level.A,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                1,
                "test",
                "test"
        );
    }
}
