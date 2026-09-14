package com.blinkvoice.visual.api.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Matches predicted and annotated events for deterministic offline replay evaluation.
 * Each ground-truth event and prediction can participate in at most one match.
 */
public final class BlinkEventEvaluator {
    private static final Comparator<BlinkEvent> BY_START_TIME =
            Comparator.comparingLong(BlinkEvent::getStartTimeMs);

    private BlinkEventEvaluator() {
    }

    public static BlinkEvaluationMetrics evaluate(
            List<BlinkEvent> expectedEvents,
            List<BlinkEvent> predictedEvents,
            long startToleranceMs
    ) {
        if (expectedEvents == null || predictedEvents == null) {
            throw new IllegalArgumentException("event lists must not be null");
        }
        if (startToleranceMs < 0L) {
            throw new IllegalArgumentException("startToleranceMs must be non-negative");
        }

        MatchAccumulator accumulator = new MatchAccumulator();
        match(expectedEvents, predictedEvents, startToleranceMs, accumulator);
        return accumulator.toMetrics();
    }

    /** Evaluates already grouped events while preserving the case boundary. */
    static BlinkEvaluationMetrics evaluateGrouped(
            Map<String, List<BlinkEvent>> expectedByCase,
            Map<String, List<BlinkEvent>> predictedByCase,
            long startToleranceMs
    ) {
        if (expectedByCase == null || predictedByCase == null) {
            throw new IllegalArgumentException("case maps must not be null");
        }
        if (startToleranceMs < 0L) {
            throw new IllegalArgumentException("startToleranceMs must be non-negative");
        }
        MatchAccumulator accumulator = new MatchAccumulator();
        Set<String> caseIds = new LinkedHashSet<>();
        caseIds.addAll(expectedByCase.keySet());
        caseIds.addAll(predictedByCase.keySet());
        for (String caseId : caseIds) {
            match(
                    expectedByCase.getOrDefault(caseId, Collections.emptyList()),
                    predictedByCase.getOrDefault(caseId, Collections.emptyList()),
                    startToleranceMs,
                    accumulator
            );
        }
        return accumulator.toMetrics();
    }

    private static void match(
            List<BlinkEvent> expectedEvents,
            List<BlinkEvent> predictedEvents,
            long startToleranceMs,
            MatchAccumulator accumulator
    ) {
        if (expectedEvents == null || predictedEvents == null) {
            throw new IllegalArgumentException("event lists must not be null");
        }
        Map<BlinkEventType, List<BlinkEvent>> expectedByType = byType(sortedCopy(expectedEvents));
        Map<BlinkEventType, List<BlinkEvent>> predictedByType = byType(sortedCopy(predictedEvents));
        Set<BlinkEventType> types = EnumSet.noneOf(BlinkEventType.class);
        types.addAll(expectedByType.keySet());
        types.addAll(predictedByType.keySet());
        for (BlinkEventType type : types) {
            List<BlinkEvent> expected = expectedByType.getOrDefault(type, Collections.emptyList());
            List<BlinkEvent> predicted = predictedByType.getOrDefault(type, Collections.emptyList());
            MatchPlan plan = optimalPlan(expected, predicted, startToleranceMs);
            accumulator.truePositiveCount += plan.matchedCount;
            accumulator.falsePositiveCount += predicted.size() - plan.matchedCount;
            accumulator.falseNegativeCount += expected.size() - plan.matchedCount;
            accumulator.latencies.addAll(plan.latencies);
        }
    }

    private static Map<BlinkEventType, List<BlinkEvent>> byType(List<BlinkEvent> events) {
        Map<BlinkEventType, List<BlinkEvent>> byType = new EnumMap<>(BlinkEventType.class);
        for (BlinkEvent event : events) {
            byType.computeIfAbsent(event.getType(), ignored -> new ArrayList<>()).add(event);
        }
        return byType;
    }

