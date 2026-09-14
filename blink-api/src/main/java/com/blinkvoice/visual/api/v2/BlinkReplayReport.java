package com.blinkvoice.visual.api.v2;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.nio.file.Path;

/** Per-case and overall results for one expected/predicted replay pair. */
public final class BlinkReplayReport {
    private final Map<String, BlinkEvaluationMetrics> caseMetrics;
    private final BlinkEvaluationMetrics overall;

    private BlinkReplayReport(
            Map<String, BlinkEvaluationMetrics> caseMetrics,
            BlinkEvaluationMetrics overall
    ) {
        this.caseMetrics = Collections.unmodifiableMap(new LinkedHashMap<>(caseMetrics));
        this.overall = overall;
    }

    public static BlinkReplayReport evaluate(
            Path expectedCsv,
            Path predictedCsv,
            long startToleranceMs
    ) throws IOException {
        Map<String, List<BlinkEvent>> expected = BlinkReplayCsv.groupByCase(
                BlinkReplayCsv.read(expectedCsv)
        );
        Map<String, List<BlinkEvent>> predicted = BlinkReplayCsv.groupByCase(
                BlinkReplayCsv.read(predictedCsv)
        );
        return evaluateGrouped(expected, predicted, startToleranceMs);
    }

    public static BlinkReplayReport evaluate(
            List<BlinkReplayEvent> expectedEvents,
            List<BlinkReplayEvent> predictedEvents,
            long startToleranceMs
    ) {
        return evaluateGrouped(
                BlinkReplayCsv.groupByCase(expectedEvents),
                BlinkReplayCsv.groupByCase(predictedEvents),
                startToleranceMs
        );
    }

    public static BlinkReplayReport evaluate(
            Reader expectedCsv,
            Reader predictedCsv,
            long startToleranceMs
    ) throws IOException {
        Map<String, List<BlinkEvent>> expected = BlinkReplayCsv.groupByCase(
                BlinkReplayCsv.read(expectedCsv)
        );
        Map<String, List<BlinkEvent>> predicted = BlinkReplayCsv.groupByCase(
                BlinkReplayCsv.read(predictedCsv)
        );

        return evaluateGrouped(expected, predicted, startToleranceMs);
    }

    private static BlinkReplayReport evaluateGrouped(
            Map<String, List<BlinkEvent>> expected,
            Map<String, List<BlinkEvent>> predicted,
            long startToleranceMs
    ) {
        Set<String> caseIds = new LinkedHashSet<>();
        caseIds.addAll(expected.keySet());
        caseIds.addAll(predicted.keySet());

        Map<String, BlinkEvaluationMetrics> metricsByCase = new LinkedHashMap<>();
        for (String caseId : caseIds) {
            List<BlinkEvent> expectedEvents = expected.getOrDefault(caseId, Collections.emptyList());
            List<BlinkEvent> predictedEvents = predicted.getOrDefault(caseId, Collections.emptyList());
            metricsByCase.put(caseId, BlinkEventEvaluator.evaluate(
                    expectedEvents,
                    predictedEvents,
                    startToleranceMs
            ));
        }

        return new BlinkReplayReport(
                metricsByCase,
                BlinkEventEvaluator.evaluateGrouped(expected, predicted, startToleranceMs)
        );
    }

    public Map<String, BlinkEvaluationMetrics> getCaseMetrics() {
        return caseMetrics;
    }

    public BlinkEvaluationMetrics getOverall() {
        return overall;
    }

    /** Stable machine-readable JSON without external runtime dependencies. */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"caseCount\":").append(caseMetrics.size());
        json.append(",\"overall\":").append(metricsJson(overall));
        json.append(",\"cases\":{");
        boolean first = true;
        for (Map.Entry<String, BlinkEvaluationMetrics> entry : caseMetrics.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":")
                    .append(metricsJson(entry.getValue()));
        }
        json.append("}}");
        return json.toString();
    }

    private static String metricsJson(BlinkEvaluationMetrics metrics) {
        return String.format(
                Locale.US,
                "{\"truePositiveCount\":%d,\"falsePositiveCount\":%d,\"falseNegativeCount\":%d,"
                        + "\"precision\":%.6f,\"recall\":%.6f,\"f1\":%.6f,"
                        + "\"falsePositiveRate\":%.6f,\"missRate\":%.6f,"
                        + "\"meanLatencyMs\":%.3f,\"p95AbsoluteLatencyMs\":%.3f}",
                metrics.getTruePositiveCount(),
                metrics.getFalsePositiveCount(),
                metrics.getFalseNegativeCount(),
                metrics.getPrecision(),
                metrics.getRecall(),
                metrics.getF1(),
                metrics.getFalsePositiveRate(),
                metrics.getMissRate(),
                metrics.getMeanLatencyMs(),
                metrics.getP95AbsoluteLatencyMs()
        );
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
