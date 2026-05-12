package com.coverageproject.experiments;

import com.coverageproject.core.Coverage;
import com.coverageproject.core.Pattern;
import com.coverageproject.data.DatasetBundle;
import com.coverageproject.ml.DecisionTree;
import com.coverageproject.ml.Metrics;
import com.coverageproject.mups.DeepDiver;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Figure 10: effect of under-representation on classifier accuracy.
 *
 * <p>The experiment finds MUPs via DEEPDIVER, picks a few candidate subgroup patterns, and for
 * each one trains a {@link DecisionTree} on the dataset with the subgroup removed, then measures
 * accuracy as subgroup rows are gradually added back to the training set. The pattern with the
 * lowest "omitted-subgroup F1" is the most affected by lack of coverage; those are the
 * subgroups that produce the most striking Figure 10 plots.
 *
 * <p>Mirrors {@code figure_10.py} from the Python reference. The Java port substitutes our
 * categorical decision tree for sklearn's, and deterministic shuffles for sklearn's
 * {@code random_state}-controlled samples.
 */
public final class Figure10 {

    /** One row of the add-back experiment for a single chosen MUP. */
    public record Row(int subgroupTrainSize, double overallAccuracy, double subgroupAccuracy, double subgroupF1) {}

    /** Per-run metadata accompanying the {@link Row} series for one chosen MUP. */
    public record Metadata(
            Pattern chosenMup,
            String chosenMupString,
            Map<String, String> chosenMupNamed,
            int subgroupRows,
            int subgroupTestSize,
            List<Integer> trainSizes,
            int tau,
            int rank,
            double selectionBaselineOverallAccuracy,
            double selectionBaselineSubgroupAccuracy,
            double selectionBaselineSubgroupF1) {}

    public record Run(List<Row> rows, Metadata metadata) {}

    /** Output of {@link #run}: per-MUP runs and a summary table across screened candidates. */
    public record RunResult(List<Run> runs, List<SummaryRow> summary) {}

    /** One row of the screening summary CSV. */
    public record SummaryRow(
            int rank,
            String pattern,
            int level,
            int coverage,
            double baselineOverallAccuracy,
            double baselineSubgroupAccuracy,
            double baselineSubgroupF1,
            Map<String, String> patternNamed) {}

    private Figure10() {
        // utility class
    }

    public static RunResult run(DatasetBundle bundle, boolean verbose) {
        return run(bundle, null, 20, 42L, verbose);
    }