    /** Dynamic programming gives maximum cardinality, then minimum total absolute timing error. */
    private static MatchPlan optimalPlan(
            List<BlinkEvent> expected,
            List<BlinkEvent> predicted,
            long toleranceMs
    ) {
        int expectedCount = expected.size();
        int predictedCount = predicted.size();
        int[][] matches = new int[expectedCount + 1][predictedCount + 1];
        double[][] costs = new double[expectedCount + 1][predictedCount + 1];
        byte[][] choices = new byte[expectedCount + 1][predictedCount + 1];

        for (int i = expectedCount - 1; i >= 0; i--) {
            for (int j = predictedCount - 1; j >= 0; j--) {
                int bestMatches = matches[i + 1][j];
                double bestCost = costs[i + 1][j];
                byte bestChoice = 1;

                if (isBetter(matches[i][j + 1], costs[i][j + 1], bestMatches, bestCost)) {
                    bestMatches = matches[i][j + 1];
                    bestCost = costs[i][j + 1];
                    bestChoice = 2;
                }

                long distance = Math.abs(
                        predicted.get(j).getStartTimeMs() - expected.get(i).getStartTimeMs()
                );
                if (distance <= toleranceMs) {
                    int matchCandidate = 1 + matches[i + 1][j + 1];
                    double costCandidate = distance + costs[i + 1][j + 1];
                    if (isBetter(matchCandidate, costCandidate, bestMatches, bestCost)
                            || (matchCandidate == bestMatches && costCandidate == bestCost)) {
                        bestMatches = matchCandidate;
                        bestCost = costCandidate;
                        bestChoice = 3;
                    }
                }

                matches[i][j] = bestMatches;
                costs[i][j] = bestCost;
                choices[i][j] = bestChoice;
            }
        }

        List<Long> latencies = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < expectedCount && j < predictedCount) {
            byte choice = choices[i][j];
            if (choice == 3) {
                latencies.add(predicted.get(j).getStartTimeMs()
                        - expected.get(i).getStartTimeMs());
                i++;
                j++;
            } else if (choice == 1) {
                i++;
            } else {
                j++;
            }
        }
        return new MatchPlan(matches[0][0], latencies);
    }

    private static boolean isBetter(
            int candidateMatches,
            double candidateCost,
            int currentMatches,
            double currentCost
    ) {
        return candidateMatches > currentMatches
                || (candidateMatches == currentMatches && candidateCost < currentCost);
    }

    private static final class MatchPlan {
        final int matchedCount;
        final List<Long> latencies;

        MatchPlan(int matchedCount, List<Long> latencies) {
            this.matchedCount = matchedCount;
            this.latencies = latencies;
        }
    }

    private static final class MatchAccumulator {
        int truePositiveCount;
        int falsePositiveCount;
        int falseNegativeCount;
        final List<Long> latencies = new ArrayList<>();

        BlinkEvaluationMetrics toMetrics() {
            double precision = ratioOrOne(truePositiveCount, truePositiveCount + falsePositiveCount);
            double recall = ratioOrOne(truePositiveCount, truePositiveCount + falseNegativeCount);
            double f1 = precision + recall == 0d
                    ? 0d
                    : 2d * precision * recall / (precision + recall);
            double falsePositiveRate = ratioOrZero(falsePositiveCount,
                    truePositiveCount + falsePositiveCount);
            double missRate = ratioOrZero(falseNegativeCount,
                    truePositiveCount + falseNegativeCount);
            return new BlinkEvaluationMetrics(
                    truePositiveCount,
                    falsePositiveCount,
                    falseNegativeCount,
                    precision,
                    recall,
                    f1,
                    falsePositiveRate,
                    missRate,
                    meanLatency(latencies),
                    p95AbsoluteLatency(latencies)
            );
        }
    }

    private static List<BlinkEvent> sortedCopy(List<BlinkEvent> events) {
        List<BlinkEvent> copy = new ArrayList<>(events.size());
        for (BlinkEvent event : events) {
            copy.add(Objects.requireNonNull(event, "event list contains null"));
        }
        Collections.sort(copy, BY_START_TIME);
        return copy;
    }

    private static double ratioOrOne(int numerator, int denominator) {
        return denominator == 0 ? 1d : (double) numerator / denominator;
    }

    private static double ratioOrZero(int numerator, int denominator) {
        return denominator == 0 ? 0d : (double) numerator / denominator;
    }

    private static double meanLatency(List<Long> latencies) {
        if (latencies.isEmpty()) {
            return 0d;
        }
        long total = 0L;
        for (long latency : latencies) {
            total += latency;
        }
        return (double) total / latencies.size();
    }

    private static double p95AbsoluteLatency(List<Long> latencies) {
        if (latencies.isEmpty()) {
            return 0d;
        }
        List<Long> absolute = new ArrayList<>(latencies.size());
        for (long latency : latencies) {
            absolute.add(Math.abs(latency));
        }
        Collections.sort(absolute);
        int index = (int) Math.ceil(absolute.size() * 0.95d) - 1;
        return absolute.get(Math.max(0, Math.min(index, absolute.size() - 1)));
    }
}
