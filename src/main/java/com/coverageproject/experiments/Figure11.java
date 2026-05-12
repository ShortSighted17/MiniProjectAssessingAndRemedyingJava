package com.coverageproject.experiments;

import com.coverageproject.core.Pattern;
import com.coverageproject.data.DatasetBundle;
import com.coverageproject.mups.DeepDiver;
import com.coverageproject.mups.PatternBreaker;
import com.coverageproject.mups.PatternCombiner;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Figure 11: MUP identification runtime vs threshold τ. Runs PATTERN-BREAKER, PATTERN-COMBINER,
 * and DEEPDIVER over a configurable list of τ values and records each algorithm's wall-clock
 * runtime and MUP count.
 */
public final class Figure11 {

    public record Row(int tau, double thresholdRate, String algorithm, double runtimeSeconds, int mupCount) {}

    private Figure11() {
        // utility class
    }

    public static List<Row> run(DatasetBundle bundle, boolean verbose) {
        return run(bundle, defaultTauValues(bundle), verbose);
    }

    public static List<Row> run(DatasetBundle bundle, List<Integer> tauValues, boolean verbose) {
        record AlgorithmEntry(
                String name,
                Function<int[], Set<Pattern>> runner) {}

        // We pin the dataset+domains here so the lambda only varies over τ.
        Function<Integer, Set<Pattern>> patternBreaker = tau ->
                PatternBreaker.patternBreaker(bundle.dataset(), bundle.domains(), tau);
        Function<Integer, Set<Pattern>> patternCombiner = tau ->
                PatternCombiner.patternCombiner(bundle.dataset(), bundle.domains(), tau);
        Function<Integer, Set<Pattern>> deepDiver = tau ->
                DeepDiver.deepdiver(bundle.dataset(), bundle.domains(), tau);

        List<AlgorithmEntry> algorithms = List.of(
                new AlgorithmEntry("PATTERN-BREAKER", t -> patternBreaker.apply(t[0])),
                new AlgorithmEntry("PATTERN-COMBINER", t -> patternCombiner.apply(t[0])),
                new AlgorithmEntry("DEEPDIVER", t -> deepDiver.apply(t[0])));

        List<Row> rows = new ArrayList<>();
        log(verbose, "  [Figure 11] Running " + tauValues.size() + " tau settings across "
                + algorithms.size() + " algorithms.");
        for (int i = 0; i < tauValues.size(); i++) {
            int tau = tauValues.get(i);
            log(verbose, "  [Figure 11] tau=" + tau + " (" + (i + 1) + "/" + tauValues.size() + ")");
            for (AlgorithmEntry entry : algorithms) {
                log(verbose, "    - " + entry.name() + " started...");
                long start = System.nanoTime();
                Set<Pattern> mups = entry.runner().apply(new int[]{tau});
                double elapsed = (System.nanoTime() - start) / 1e9;
                log(verbose, "    - " + entry.name() + " finished in "
                        + String.format("%.3f", elapsed) + "s; MUPs=" + mups.size());
                rows.add(new Row(tau, (double) tau / bundle.n(), entry.name(), elapsed, mups.size()));
            }
        }
        return rows;
    }

    private static List<Integer> defaultTauValues(DatasetBundle bundle) {
        JsonNode configured = bundle.config().figure11().get("tau_values");
        if (configured == null || !configured.isArray() || configured.size() == 0) {
            return List.of(1, 2, 5, 10, 20, 30);
        }
        List<Integer> result = new ArrayList<>();
        configured.forEach(n -> result.add(n.asInt()));
        return result;
    }

    private static void log(boolean verbose, String message) {
        if (verbose) {
            System.out.println(message);
        }
    }
}
