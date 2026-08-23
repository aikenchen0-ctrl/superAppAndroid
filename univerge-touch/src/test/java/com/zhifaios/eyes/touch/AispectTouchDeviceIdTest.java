package com.zhifaios.eyes.touch;

import org.junit.Assert;
import org.junit.Test;

public final class AispectTouchDeviceIdTest {
    @Test
    public void keepsHostProvidedDeviceId() {
        Assert.assertEquals("host-device", AispectTouchDeviceId.resolve("host-device", "generated-device"));
    }

    @Test
    public void usesGeneratedDeviceIdWhenHostValueIsEmpty() {
        Assert.assertEquals("generated-device", AispectTouchDeviceId.resolve("", "generated-device"));
        Assert.assertEquals("generated-device", AispectTouchDeviceId.resolve(null, "generated-device"));
    }
}
