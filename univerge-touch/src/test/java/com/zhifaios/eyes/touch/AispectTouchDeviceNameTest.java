package com.zhifaios.eyes.touch;

import org.junit.Assert;
import org.junit.Test;

public final class AispectTouchDeviceNameTest {
    @Test
    public void prefersConfiguredNameAndOtherwiseCombinesManufacturerAndModel() {
        Assert.assertEquals(
                "Custom device",
                AispectTouchClassifier.resolveRemoteModelDeviceName(" Custom device ", "OnePlus", "PHK110")
        );
        Assert.assertEquals(
                "OnePlus PHK110",
                AispectTouchClassifier.resolveRemoteModelDeviceName("", "OnePlus", "PHK110")
        );
    }
}
