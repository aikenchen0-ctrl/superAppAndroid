package com.blinkvoice.visual.api;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class BlinkVoiceSdkTest {
    @Test
    public void preloadWithNullContextNotifiesFailureWithoutThrowing() {
        final boolean[] callbackResult = {true};

        BlinkVoiceSdk.preload(null, detectorReady -> callbackResult[0] = detectorReady);

        assertFalse(callbackResult[0]);
    }
}
