package com.zhifaios.eyes.aispect;

import org.junit.Assert;
import org.junit.Test;

public final class AispectPendingModelActivationTest {
    @Test
    public void releasesPendingModelOnlyWhenTouchIsIdle() {
        AispectPendingModelActivation pending = new AispectPendingModelActivation();
        pending.replace("model-a", "2.0.0");

        Assert.assertNull(pending.takeIfIdle(true));
        Assert.assertTrue(pending.hasPending());

        AispectPendingModelActivation.Model model = pending.takeIfIdle(false);

        Assert.assertEquals("model-a", model.modelId);
        Assert.assertEquals("2.0.0", model.version);
        Assert.assertFalse(pending.hasPending());
    }

    @Test
    public void returnsSupersededPendingModelForCleanup() {
        AispectPendingModelActivation pending = new AispectPendingModelActivation();
        pending.replace("model-a", "1.0.0");

        AispectPendingModelActivation.Model superseded = pending.replace("model-a", "2.0.0");

        Assert.assertEquals("1.0.0", superseded.version);
        Assert.assertEquals("2.0.0", pending.takeIfIdle(false).version);
    }

    @Test
    public void consecutiveReplacementReturnsOnlyTheImmediatelySupersededModel() {
        AispectPendingModelActivation pending = new AispectPendingModelActivation();
        pending.replace("model-a", "1.0.0");
        pending.replace("model-b", "2.0.0");

        AispectPendingModelActivation.Model superseded = pending.replace("model-c", "3.0.0");

        Assert.assertTrue(superseded.matches("model-b", "2.0.0"));
        Assert.assertTrue(pending.takeIfIdle(false).matches("model-c", "3.0.0"));
    }

    @Test
    public void sameVersionReplacementMatchesCurrentAndMustNotBeRemoved() {
        AispectPendingModelActivation pending = new AispectPendingModelActivation();
        pending.replace("model-a", "1.0.0");

        AispectPendingModelActivation.Model superseded = pending.replace("model-a", "1.0.0");

        Assert.assertTrue(superseded.matches("model-a", "1.0.0"));
        Assert.assertTrue(pending.takeIfIdle(false).matches("model-a", "1.0.0"));
    }
}
