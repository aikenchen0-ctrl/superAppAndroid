package com.zhifaios.eyes.touch;

import com.zhifaios.eyes.aispect.AispectModels;

import org.junit.Assert;
import org.junit.Test;

public final class AispectTouchResultMapperTest {
    @Test
    public void mapsFourClassPredictionToPublicResult() {
        AispectModels.ImpactPrediction prediction = new AispectModels.ImpactPrediction(
                new double[]{0.10, 0.80, 0.05, 0.05},
                new String[]{"thumb_light", "thumb_heavy", "index_light", "index_heavy"},
                "model-a",
                "2.1.0"
        );
        AispectModels.TouchEvent event = new AispectModels.TouchEvent(
                AispectModels.TouchKind.HEAVY_TAP,
                12f,
                24f,
                123.456,
                6f
        );

        AispectTouchResult result = AispectTouchResultMapper.fromPrediction(prediction, event);

        Assert.assertEquals(AispectTouchEventType.TAP, result.eventType);
        Assert.assertEquals(AispectTouchStrength.HEAVY, result.strength);
        Assert.assertEquals(AispectFingerType.THUMB, result.fingerType);
        Assert.assertEquals("thumb_heavy", result.classLabel);
        Assert.assertEquals("model-a", result.modelId);
        Assert.assertEquals("2.1.0", result.modelVersion);
        Assert.assertEquals(0.80, result.confidence, 0.000001);
        Assert.assertEquals(123456L, result.eventTimeMillis);
    }

    @Test
    public void keepsFingerUnknownForBinaryPrediction() {
        AispectModels.ImpactPrediction prediction = new AispectModels.ImpactPrediction(
                new double[]{0.70, 0.30},
                new String[]{"heavy", "light"},
                "binary-model"
        );
        AispectModels.TouchEvent event = new AispectModels.TouchEvent(
                AispectModels.TouchKind.LIGHT_TAP,
                12f,
                24f,
                10.0,
                null
        );

        AispectTouchResult result = AispectTouchResultMapper.fromPrediction(prediction, event);

        Assert.assertEquals(AispectTouchStrength.HEAVY, result.strength);
        Assert.assertEquals(AispectFingerType.UNKNOWN, result.fingerType);
        Assert.assertEquals("heavy", result.classLabel);
        Assert.assertEquals(0.70, result.confidence, 0.000001);
    }

    @Test
    public void mapsCausalPredictionToPressEvent() {
        AispectModels.ImpactPrediction prediction = new AispectModels.ImpactPrediction(
                new double[]{0.20, 0.70, 0.05, 0.05},
                new String[]{"thumb_light", "thumb_heavy", "index_light", "index_heavy"},
                "causal-model",
                "1.0.0"
        );
        AispectModels.TouchKind press = Enum.valueOf(AispectModels.TouchKind.class, "PRESS");
        AispectModels.TouchEvent event = new AispectModels.TouchEvent(
                press,
                12f,
                24f,
                10.0,
                null
        );

        AispectTouchResult result = AispectTouchResultMapper.fromPrediction(prediction, event);

        Assert.assertEquals(AispectTouchEventType.PRESS, result.eventType);
    }

}
