package com.coverageproject.mups;

import com.coverageproject.core.CoverageCache;
import com.coverageproject.core.Pattern;
import com.coverageproject.core.Patterns;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PATTERN-BREAKER (Algorithm 1 in the paper), the top-down BFS algorithm for MUP identification.
 *
 * <p>Starting from the root {@code XX...X}, the algorithm walks the pattern graph level by level.
 * For every candidate at the current level:
 *
 * <ul>
 *   <li>If any of its parents is uncovered, the node is marked uncovered without checking its
 *       coverage. (This is how the monotonicity property prunes the covered region: if a parent is
 *       uncovered, all its descendants are too.)
 *   <li>Otherwise we compute the coverage. If it's below {@code tau}, the node is a MUP. If it's
 *       at or above {@code tau}, we generate its children via Rule 1 (replace each non-deterministic
 *       cell to the right of the right-most deterministic cell, one at a time).
 * </ul>
 *
 * <p>Implementation note on the paper's line 9. Algorithm 1 in the paper checks "{@code if P' ∉ Qp
 * or P' ∈ M then flag = true}" — i.e., a parent is rejected when it's missing from the previous
 * level's queue OR it's already a MUP. The published condition doesn't match the prose
 * description, which simply says "if any parent is uncovered, mark this node as uncovered". A
 * pattern whose parent is uncovered-but-not-a-MUP (because that parent itself was caught by Rule 1
 * elsewhere) wouldn't be in {@code Qp}, and it wouldn't be in {@code M}, so the paper's literal
 * condition does not catch it. We follow the original Python reference here and track an explicit
 * "previousUncovered" set that propagates the uncovered flag down through generations, which is
 * what the prose actually describes.
 */
public final class PatternBreaker {

    private PatternBreaker() {
        // utility class
    }

    public static Set<Pattern> patternBreaker(
            List<List<String>> dataset, List<List<String>> domains, int tau) {
        int d = domains.size();
        Pattern root = Pattern.allX(d);

        Set<Pattern> mups = new HashSet<>();
        Set<Pattern> currentLevel = new HashSet<>();
        currentLevel.add(root);
        Set<Pattern> previousUncovered = new HashSet<>();
        CoverageCache cache = new CoverageCache(dataset);

        // The graph has at most d+1 levels (level 0 = root, level d = leaves), so we iterate that
        // many times. The early-break on an empty current level handles the case where every node
        // higher up was uncovered.
        for (int level = 0; level <= d; level++) {
            if (currentLevel.isEmpty()) {
                break;
            }

            Set<Pattern> nextLevel = new HashSet<>();
            Set<Pattern> currentUncovered = new HashSet<>();

            for (Pattern pattern : currentLevel) {
                if (anyParentInSet(pattern, previousUncovered)) {
                    // Propagate the uncovered mark to this node's descendants on the next iteration.
                    currentUncovered.add(pattern);
                    continue;
                }

                if (cache.coverage(pattern) < tau) {
                    mups.add(pattern);
                    currentUncovered.add(pattern);
                } else {
                    nextLevel.addAll(rule1Children(pattern, domains));
                }
            }

            previousUncovered = currentUncovered;
            currentLevel = nextLevel;
        }

        return mups;
    }

    private static boolean anyParentInSet(Pattern pattern, Set<Pattern> set) {
        if (set.isEmpty()) {
            return false;
        }
        for (Pattern parent : Patterns.parents(pattern)) {
            if (set.contains(parent)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Children of {@code pattern} under Rule 1 of the paper: replace the non-deterministic cells
     * <em>to the right of the right-most deterministic cell</em>, one at a time, with each domain
     * value. This is what makes each MUP candidate get generated exactly once across the BFS.
     *
     * <p>Exposed package-private because DEEPDIVER also generates children using Rule 1.
     */
    static List<Pattern> rule1Children(Pattern pattern, List<List<String>> domains) {
        int rightmostDeterministic = -1;
        for (int i = 0; i < pattern.size(); i++) {
            if (pattern.get(i) != Pattern.X) {
                rightmostDeterministic = i;
            }
        }

        List<Pattern> result = new java.util.ArrayList<>();
        for (int i = rightmostDeterministic + 1; i < pattern.size(); i++) {
            if (pattern.get(i) == Pattern.X) {
                for (String value : domains.get(i)) {
                    result.add(pattern.withCell(i, value));
                }
            }
        }
        return result;
    }
}
