package com.zhifaios.eyes.touch;

import org.junit.Assert;
import org.junit.Test;

public final class AispectTouchModelUpdateResultTest {
    @Test
    public void exposesRequestedAndActiveModelMetadata() {
        AispectTouchModelUpdateResult result = new AispectTouchModelUpdateResult(
                AispectTouchModelUpdateStatus.DEFERRED,
                "model-new",
                "2.0.0",
                "model-old",
                "1.0.0",
                "touch_active"
        );

        Assert.assertEquals(AispectTouchModelUpdateStatus.DEFERRED, result.status);
        Assert.assertEquals("model-new", result.requestedModelId);
        Assert.assertEquals("2.0.0", result.requestedModelVersion);
        Assert.assertEquals("model-old", result.activeModelId);
        Assert.assertEquals("1.0.0", result.activeModelVersion);
        Assert.assertEquals("touch_active", result.reason);
        Assert.assertFalse(result.updated);
        Assert.assertEquals("model-new", result.modelId);
    }

    @Test
    public void marksOnlyActivatedResultAsUpdated() {
        AispectTouchModelUpdateResult result = new AispectTouchModelUpdateResult(
                AispectTouchModelUpdateStatus.ACTIVATED,
                "model-a",
                "2.0.0",
                "model-a",
                "2.0.0",
                "updated"
        );

        Assert.assertTrue(result.updated);
    }
}
