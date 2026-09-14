package com.zhifaios.eyes.aispect;

import com.zhifa.univerge.eyes.aispect.AispectCollectionController;
import com.zhifa.univerge.eyes.aispect.AispectSignalWindowBuilder;

import org.junit.Assert;
import org.junit.Test;

public final class AispectSignalWindowBuilderTest {
    @Test
    public void derivesReleasePostRollFromSensorRateInsteadOfUsingFixedDelay() {
        AispectSignalWindowBuilder builder = new AispectSignalWindowBuilder();
        AispectCollectionController.Config controllerConfig = new AispectCollectionController.Config();

        Assert.assertEquals(5, builder.config().preFrames);
        Assert.assertEquals(9, builder.config().postFrames);
        Assert.assertEquals(0L, controllerConfig.postTouchCaptureDelayMs);
        Assert.assertEquals(46L, AispectCollectionController.releaseCompletionDelayMs(240.0, 9, 8L));
        Assert.assertEquals(83L, AispectCollectionController.releaseCompletionDelayMs(120.0, 9, 8L));
    }

    @Test
    public void releaseDelaySaturatesWithoutOverflow() {
        Assert.assertEquals(Long.MAX_VALUE, AispectCollectionController.releaseCompletionDelayMs(Double.MIN_VALUE, 15, 8L));
        Assert.assertEquals(8L, AispectCollectionController.releaseCompletionDelayMs(240.0, -1, 8L));
        Assert.assertEquals(133L, AispectCollectionController.releaseCompletionDelayMs(Double.NaN, 15, 8L));
    }

    @Test
    public void releaseWindowClampsConfigAndAcceptsNullFrames() {
        AispectSignalWindowBuilder builder = new AispectSignalWindowBuilder();
        builder.config().preFrames = Integer.MAX_VALUE;
        builder.config().postFrames = Integer.MAX_VALUE;

        AispectSignalWindowBuilder.Window window = builder.build(null, 1.0, 240.0);

        Assert.assertEquals(-5, window.firstFrameIndex);
        Assert.assertEquals(21, window.frames.length);
        Assert.assertEquals(0, window.availableFrameCount);
    }
}
