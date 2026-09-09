package com.zhifaios.eyes.touch;

import com.zhifa.univerge.eyes.touch.AispectTouchClassifier;

import org.junit.Assert;
import org.junit.Test;

public final class AispectTouchClassifierVersionTest {
    @Test
    public void publicSdkVersionMatchesPublishedArtifact() {
        Assert.assertEquals("1.1.0", AispectTouchClassifier.SDK_VERSION);
    }
}
