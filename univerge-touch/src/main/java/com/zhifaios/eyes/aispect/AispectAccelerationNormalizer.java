package com.zhifaios.eyes.aispect;

final class AispectAccelerationNormalizer {
    static final class Sample {
        final boolean reliable;
        final double x;
        final double y;
        final double z;

        Sample(boolean reliable, double x, double y, double z) {
            this.reliable = reliable;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private AispectAccelerationNormalizer() {
    }

    static Sample normalize(
            boolean isLinearAcceleration,
            boolean hasGravitySample,
            double rawX,
            double rawY,
            double rawZ,
            double gravityX,
            double gravityY,
            double gravityZ
    ) {
        if (isLinearAcceleration) {
            return new Sample(true, rawX, rawY, rawZ);
        }
        if (!hasGravitySample) {
            return new Sample(false, 0, 0, 0);
        }
        return new Sample(
                true,
                rawX - gravityX,
                rawY - gravityY,
                rawZ - gravityZ
        );
    }
}
