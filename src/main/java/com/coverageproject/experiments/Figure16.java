package com.coverageproject.experiments;

import com.coverageproject.data.DatasetBundle;
import com.coverageproject.enhancement.Greedy;
import com.coverageproject.enhancement.Naive;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Figure 16: coverage-enhancement runtime vs threshold τ. Runs the NAIVE baseline at one level and
 * GREEDY at one or more levels for each configured τ.
 */
public final class Figure16 {

    public record Row(
            int tau,
            double thresholdRate,
            String method,
            int level,
            double runtimeSeconds,
            int numAddedRows) {}

    private Figure16() {
        // utility class
    }

    public static List<Row> run(DatasetBundle bundle, boolean verbose) {
        return run(bundle, null, null, null, verbose);
    }

    public static List<Row> run(
            DatasetBundle bundle,
            List<Integer> tauValuesArg,
            List<Integer> levelsArg,
            Integer naiveLevelArg,
            boolean verbose) {
        List<Integer> tauValues = orDefault(tauValuesArg,
                bundle.config().figure16().get("tau_values"),
                List.of(1, 2, 5, 10));
        List<Integer> levels = orDefault(levelsArg,
                bundle.config().figure16().get("levels"),
                List.of(2, 3));
        int naiveLevel = naiveLevelArg != null
                ? naiveLevelArg
                : (bundle.config().figure16().get("naive_level") != null
                        ? bundle.config().figure16().get("naive_level").asInt()
                        : levels.stream().min(Integer::compare).orElseThrow());

        List<Row> rows = new ArrayList<>();
        log(verbose, "  [Figure 16] Running threshold experiment for tau values: " + tauValues);
        for (int t = 0; t < tauValues.size(); t++) {
            int tau = tauValues.get(t);
            log(verbose, "  [Figure 16] tau=" + tau + " (" + (t + 1) + "/" + tauValues.size() + ")");

            log(verbose, "    - Naive baseline, level=" + naiveLevel + " started...");
            long start = System.nanoTime();
            List<List<String>> added =
                    Naive.naiveCoverageEnhancement(bundle.dataset(), bundle.domains(), tau, naiveLevel);
            double elapsed = (System.nanoTime() - start) / 1e9;
            log(verbose, "    - Naive baseline finished in " + String.format("%.3f", elapsed)
                    + "s; added_rows=" + added.size());
            rows.add(new Row(tau, (double) tau / bundle.n(),
                    "Naive (l=" + naiveLevel + ")", naiveLevel, elapsed, added.size()));

            for (int level : levels) {
                log(verbose, "    - Greedy enhancement, level=" + level + " started...");
                start = System.nanoTime();
                added = Greedy.greedyCoverageEnhancement(
                        bundle.dataset(), bundle.domains(), tau, level);
                elapsed = (System.nanoTime() - start) / 1e9;
                log(verbose, "    - Greedy level=" + level + " finished in "
                        + String.format("%.3f", elapsed) + "s; added_rows=" + added.size());
                rows.add(new Row(tau, (double) tau / bundle.n(),
                        "Greedy (l=" + level + ")", level, elapsed, added.size()));
            }
        }
        return rows;
    }

    private static List<Integer> orDefault(List<Integer> argument, JsonNode configured, List<Integer> fallback) {
        if (argument != null && !argument.isEmpty()) {
            return argument;
        }
        if (configured != null && configured.isArray() && configured.size() > 0) {
            List<Integer> values = new ArrayList<>();
            configured.forEach(n -> values.add(n.asInt()));
            return values;
        }
        return fallback;
    }

    private static void log(boolean verbose, String message) {
        if (verbose) {
            System.out.println(message);
        }
    }
}