    public static RunResult run(
            DatasetBundle bundle, Integer tauArg, int subgroupTestSize, long randomSeed, boolean verbose) {
        Map<String, JsonNode> fig = bundle.config().figure10();
        int tau = tauArg != null ? tauArg
                : (fig.get("tau") != null ? fig.get("tau").asInt() : 30);
        int maxCandidateMups = fig.get("max_candidate_mups") != null
                ? fig.get("max_candidate_mups").asInt() : 100;
        int numSubgroupsToPlot = fig.get("num_subgroups_to_plot") != null
                ? fig.get("num_subgroups_to_plot").asInt() : 5;
        int minRowsNeeded = fig.get("min_subgroup_rows") != null
                ? fig.get("min_subgroup_rows").asInt()
                : minimumRowsNeeded(bundle, subgroupTestSize);

        log(verbose, "  [Figure 10] Finding MUPs with DeepDiver (tau=" + tau + ")...");
        Set<Pattern> mups = DeepDiver.deepdiver(bundle.dataset(), bundle.domains(), tau);
        log(verbose, "  [Figure 10] Found " + mups.size() + " MUPs.");
        if (mups.isEmpty()) {
            throw new IllegalStateException(
                    "No MUPs found for Figure 10. Try increasing tau in config.json.");
        }

        record CandidateInfo(Pattern pattern, int coverage, int level, String asString) {}

        List<CandidateInfo> candidates = new ArrayList<>();
        for (Pattern mup : mups) {
            int cov = Coverage.coverage(mup, bundle.dataset());
            if (cov >= minRowsNeeded) {
                candidates.add(new CandidateInfo(mup, cov, mup.level(), mup.asString()));
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No Figure 10 MUP has enough rows for the requested test/train sizes. "
                            + "Lower subgroup_test_size or subgroup_train_sizes in config.json.");
        }

        // Sort: highest coverage first, then lowest level, then lexicographic on pattern string.
        candidates.sort(Comparator
                .<CandidateInfo>comparingInt(c -> -c.coverage())
                .thenComparingInt(CandidateInfo::level)
                .thenComparing(CandidateInfo::asString));
        if (candidates.size() > maxCandidateMups) {
            candidates = candidates.subList(0, maxCandidateMups);
        }

        log(verbose, "  [Figure 10] Screening " + candidates.size()
                + " candidate MUPs for omitted-subgroup performance...");
        record Screened(
                Pattern pattern,
                String patternString,
                Map<String, String> patternNamed,
                int level,
                int coverage,
                double baselineOverallAccuracy,
                double baselineSubgroupAccuracy,
                double baselineSubgroupF1) {}

        List<Screened> screened = new ArrayList<>();
        long start = System.nanoTime();
        for (int i = 0; i < candidates.size(); i++) {
            CandidateInfo ci = candidates.get(i);
            Map<String, String> named = ci.pattern().asNamedMap(bundle.featureCols());
            log(verbose, "  [Figure 10] Candidate " + (i + 1) + "/" + candidates.size()
                    + " rows=" + ci.coverage() + ": " + named);
            BaselineResult result = evaluateCandidateAtZero(
                    bundle, ci.pattern(), subgroupTestSize, randomSeed);
            if (result != null) {
                screened.add(new Screened(
                        ci.pattern(), ci.asString(), named, ci.level(), ci.coverage(),
                        result.overallAccuracy, result.subgroupAccuracy, result.subgroupF1));
            }
        }
        if (screened.isEmpty()) {
            throw new IllegalStateException(
                    "Figure 10 candidate screening did not produce any usable subgroup.");
        }

        // Sort: lowest F1 first (most affected by omission), then lowest accuracy, then highest
        // coverage, then lowest level, then lexicographic on pattern string.
        screened.sort(Comparator
                .<Screened>comparingDouble(Screened::baselineSubgroupF1)
                .thenComparingDouble(Screened::baselineSubgroupAccuracy)
                .thenComparingInt(s -> -s.coverage())
                .thenComparingInt(Screened::level)
                .thenComparing(Screened::patternString));

        List<SummaryRow> summary = new ArrayList<>();
        for (int i = 0; i < screened.size(); i++) {
            Screened s = screened.get(i);
            summary.add(new SummaryRow(
                    i + 1,
                    s.patternString,
                    s.level,
                    s.coverage,
                    s.baselineOverallAccuracy,
                    s.baselineSubgroupAccuracy,
                    s.baselineSubgroupF1,
                    s.patternNamed));
        }

        double elapsed = (System.nanoTime() - start) / 1e9;
        log(verbose, "  [Figure 10] Candidate screening finished in "
                + String.format("%.3f", elapsed) + "s.");
        log(verbose, "  [Figure 10] Best candidates by lowest omitted-subgroup F1:");
        int toShow = Math.min(numSubgroupsToPlot, screened.size());
        for (int i = 0; i < toShow; i++) {
            Screened s = screened.get(i);
            log(verbose, "    #" + (i + 1)
                    + ": F1=" + String.format("%.4f", s.baselineSubgroupF1)
                    + ", accuracy=" + String.format("%.4f", s.baselineSubgroupAccuracy)
                    + ", rows=" + s.coverage + ", MUP=" + s.patternNamed);
        }

        List<Run> runs = new ArrayList<>();
        for (int i = 0; i < toShow; i++) {
            Screened s = screened.get(i);
            int subgroupRows = s.coverage;
            int subgroupTestSizeForPattern = Math.min(subgroupTestSize, Math.max(1, subgroupRows / 5));
            int available = subgroupRows - subgroupTestSizeForPattern;
            List<Integer> trainSizes = configuredTrainSizes(bundle, available);
            log(verbose, "  [Figure 10] Running full add-back experiment for candidate #" + (i + 1) + "...");
            Run run = addBackExperiment(
                    bundle,
                    s.pattern,
                    trainSizes,
                    subgroupTestSizeForPattern,
                    randomSeed,
                    verbose,
                    "candidate #" + (i + 1),
                    tau,
                    i + 1,
                    s.baselineOverallAccuracy,
                    s.baselineSubgroupAccuracy,
                    s.baselineSubgroupF1);
            runs.add(run);
        }
        return new RunResult(runs, summary);
    }

