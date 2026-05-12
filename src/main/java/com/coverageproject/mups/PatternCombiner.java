package com.coverageproject.mups;

import com.coverageproject.core.Pattern;
import com.coverageproject.core.Patterns;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PATTERN-COMBINER (Algorithm 2 in the paper), the bottom-up algorithm for MUP identification.
 *
 * <p>Starts at the most specific level (leaves), iterates the dataset once to count how many rows
 * match each leaf pattern, and then climbs the pattern graph one level at a time. The coverage of
 * a parent is the sum of the coverages of its disjoint partition-children (the children obtained
 * by fixing the parent's right-most non-deterministic cell to each domain value). If a parent's
 * coverage is still below {@code tau}, it survives to the next level as a candidate; otherwise the
 * branch is pruned.
 *
 * <p>The MUPs at each level are exactly the level-l uncovered patterns whose level-(l-1) parents
 * are all covered (equivalently: none of the level-l-1 parents appear in {@code nextCount}).
 *
 * <p>This follows Algorithm 2 verbatim. A few small notes about the literal pseudocode:
 *
 * <ul>
 *   <li>Line 14 reads {@code cnt = Σ (count[P''] if P'' ∈ count else τ)}. Treating an absent child
 *       as having coverage τ is sound: an absent child is one whose coverage was ≥ τ at the
 *       previous level, so it didn't make it into the {@code count} map. Substituting τ for it
 *       lets us early-exit as soon as the running sum reaches τ, without losing correctness — and
 *       in that case the new parent fails the {@code cnt < τ} test on line 15 anyway, so it's
 *       never stored.
 *   <li>Rule 2 generates parents by replacing a deterministic-with-value-0 cell to the right of
 *       the right-most non-deterministic cell with X. "Value 0" is interpreted as the first value
 *       of the column's domain (see the paper's footnote on Rule 2: "one of the values of each
 *       attribute is mapped to 0").
 * </ul>
 */
public final class PatternCombiner {

    private PatternCombiner() {
        // utility class
    }

    public static Set<Pattern> patternCombiner(
            List<List<String>> dataset, List<List<String>> domains, int tau) {

        // Step 1: pass over the dataset once and count occurrences of each level-d (leaf) pattern.
        // We only keep leaves whose count is below tau; the rest are covered, so they cannot lead
        // to a MUP and don't need to participate in the bottom-up sweep.
        Map<List<String>, Integer> leafCounts = new HashMap<>();
        for (List<String> row : dataset) {
            leafCounts.merge(row, 1, Integer::sum);
        }

        Map<Pattern, Integer> count = new HashMap<>();
        for (List<String> values : cartesianProduct(domains)) {
            int cnt = leafCounts.getOrDefault(values, 0);
            if (cnt < tau) {
                count.put(new Pattern(values), cnt);
            }
        }

        if (count.isEmpty()) {
            return Set.of();
        }

        Set<Pattern> mups = new HashSet<>();

        for (int level = 0; level <= domains.size(); level++) {
            Map<Pattern, Integer> nextCount = new HashMap<>();

            for (Pattern pattern : count.keySet()) {
                for (Pattern parent : rule2Parents(pattern, domains)) {
                    int cnt = 0;
                    boolean reachedTau = false;
                    for (Pattern child : partitionChildren(parent, domains)) {
                        Integer childCount = count.get(child);
                        // An absent child is one whose coverage was ≥ τ (so it was not stored). We
                        // pretend its coverage is τ for the purposes of this sum, which is enough
                        // to early-exit once the running total reaches τ.
                        cnt += childCount != null ? childCount : tau;
                        if (cnt >= tau) {
                            reachedTau = true;
                            break;
                        }
                    }
                    if (!reachedTau) {
                        nextCount.put(parent, cnt);
                    }
                }
            }

            // Patterns in `count` whose parents are all covered (= none of them appear in
            // `nextCount`) are MUPs. The text: "The uncovered nodes at level ℓ that all of their
            // parents at level ℓ-1 are covered are MUPs."
            Set<Pattern> nextKeys = nextCount.keySet();
            for (Pattern pattern : count.keySet()) {
                if (!anyParentIn(pattern, nextKeys)) {
                    mups.add(pattern);
                }
            }

            if (nextCount.isEmpty()) {
                break;
            }
            count = nextCount;
        }

        return mups;
    }

    /**
     * Rule 2 parents of {@code pattern}. Mirror image of Rule 1: find the right-most
     * non-deterministic cell, then for each deterministic cell to its right whose value equals the
     * first value of that column's domain (the "0" in the paper), replace it with X. That cell is
     * the one parent the pattern is responsible for generating in the bottom-up sweep.
     */
    static List<Pattern> rule2Parents(Pattern pattern, List<List<String>> domains) {
        int rightmostX = -1;
        for (int i = 0; i < pattern.size(); i++) {
            if (pattern.get(i) == Pattern.X) {
                rightmostX = i;
            }
        }

        List<Pattern> result = new ArrayList<>();
        for (int i = rightmostX + 1; i < pattern.size(); i++) {
            String cell = pattern.get(i);
            if (cell != Pattern.X && cell.equals(domains.get(i).get(0))) {
                result.add(pattern.withCell(i, Pattern.X));
            }
        }
        return result;
    }

    /**
     * The disjoint partition-children of {@code pattern}: replace the right-most non-deterministic
     * cell with each value in its column's domain. Their coverages sum to {@code pattern}'s
     * coverage (see § III-D of the paper).
     */
    private static List<Pattern> partitionChildren(Pattern pattern, List<List<String>> domains) {
        int rightmostX = -1;
        for (int i = 0; i < pattern.size(); i++) {
            if (pattern.get(i) == Pattern.X) {
                rightmostX = i;
            }
        }
        if (rightmostX == -1) {
            return List.of();
        }

        List<Pattern> result = new ArrayList<>();
        for (String value : domains.get(rightmostX)) {
            result.add(pattern.withCell(rightmostX, value));
        }
        return result;
    }

    private static boolean anyParentIn(Pattern pattern, Set<Pattern> set) {
        for (Pattern parent : Patterns.parents(pattern)) {
            if (set.contains(parent)) {
                return true;
            }
        }
        return false;
    }

    /**
     * All d-tuples obtained as the Cartesian product of {@code domains}. Used to seed the leaf
     * level. Built iteratively to avoid surprisingly deep recursion when d is large.
     */
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
        // Wrap each tuple as an unmodifiable list so it's usable as a Map key (List equality is
        // structural, so this is fine).
        List<List<String>> frozen = new ArrayList<>(result.size());
        for (List<String> tuple : result) {
            frozen.add(List.copyOf(tuple));
        }
        return frozen;
    }

    /** Convenience for tests. */
    @SuppressWarnings("unused")
    static Pattern patternOf(String... cells) {
        return new Pattern(Arrays.asList(cells));
    }
}
