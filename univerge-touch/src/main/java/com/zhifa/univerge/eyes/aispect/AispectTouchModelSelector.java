package com.zhifa.univerge.eyes.aispect;

import java.util.List;

final class AispectTouchModelSelector {
    static final String PUBLISHED_MODEL_ID = "causal_touch_relative_v1_all_devices_fit_20260904";
    static final String LEGACY_PUBLISHED_MODEL_ID = "lemon_tao_full4_enhanced_patch21_no_confidence";
    static final String CONTACT_RICH_MODEL_ID = "oneplus_four_class_after_recollect_hard_nearfull_weighted_old13_multichannel";
    static final String LEVEL_C_MODEL_ID = "oneplus_four_class_hard_core_old13";

    private AispectTouchModelSelector() {
    }

    static String initialModelId(
            String activeDownloadedModelId,
            AispectDeviceCapabilityProfiler.Snapshot capability,
            List<AispectImpactCNNClassifier.ModelInfo> models
    ) {
        if (containsModelId(models, activeDownloadedModelId)) {
            return activeDownloadedModelId;
        }
        return recommendedModelId(capability, models);
    }

    static String recommendedModelId(
            AispectDeviceCapabilityProfiler.Snapshot capability,
            List<AispectImpactCNNClassifier.ModelInfo> models
    ) {
        if (models == null || models.isEmpty()) {
            return null;
        }
        String publishedModel = findModelId(models, PUBLISHED_MODEL_ID);
        if (publishedModel != null) {
            return publishedModel;
        }
        String legacyPublishedModel = findModelId(models, LEGACY_PUBLISHED_MODEL_ID);
        if (legacyPublishedModel != null) {
            return legacyPublishedModel;
        }
        if (capability != null && capability.level == AispectDeviceCapabilityProfiler.Level.C) {
            String levelCModel = findModelId(models, LEVEL_C_MODEL_ID);
            if (levelCModel != null) {
                return levelCModel;
            }
        }
        String contactRichModel = findModelId(models, CONTACT_RICH_MODEL_ID);
        if (contactRichModel != null) {
            return contactRichModel;
        }
        return models.get(0).id;
    }

    static boolean isRecommendedModel(
            String modelId,
            AispectDeviceCapabilityProfiler.Snapshot capability,
            List<AispectImpactCNNClassifier.ModelInfo> models
    ) {
        String recommended = recommendedModelId(capability, models);
        return recommended != null && recommended.equals(modelId);
    }

    static String predictionModelId(
            String currentModelId,
            boolean manualSelection,
            AispectDeviceCapabilityProfiler.Snapshot capability,
            List<AispectImpactCNNClassifier.ModelInfo> models
    ) {
        if (manualSelection && containsModelId(models, currentModelId)) {
            return currentModelId;
        }
        String recommended = recommendedModelId(capability, models);
        if (recommended != null) {
            return recommended;
        }
        return currentModelId;
    }

    private static String findModelId(List<AispectImpactCNNClassifier.ModelInfo> models, String modelId) {
        for (AispectImpactCNNClassifier.ModelInfo model : models) {
            if (model != null && modelId.equals(model.id)) {
                return model.id;
            }
        }
        return null;
    }

    private static boolean containsModelId(List<AispectImpactCNNClassifier.ModelInfo> models, String modelId) {
        return modelId != null && findModelId(models, modelId) != null;
    }

}
