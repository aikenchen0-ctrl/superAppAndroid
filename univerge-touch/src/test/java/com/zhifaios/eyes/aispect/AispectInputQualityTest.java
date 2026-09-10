package com.zhifaios.eyes.aispect;

import org.junit.Assert;
import org.junit.Test;

public final class AispectInputQualityTest {
    @Test
    public void rejectsMultiPointerGesture() {
        AispectInputQuality.Reason result = AispectInputQuality.gestureReason(2, 0.0, false, 36.0);

        Assert.assertEquals(AispectInputQuality.Reason.MULTI_POINTER, result);
    }

    @Test
    public void rejectsDraggedOrCancelledGesture() {
        AispectInputQuality.Reason result = AispectInputQuality.gestureReason(1, 0.0, true, 36.0);

        Assert.assertEquals(AispectInputQuality.Reason.DRAG_OR_CANCEL, result);
    }

    @Test
    public void rejectsLowRateOrIncompleteSignalWindow() {
        Assert.assertEquals(
                AispectInputQuality.Reason.SAMPLE_RATE_OUT_OF_RANGE,
                AispectInputQuality.signalReason(true, 90.0, 20, 120.0, 320.0, 15)
        );
        Assert.assertEquals(
                AispectInputQuality.Reason.SENSOR_WINDOW_INCOMPLETE,
                AispectInputQuality.signalReason(true, 240.0, 14, 120.0, 320.0, 15)
        );
    }

    @Test
    public void acceptsQualifiedSinglePointerSignal() {
        Assert.assertEquals(
                AispectInputQuality.Reason.NONE,
                AispectInputQuality.gestureReason(1, 36.0, false, 36.0)
        );
        Assert.assertEquals(
                AispectInputQuality.Reason.NONE,
                AispectInputQuality.signalReason(true, 240.0, 20, 120.0, 320.0, 15)
        );
    }

    @Test
    public void preservesManualCollectionWhenReleaseSignalIsIncomplete() {
        Assert.assertFalse(
                AispectInputQuality.shouldRejectReleaseInput(
                        true,
                        AispectInputQuality.Reason.SAMPLE_RATE_OUT_OF_RANGE
                )
        );
        Assert.assertTrue(
                AispectInputQuality.shouldRejectReleaseInput(
                        false,
                        AispectInputQuality.Reason.SAMPLE_RATE_OUT_OF_RANGE
                )
        );
    }

    @Test
    public void preservesManualCollectionWhenGestureIsInvalidForRuntimeRecognition() {
        Assert.assertFalse(
                AispectInputQuality.shouldRejectGestureInput(
                        true,
                        AispectInputQuality.Reason.DRAG_OR_CANCEL
                )
        );
        Assert.assertTrue(
                AispectInputQuality.shouldRejectGestureInput(
                        false,
                        AispectInputQuality.Reason.DRAG_OR_CANCEL
                )
        );
    }

}
