package com.blinkvoice.visual.detector;

public final class EyelidAngleCalculator {
    private static final float EPSILON = 1e-6f;

    private EyelidAngleCalculator() {
    }

    public static final class Point3 {
        public final float x;
        public final float y;
        public final float z;

        public Point3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static float computeAngleDegrees(Point3[] upperEyelid, Point3[] lowerEyelid) {
        Vector3 upperNormal = computeNormal(upperEyelid);
        Vector3 lowerNormal = computeNormal(lowerEyelid);
        float upperLength = upperNormal.length();
        float lowerLength = lowerNormal.length();
        if (upperLength < EPSILON || lowerLength < EPSILON) {
            return 0f;
        }

        float cosine = upperNormal.dot(lowerNormal) / (upperLength * lowerLength);
        cosine = Math.max(-1f, Math.min(1f, cosine));
        float angle = (float) Math.toDegrees(Math.acos(cosine));
        return angle > 90f ? 180f - angle : angle;
    }

    private static Vector3 computeNormal(Point3[] points) {
        if (points == null || points.length < 3) {
            return Vector3.ZERO;
        }

        Point3 origin = points[0];
        for (int i = 1; i < points.length - 1; i++) {
            Vector3 first = Vector3.between(origin, points[i]);
            for (int j = i + 1; j < points.length; j++) {
                Vector3 second = Vector3.between(origin, points[j]);
                Vector3 normal = first.cross(second);
                if (normal.length() >= EPSILON) {
                    return normal;
                }
            }
        }
        return Vector3.ZERO;
    }

    private static final class Vector3 {
        static final Vector3 ZERO = new Vector3(0f, 0f, 0f);

        final float x;
        final float y;
        final float z;

        Vector3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static Vector3 between(Point3 from, Point3 to) {
            return new Vector3(to.x - from.x, to.y - from.y, to.z - from.z);
        }

        Vector3 cross(Vector3 other) {
            return new Vector3(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        float dot(Vector3 other) {
            return x * other.x + y * other.y + z * other.z;
        }

        float length() {
            return (float) Math.sqrt(x * x + y * y + z * z);
        }
    }
}
