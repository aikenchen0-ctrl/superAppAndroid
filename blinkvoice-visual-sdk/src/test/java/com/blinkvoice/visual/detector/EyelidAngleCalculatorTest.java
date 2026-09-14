package com.blinkvoice.visual.detector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EyelidAngleCalculatorTest {
    @Test
    public void returnsSmallerAngleWhenEyelidsAreClosed() {
        EyelidAngleCalculator.Point3[] openUpper = new EyelidAngleCalculator.Point3[]{
                new EyelidAngleCalculator.Point3(-1f, 0f, 0f),
                new EyelidAngleCalculator.Point3(1f, 0f, 0f),
                new EyelidAngleCalculator.Point3(0f, 1f, 1f)
        };
        EyelidAngleCalculator.Point3[] openLower = new EyelidAngleCalculator.Point3[]{
                new EyelidAngleCalculator.Point3(-1f, 0f, 0f),
                new EyelidAngleCalculator.Point3(1f, 0f, 0f),
                new EyelidAngleCalculator.Point3(0f, -1f, 1f)
        };
        EyelidAngleCalculator.Point3[] closedUpper = new EyelidAngleCalculator.Point3[]{
                new EyelidAngleCalculator.Point3(-1f, 0f, 0f),
                new EyelidAngleCalculator.Point3(1f, 0f, 0f),
                new EyelidAngleCalculator.Point3(0f, 0.1f, 1f)
        };
        EyelidAngleCalculator.Point3[] closedLower = new EyelidAngleCalculator.Point3[]{
                new EyelidAngleCalculator.Point3(-1f, 0f, 0f),
                new EyelidAngleCalculator.Point3(1f, 0f, 0f),
                new EyelidAngleCalculator.Point3(0f, -0.1f, 1f)
        };

        float openAngle = EyelidAngleCalculator.computeAngleDegrees(openUpper, openLower);
        float closedAngle = EyelidAngleCalculator.computeAngleDegrees(closedUpper, closedLower);

        assertTrue(openAngle > closedAngle);
        assertEquals(90f, openAngle, 0.001f);
        assertEquals(11.421f, closedAngle, 0.001f);
    }

    @Test
    public void returnsZeroForDegeneratePlane() {
        EyelidAngleCalculator.Point3[] flat = new EyelidAngleCalculator.Point3[]{
                new EyelidAngleCalculator.Point3(0f, 0f, 0f),
                new EyelidAngleCalculator.Point3(0f, 0f, 0f),
                new EyelidAngleCalculator.Point3(0f, 0f, 0f)
        };

        assertEquals(0f, EyelidAngleCalculator.computeAngleDegrees(flat, flat), 0.001f);
    }
}
