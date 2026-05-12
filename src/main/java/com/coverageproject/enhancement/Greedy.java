package com.coverageproject.enhancement;

import com.coverageproject.core.Coverage;
import com.coverageproject.core.Pattern;
import com.coverageproject.core.Patterns;
import com.coverageproject.core.ValidationOracle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GREEDY coverage-enhancement (Algorithm 5 in the technical report; § IV-B of the conference
 * version).
 *
 * <p>Given a target coverage threshold {@code tau} and a target level {@code maxLevel}, the
 * algorithm collects the minimum-ish set of additional tuples needed to bring every level-{@code
 * maxLevel} pattern up to coverage {@code tau}. It is the classic greedy approximation for set
 * cover / hitting set: at each step pick the candidate tuple that hits the most still-deficient
 * patterns, and apply it once.
 *
 * <p>This is the direct implementation: enumerate every valid value combination, score it against
 * the deficit map, take the argmax, decrement deficits, repeat. The paper's § IV-B describes a
 * pruned tree-search ("Efficient implementation of the greedy algorithm") that uses inverted
 * indices and DFS to avoid the full enumeration. We deliberately don't implement that variant
 * here, because the goal of this port is to replicate the algorithm as the paper writes it; the
 * tree-pruning variant is a performance optimisation, not a different algorithm.
 */
public final class Greedy {

    private Greedy() {
        // utility class
    }

    public static List<List<String>> greedyCoverageEnhancement(
            List<List<String>> dataset,
            List<List<String>> domains,
            int tau,
            int maxLevel) {
        return greedyCoverageEnhancement(dataset, domains, tau, maxLevel, ValidationOracle.ACCEPT_ALL);
    }

    public static List<List<String>> greedyCoverageEnhancement(
            List<List<String>> dataset,
            List<List<String>> domains,
            int tau,
            int maxLevel,
            ValidationOracle oracle) {

        // Step 1: figure out which patterns at level `maxLevel` still need help, and by how many
        // rows.  We iterate the dataset once per pattern via plain Coverage.coverage; the paper's
        // Appendix A describes how to do this with bit-vectors, but that's an Appendix-A speedup,
        // not part of the GREEDY algorithm itself.
        Map<Pattern, Integer> uncoveredDeficit = new LinkedHashMap<>();
        for (Pattern pattern : Patterns.allPatternsAtLevel(domains, maxLevel)) {
            int cov = Coverage.coverage(pattern, dataset);
            if (cov < tau) {
                uncoveredDeficit.put(pattern, tau - cov);
            }
        }

        if (uncoveredDeficit.isEmpty()) {
            return List.of();
        }

        // Enumerate every valid tuple once. We re-use this list across iterations.
        List<List<String>> candidateTuples = new ArrayList<>();
        for (List<String> tuple : cartesianProduct(domains)) {
            if (oracle.accept(tuple)) {
                candidateTuples.add(tuple);
            }
        }

        List<List<String>> added = new ArrayList<>();

        while (!uncoveredDeficit.isEmpty()) {
            List<String> bestTuple = null;
            int bestHitCount = 0;

            for (List<String> candidate : candidateTuples) {
                int hitCount = 0;
                for (Map.Entry<Pattern, Integer> entry : uncoveredDeficit.entrySet()) {
                    if (entry.getValue() > 0 && entry.getKey().matchesRow(candidate)) {
                        hitCount++;
                    }
                }
                if (hitCount > bestHitCount) {
                    bestHitCount = hitCount;
                    bestTuple = candidate;
                }
            }

            if (bestTuple == null || bestHitCount == 0) {
                throw new IllegalStateException(
                        "Could not cover all patterns. The validation oracle may block all "
                                + "useful tuples.");
            }

            added.add(bestTuple);

            // Decrement the deficit of every pattern that this tuple matches, and drop the ones
            // that are fully satisfied.
            Map<Pattern, Integer> newDeficit = new HashMap<>();
            for (Map.Entry<Pattern, Integer> entry : uncoveredDeficit.entrySet()) {
                int deficit = entry.getValue();
                if (entry.getKey().matchesRow(bestTuple)) {
                    deficit -= 1;
                }
                if (deficit > 0) {
                    newDeficit.put(entry.getKey(), deficit);
                }
            }
            uncoveredDeficit = newDeficit;
        }

        return added;
    }

    /** Iterative Cartesian product over a list of domains. */
    private static List<List<String>> cartesianProduct(List<List<String>> domains) {
        List<List<String>> result = new ArrayList<>();
        result.add(new ArrayList<>());
        for (List<String> domain : domains) {
            List<List<String>> next = new ArrayList<>();
            for (List<String> prefix : result) {
                for (String value : domain) {
                    List<String> extended = new ArrayList<>(prefix);
                    extended.add(value);
                    next.add(extended);
                }
            }
            result = next;
        }
        List<List<String>> frozen = new ArrayList<>(result.size());
        for (List<String> tuple : result) {
            frozen.add(List.copyOf(tuple));
        }
        return frozen;
    }
}
