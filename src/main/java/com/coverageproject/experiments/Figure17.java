package com.coverageproject.experiments;

import com.coverageproject.data.DatasetBundle;
import com.coverageproject.enhancement.Greedy;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Figure 17: coverage-enhancement runtime vs number of attributes. At a fixed τ, project the
 * dataset down to {@code d} attributes for {@code d} sweeping from {@code minDimensions} up to the
 * full attribute count, and run GREEDY at the configured levels.
 */
public final class Figure17 {

    public record Row(int dimensionCount, int tau, int level, double runtimeSeconds, int numAddedRows) {}

    private Figure17() {
        // utility class
    }

    public static List<Row> run(DatasetBundle bundle, boolean verbose) {
        return run(bundle, null, null, 2, verbose);
    }

    public static List<Row> run(
            DatasetBundle bundle,
            Integer tauArg,
            List<Integer> levelsArg,
            int minDimensions,
            boolean verbose) {
        int tau;
        if (tauArg != null) {
            tau = tauArg;
        } else {
            JsonNode configuredTau = bundle.config().figure17().get("tau");
            tau = configuredTau != null ? configuredTau.asInt() : 5;
        }
        List<Integer> levels;
        if (levelsArg != null && !levelsArg.isEmpty()) {
            levels = levelsArg;
        } else {
            JsonNode configuredLevels = bundle.config().figure17().get("levels");
            if (configuredLevels != null && configuredLevels.isArray() && configuredLevels.size() > 0) {
                List<Integer> values = new ArrayList<>();
                configuredLevels.forEach(n -> values.add(n.asInt()));
                levels = values;
            } else {
                levels = List.of(2, 3);
            }
        }

        List<Row> rows = new ArrayList<>();
        List<Integer> dimensionValues = new ArrayList<>();
        for (int d = minDimensions; d <= bundle.d(); d++) {
            dimensionValues.add(d);
        }
        log(verbose, "  [Figure 17] Running dimension experiment for dimensions "
                + dimensionValues + ", tau=" + tau + ".");
        for (int idx = 0; idx < dimensionValues.size(); idx++) {
            int d = dimensionValues.get(idx);
            List<List<String>> datasetD = new ArrayList<>(bundle.dataset().size());
            for (List<String> row : bundle.dataset()) {
                datasetD.add(List.copyOf(row.subList(0, d)));
            }
            List<List<String>> domainsD = bundle.domains().subList(0, d);

            log(verbose, "  [Figure 17] dimensions=" + d + " (" + (idx + 1) + "/" + dimensionValues.size() + ")");
            for (int rawLevel : levels) {
                int level = Math.min(rawLevel, d);
                log(verbose, "    - Greedy enhancement, level=" + level + " started...");
                long start = System.nanoTime();
                List<List<String>> added = Greedy.greedyCoverageEnhancement(datasetD, domainsD, tau, level);
                double elapsed = (System.nanoTime() - start) / 1e9;
                log(verbose, "    - level=" + level + " finished in "
                        + String.format("%.3f", elapsed) + "s; added_rows=" + added.size());
                rows.add(new Row(d, tau, level, elapsed, added.size()));
            }
        }
        return rows;
    }

    private static void log(boolean verbose, String message) {
        if (verbose) {
            System.out.println(message);
        }
    }
}