    private record BaselineResult(double overallAccuracy, double subgroupAccuracy, double subgroupF1) {}

    private static BaselineResult evaluateCandidateAtZero(
            DatasetBundle bundle, Pattern pattern, int subgroupTestSize, long randomSeed) {
        List<Integer> subgroupIdx = rowsMatchingPattern(bundle, pattern);
        if (subgroupIdx.size() <= subgroupTestSize) {
            return null;
        }
        List<Integer> nonSubgroupIdx = complement(subgroupIdx, bundle.dataset().size());
        if (nonSubgroupIdx.isEmpty()) {
            return null;
        }

        Random rng = new Random(randomSeed);
        List<Integer> subgroupTest = sampleWithoutReplacement(subgroupIdx, subgroupTestSize, rng);

        TrainTestSplit split = trainTestSplit(nonSubgroupIdx, 0.25, rng);
        List<Integer> overallTest = new ArrayList<>(split.test);
        overallTest.addAll(subgroupTest);

        DecisionTree model = trainDecisionTree(bundle, split.train, rng);
        List<String> overallPred = predict(bundle, model, overallTest);
        List<String> subgroupPred = predict(bundle, model, subgroupTest);
        List<String> overallTrue = labels(bundle, overallTest);
        List<String> subgroupTrue = labels(bundle, subgroupTest);

        return new BaselineResult(
                Metrics.accuracy(overallTrue, overallPred),
                Metrics.accuracy(subgroupTrue, subgroupPred),
                Metrics.weightedF1(subgroupTrue, subgroupPred));
    }

    private static Run addBackExperiment(
            DatasetBundle bundle,
            Pattern pattern,
            List<Integer> trainSizes,
            int subgroupTestSize,
            long randomSeed,
            boolean verbose,
            String prefix,
            int tau,
            int rank,
            double baselineOverallAccuracy,
            double baselineSubgroupAccuracy,
            double baselineSubgroupF1) {
        List<Integer> subgroupIdx = rowsMatchingPattern(bundle, pattern);
        List<Integer> nonSubgroupIdx = complement(subgroupIdx, bundle.dataset().size());

        Random rng = new Random(randomSeed);
        List<Integer> subgroupTest = sampleWithoutReplacement(subgroupIdx, subgroupTestSize, rng);
        Set<Integer> subgroupTestSet = new LinkedHashSet<>(subgroupTest);
        List<Integer> subgroupRemaining = new ArrayList<>();
        for (int idx : subgroupIdx) {
            if (!subgroupTestSet.contains(idx)) {
                subgroupRemaining.add(idx);
            }
        }

        TrainTestSplit split = trainTestSplit(nonSubgroupIdx, 0.25, rng);
        List<Integer> overallTest = new ArrayList<>(split.test);
        overallTest.addAll(subgroupTest);
        List<String> overallTrue = labels(bundle, overallTest);
        List<String> subgroupTrue = labels(bundle, subgroupTest);

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < trainSizes.size(); i++) {
            int k = trainSizes.get(i);
            log(verbose, "  [Figure 10] " + prefix + " training size " + k
                    + " (" + (i + 1) + "/" + trainSizes.size() + ")...");
            List<Integer> subgroupTrainK = sampleWithoutReplacement(subgroupRemaining, k, rng);
            List<Integer> train = new ArrayList<>(split.train);
            train.addAll(subgroupTrainK);

            DecisionTree model = trainDecisionTree(bundle, train, rng);
            List<String> overallPred = predict(bundle, model, overallTest);
            List<String> subgroupPred = predict(bundle, model, subgroupTest);

            rows.add(new Row(
                    k,
                    Metrics.accuracy(overallTrue, overallPred),
                    Metrics.accuracy(subgroupTrue, subgroupPred),
                    Metrics.weightedF1(subgroupTrue, subgroupPred)));
        }

