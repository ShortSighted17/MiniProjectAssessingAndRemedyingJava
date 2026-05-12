package com.coverageproject.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Operations on {@link Pattern}s that don't fit naturally as instance methods: parent/child
 * navigation in the pattern graph, dominance tests, and bulk enumeration at a given level.
 *
 * <p>These are deliberately straightforward, matching the paper's pseudocode and the original
 * Python reference implementation; we don't attempt any of the inverted-index speedups described in
 * the paper's appendix.
 */
public final class Patterns {

    private Patterns() {
        // utility class
    }

    /**
     * Every parent of {@code pattern}: one parent per deterministic cell, obtained by replacing
     * that cell with X. A pattern with {@code level == 0} has no parents.
     */
    public static List<Pattern> parents(Pattern pattern) {
        List<Pattern> result = new ArrayList<>();
        for (int i = 0; i < pattern.size(); i++) {
            if (pattern.get(i) != Pattern.X) {
                result.add(pattern.withCell(i, Pattern.X));
            }
        }
        return result;
    }

    /**
     * Every child of {@code pattern}: for each non-deterministic cell, one child per domain value
     * substituted into that cell.
     */
    public static List<Pattern> children(Pattern pattern, List<List<String>> domains) {
        List<Pattern> result = new ArrayList<>();
        for (int i = 0; i < pattern.size(); i++) {
            if (pattern.get(i) == Pattern.X) {
                for (String value : domains.get(i)) {
                    result.add(pattern.withCell(i, value));
                }
            }
        }
        return result;
    }

    /**
     * True iff {@code general} dominates {@code specific} in the paper's sense: every deterministic
     * cell of {@code general} matches the corresponding cell of {@code specific}.
     */
    public static boolean dominates(Pattern general, Pattern specific) {
        if (general.size() != specific.size()) {
            throw new IllegalArgumentException("Patterns must have the same length");
        }
        for (int i = 0; i < general.size(); i++) {
            String g = general.get(i);
            if (g != Pattern.X && !g.equals(specific.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** True iff any MUP in {@code mups} dominates {@code pattern}. */
    public static boolean dominatedByAnyMup(Pattern pattern, Set<Pattern> mups) {
        for (Pattern mup : mups) {
            if (dominates(mup, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate every pattern of length {@code domains.size()} that has exactly {@code targetLevel}
     * deterministic cells. Used by the coverage-enhancement algorithms (which need to enumerate
     * level-l patterns) and by NAIVE MUP identification (not implemented here, see paper § III-A).
     */
    public static List<Pattern> allPatternsAtLevel(List<List<String>> domains, int targetLevel) {
        List<Pattern> result = new ArrayList<>();
        int d = domains.size();
        ArrayList<String> current = new ArrayList<>(d);
        for (int i = 0; i < d; i++) {
            current.add(Pattern.X);
        }
        backtrackAllPatternsAtLevel(domains, targetLevel, 0, current, 0, result);
        return result;
    }

    private static void backtrackAllPatternsAtLevel(
            List<List<String>> domains,
            int targetLevel,
            int index,
            ArrayList<String> current,
            int deterministicCount,
            List<Pattern> out) {
        if (deterministicCount > targetLevel) {
            return;
        }
        if (index == domains.size()) {
            if (deterministicCount == targetLevel) {
                out.add(new Pattern(current));
            }
            return;
        }

        // Option A: keep this cell as X.
        current.set(index, Pattern.X);
        backtrackAllPatternsAtLevel(domains, targetLevel, index + 1, current, deterministicCount, out);

        // Option B: fix this cell to each domain value.
        for (String value : domains.get(index)) {
            current.set(index, value);
            backtrackAllPatternsAtLevel(
                    domains, targetLevel, index + 1, current, deterministicCount + 1, out);
        }
        current.set(index, Pattern.X);
    }
}
