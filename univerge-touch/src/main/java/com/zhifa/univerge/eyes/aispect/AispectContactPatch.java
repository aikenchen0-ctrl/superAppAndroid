package com.zhifa.univerge.eyes.aispect;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public final class AispectContactPatch {
    public static final double DEFAULT_SIGMA_NORM = 0.012;

    public final String source;
    public final boolean valid;
    public final boolean defaultApplied;
    // Proxy values are diagnostic metadata and never replace legacy model inputs.
    public final double confidence;
    public final boolean proxyApplied;
    public final double proxyMajorPx;
    public final double proxyMinorPx;
    public final double xNorm;
    public final double yNorm;
    public final double covariance11;
    public final double covariance12;
    public final double covariance22;
    public final double sigmaXNorm;
    public final double sigmaYNorm;
    public final double orientationRad;
    public final double rawMajorPx;
    public final double rawMinorPx;
    public final double rawOrientationRad;

    private AispectContactPatch(
            String source,
            boolean valid,
            boolean defaultApplied,
            double confidence,
            boolean proxyApplied,
            double proxyMajorPx,
            double proxyMinorPx,
            double xNorm,
            double yNorm,
            double covariance11,
            double covariance12,
            double covariance22,
            double sigmaXNorm,
            double sigmaYNorm,
            double orientationRad,
            double rawMajorPx,
            double rawMinorPx,
            double rawOrientationRad
    ) {
        this.source = source;
        this.valid = valid;
        this.defaultApplied = defaultApplied;
        this.confidence = confidence;
        this.proxyApplied = proxyApplied;
        this.proxyMajorPx = proxyMajorPx;
        this.proxyMinorPx = proxyMinorPx;
        this.xNorm = xNorm;
        this.yNorm = yNorm;
        this.covariance11 = covariance11;
        this.covariance12 = covariance12;
        this.covariance22 = covariance22;
        this.sigmaXNorm = sigmaXNorm;
        this.sigmaYNorm = sigmaYNorm;
        this.orientationRad = orientationRad;
        this.rawMajorPx = rawMajorPx;
        this.rawMinorPx = rawMinorPx;
        this.rawOrientationRad = rawOrientationRad;
    }

    public static AispectContactPatch fromFrame(AispectTouchFrame frame) {
        double major = positiveFinite(frame.touchMajor);
        double minor = positiveFinite(frame.touchMinor);
        String source = "unknown_default";
        boolean valid = false;
        boolean defaultApplied = true;
        double sigmaX = DEFAULT_SIGMA_NORM;
        double sigmaY = DEFAULT_SIGMA_NORM;
        double orientation = 0;

        if (major > 0 && minor > 0) {
            source = "ellipse_major_minor";
            valid = true;
            defaultApplied = false;
            sigmaX = Math.max(major * 0.5, 1.0);
            sigmaY = Math.max(minor * 0.5, 1.0);
            orientation = finite(frame.orientation, 0);
        } else if (major > 0) {
            source = "touch_major_only_synthesized_minor";
            valid = true;
            defaultApplied = false;
            sigmaX = Math.max(major * 0.5, 1.0);
            sigmaY = sigmaX;
        } else {
            double proxyMajor = pressureProxyMajor(frame);
            if (proxyMajor > 0) {
                // Keep the legacy default covariance/raw fields unchanged. The estimate is
                // exposed separately because pressure/size are not contact radii.
                return fromNormalized(
                        "pressure_proxy",
                        false,
                        true,
                        frame.xNorm,
                        frame.yNorm,
                        DEFAULT_SIGMA_NORM,
                        DEFAULT_SIGMA_NORM,
                        0,
                        0,
                        0,
                        0,
                        0,
                        true,
                        proxyMajor,
                        proxyMajor * 0.8
                );
            }
        }

        return fromPixelPatch(
                source,
                valid,
                defaultApplied,
                frame.xNorm,
                frame.yNorm,
                sigmaX,
                sigmaY,
                orientation,
                frame.width,
                frame.height,
                major,
                minor,
                finite(frame.orientation, 0),
                valid ? 1.0 : 0.0,
                false,
                0,
                0
        );
    }

    public static AispectContactPatch fromFrames(List<AispectTouchFrame> frames) {
        if (frames.isEmpty()) {
            return defaultPatch(0, 0);
        }
        double x = 0;
        double y = 0;
        double cov11 = 0;
        double cov12 = 0;
        double cov22 = 0;
        double sigmaX = 0;
        double sigmaY = 0;
        double validCount = 0;
        double defaultCount = 0;
        double proxyCount = 0;
        double proxyMajor = 0;
        double proxyMinor = 0;
        AispectContactPatch last = null;
        for (AispectTouchFrame frame : frames) {
            AispectContactPatch patch = fromFrame(frame);
            last = patch;
            x += patch.xNorm;
            y += patch.yNorm;
            cov11 += patch.covariance11;
            cov12 += patch.covariance12;
            cov22 += patch.covariance22;
            sigmaX += patch.sigmaXNorm;
            sigmaY += patch.sigmaYNorm;
            if (patch.valid) {
                validCount += 1;
            }
            if (patch.defaultApplied) {
                defaultCount += 1;
            }
            if (patch.proxyApplied) {
                proxyCount += 1;
                proxyMajor += patch.proxyMajorPx;
                proxyMinor += patch.proxyMinorPx;
            }
        }
        int count = frames.size();
        String source;
        boolean valid = validCount > 0;
        boolean defaultApplied = defaultCount == count;
        if (validCount == count && last != null) {
            source = last.source;
        } else if (validCount > 0) {
            source = "mixed_contact_patch";
        } else if (proxyCount > 0) {
            source = proxyCount == count ? "pressure_proxy" : "mixed_contact_patch";
        } else {
            source = "unknown_default";
        }
        return new AispectContactPatch(
                source,
                valid,
                defaultApplied,
                valid ? 1.0 : 0.0,
                proxyCount > 0,
                proxyCount > 0 ? proxyMajor / proxyCount : 0,
                proxyCount > 0 ? proxyMinor / proxyCount : 0,
                x / count,
                y / count,
                cov11 / count,
                cov12 / count,
                cov22 / count,
                sigmaX / count,
                sigmaY / count,
                0,
                0,
                0,
                0
        );
    }

    public static AispectContactPatch defaultPatch(double xNorm, double yNorm) {
        return fromNormalized("unknown_default", false, true, xNorm, yNorm, DEFAULT_SIGMA_NORM, DEFAULT_SIGMA_NORM, 0, 0, 0, 0, 0, false, 0, 0);
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("schemaVersion", 1);
        json.put("source", source);
        json.put("valid", valid);
        json.put("defaultApplied", defaultApplied);
        json.put("confidence", confidence);
        json.put("proxyApplied", proxyApplied);
        json.put("x_norm", xNorm);
        json.put("y_norm", yNorm);
        JSONObject covariance = new JSONObject();
        covariance.put("cov_11", covariance11);
        covariance.put("cov_12", covariance12);
        covariance.put("cov_22", covariance22);
        json.put("covariance", covariance);
        JSONObject normalized = new JSONObject();
        normalized.put("sigmaX", sigmaXNorm);
        normalized.put("sigmaY", sigmaYNorm);
        normalized.put("orientationRad", orientationRad);
        json.put("normalized", normalized);
        JSONObject raw = new JSONObject();
        raw.put("available", valid);
        raw.put("majorPx", rawMajorPx);
        raw.put("minorPx", rawMinorPx);
        raw.put("orientationRad", rawOrientationRad);
        json.put("raw", raw);
        JSONObject proxy = new JSONObject();
        proxy.put("available", proxyApplied);
        proxy.put("majorPx", proxyMajorPx);
        proxy.put("minorPx", proxyMinorPx);
        json.put("proxy", proxy);
        return json;
    }

    private static AispectContactPatch fromNormalized(
            String source,
            boolean valid,
            boolean defaultApplied,
            double xNorm,
            double yNorm,
            double sigmaXNorm,
            double sigmaYNorm,
            double orientationRad,
            double rawMajorPx,
            double rawMinorPx,
            double rawOrientationRad,
            double confidence,
            boolean proxyApplied,
            double proxyMajorPx,
            double proxyMinorPx
    ) {
        double cos = Math.cos(orientationRad);
        double sin = Math.sin(orientationRad);
        double varianceX = sigmaXNorm * sigmaXNorm;
        double varianceY = sigmaYNorm * sigmaYNorm;
        double cov11 = cos * cos * varianceX + sin * sin * varianceY;
        double cov22 = sin * sin * varianceX + cos * cos * varianceY;
        double cov12 = sin * cos * (varianceX - varianceY);
        return new AispectContactPatch(source, valid, defaultApplied, confidence, proxyApplied, proxyMajorPx, proxyMinorPx, clampUnit(xNorm), clampUnit(yNorm), cov11, cov12, cov22, sigmaXNorm, sigmaYNorm, orientationRad, rawMajorPx, rawMinorPx, rawOrientationRad);
    }

    private static AispectContactPatch fromPixelPatch(
            String source,
            boolean valid,
            boolean defaultApplied,
            double xNorm,
            double yNorm,
            double sigmaXPx,
            double sigmaYPx,
            double orientationRad,
            int width,
            int height,
            double rawMajorPx,
            double rawMinorPx,
            double rawOrientationRad,
            double confidence,
            boolean proxyApplied,
            double proxyMajorPx,
            double proxyMinorPx
    ) {
        double cos = Math.cos(orientationRad);
        double sin = Math.sin(orientationRad);
        double varianceX = sigmaXPx * sigmaXPx;
        double varianceY = sigmaYPx * sigmaYPx;
        double covPx11 = cos * cos * varianceX + sin * sin * varianceY;
        double covPx22 = sin * sin * varianceX + cos * cos * varianceY;
        double covPx12 = sin * cos * (varianceX - varianceY);
        double safeWidth = Math.max(1, width);
        double safeHeight = Math.max(1, height);
        double cov11 = covPx11 / (safeWidth * safeWidth);
        double cov12 = covPx12 / (safeWidth * safeHeight);
        double cov22 = covPx22 / (safeHeight * safeHeight);
        double sigmaXNorm = sigmaXPx / safeWidth;
        double sigmaYNorm = sigmaYPx / safeHeight;
        return new AispectContactPatch(source, valid, defaultApplied, confidence, proxyApplied, proxyMajorPx, proxyMinorPx, clampUnit(xNorm), clampUnit(yNorm), cov11, cov12, cov22, sigmaXNorm, sigmaYNorm, orientationRad, rawMajorPx, rawMinorPx, rawOrientationRad);
    }

    private static double positiveFinite(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            return 0;
        }
        return value;
    }

    private static double pressureProxyMajor(AispectTouchFrame frame) {
        double pressure = positiveFinite(frame.pressure);
        double size = positiveFinite(frame.size);
        if (pressure <= 0 && size <= 0) {
            return 0;
        }
        // Pressure/size are not contact radii. Keep this estimate separate and low confidence.
        double signal = Math.max(Math.min(pressure, 1.0), Math.min(size, 1.0));
        double shortSide = Math.min(Math.max(1, frame.width), Math.max(1, frame.height));
        return Math.max(1.0, shortSide * (0.01 + 0.01 * signal));
    }

    private static double finite(double value, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return value;
    }

    private static double clampUnit(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }
}