        Metadata metadata = new Metadata(
                pattern,
                pattern.asString(),
                pattern.asNamedMap(bundle.featureCols()),
                subgroupIdx.size(),
                subgroupTestSize,
                trainSizes,
                tau,
                rank,
                baselineOverallAccuracy,
                baselineSubgroupAccuracy,
                baselineSubgroupF1);
        return new Run(rows, metadata);
    }

    private static List<Integer> rowsMatchingPattern(DatasetBundle bundle, Pattern pattern) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < bundle.dataset().size(); i++) {
            if (pattern.matchesRow(bundle.dataset().get(i))) {
                result.add(i);
            }
        }
        return result;
    }

    private static List<Integer> complement(List<Integer> indices, int total) {
        Set<Integer> excluded = new java.util.HashSet<>(indices);
        List<Integer> result = new ArrayList<>(total - indices.size());
        for (int i = 0; i < total; i++) {
            if (!excluded.contains(i)) {
                result.add(i);
            }
        }
        return result;
    }

    private static List<Integer> sampleWithoutReplacement(List<Integer> pool, int n, Random rng) {
        if (n >= pool.size()) {
            return new ArrayList<>(pool);
        }
        List<Integer> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, rng);
        return new ArrayList<>(copy.subList(0, n));
    }

    private record TrainTestSplit(List<Integer> train, List<Integer> test) {}

    private static TrainTestSplit trainTestSplit(List<Integer> pool, double testFraction, Random rng) {
        List<Integer> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, rng);
        int testCount = Math.max(1, (int) Math.round(shuffled.size() * testFraction));
        List<Integer> test = new ArrayList<>(shuffled.subList(0, testCount));
        List<Integer> train = new ArrayList<>(shuffled.subList(testCount, shuffled.size()));
        return new TrainTestSplit(train, test);
    }

    private static DecisionTree trainDecisionTree(DatasetBundle bundle, List<Integer> indices, Random rng) {
        List<List<String>> features = new ArrayList<>(indices.size());
        List<String> labels = new ArrayList<>(indices.size());
        for (int idx : indices) {
            features.add(bundle.dataset().get(idx));
            labels.add(bundle.labels().get(idx));
        }
        return DecisionTree.fit(features, labels, bundle.featureCols(), rng);
    }

    private static List<String> predict(DatasetBundle bundle, DecisionTree model, List<Integer> indices) {
        List<List<String>> rows = new ArrayList<>(indices.size());
        for (int idx : indices) {
            rows.add(bundle.dataset().get(idx));
        }
        return model.predictAll(rows);
    }

    private static List<String> labels(DatasetBundle bundle, List<Integer> indices) {
        List<String> result = new ArrayList<>(indices.size());
        for (int idx : indices) {
            result.add(bundle.labels().get(idx));
        }
        return result;
    }

    private static int minimumRowsNeeded(DatasetBundle bundle, int subgroupTestSize) {
        JsonNode configured = bundle.config().figure10().get("subgroup_train_sizes");
        if (configured == null || !configured.isArray() || configured.size() == 0) {
            return subgroupTestSize + 1;
        }
        int max = 0;
        for (JsonNode node : configured) {
            max = Math.max(max, node.asInt());
        }
        return subgroupTestSize + max;
    }

    private static List<Integer> configuredTrainSizes(DatasetBundle bundle, int available) {
        JsonNode configured = bundle.config().figure10().get("subgroup_train_sizes");
        if (configured == null || !configured.isArray() || configured.size() == 0) {
            int step = Math.max(1, available / 4);
            List<Integer> result = new ArrayList<>();
            for (int k = 0; k <= available; k += step) {
                result.add(k);
            }
            if (result.isEmpty() || result.get(result.size() - 1) != available) {
                result.add(available);
            }
            return result;
        }
        // De-duplicate, drop entries above 'available', sort, ensure 0 is included.
        java.util.TreeSet<Integer> sorted = new java.util.TreeSet<>();
        for (JsonNode node : configured) {
            int value = node.asInt();
            if (value <= available) {
                sorted.add(value);
            }
        }
        sorted.add(0);
        return new ArrayList<>(sorted);
    }

    private static void log(boolean verbose, String message) {
        if (verbose) {
            System.out.println(message);
        }
    }
}
