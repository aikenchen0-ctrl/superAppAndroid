package com.blinkvoice.visual.api.v2;

/** Immutable event-level replay metrics. */
public final class BlinkEvaluationMetrics {
    private final int truePositiveCount;
    private final int falsePositiveCount;
    private final int falseNegativeCount;
    private final double precision;
    private final double recall;
    private final double f1;
    private final double falsePositiveRate;
    private final double missRate;
    private final double meanLatencyMs;
    private final double p95AbsoluteLatencyMs;

    BlinkEvaluationMetrics(
            int truePositiveCount,
            int falsePositiveCount,
            int falseNegativeCount,
            double precision,
            double recall,
            double f1,
            double falsePositiveRate,
            double missRate,
            double meanLatencyMs,
            double p95AbsoluteLatencyMs
    ) {
        this.truePositiveCount = truePositiveCount;
        this.falsePositiveCount = falsePositiveCount;
        this.falseNegativeCount = falseNegativeCount;
        this.precision = precision;
        this.recall = recall;
        this.f1 = f1;
        this.falsePositiveRate = falsePositiveRate;
        this.missRate = missRate;
        this.meanLatencyMs = meanLatencyMs;
        this.p95AbsoluteLatencyMs = p95AbsoluteLatencyMs;
    }

    public int getTruePositiveCount() {
        return truePositiveCount;
    }

    public int getFalsePositiveCount() {
        return falsePositiveCount;
    }

    public int getFalseNegativeCount() {
        return falseNegativeCount;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getF1() {
        return f1;
    }

    /** False triggers divided by all predicted events. */
    public double getFalsePositiveRate() {
        return falsePositiveRate;
    }

    /** Missed ground-truth events divided by all ground-truth events. */
    public double getMissRate() {
        return missRate;
    }

    /** Signed mean of predicted start minus expected start for matched events. */
    public double getMeanLatencyMs() {
        return meanLatencyMs;
    }

    /** 95th percentile of absolute start-time error for matched events. */
    public double getP95AbsoluteLatencyMs() {
        return p95AbsoluteLatencyMs;
    }
}
