package com.coverageproject.mups;

import com.coverageproject.core.CoverageCache;
import com.coverageproject.core.MupIndex;
import com.coverageproject.core.Pattern;
import com.coverageproject.core.Patterns;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * DEEPDIVER (Algorithm 3 in the paper / Algorithm 1 in the IEEE conference version), the
 * DFS-with-back-tracking MUP identification algorithm.
 *
 * <p>The strategy: dive down the pattern graph until we hit an uncovered node; then climb up
 * through any uncovered parents until we find a "topmost" uncovered node — that node is a MUP. We
 * record it in the MUP index, which is then used to prune both the descendants ("dominated by a
 * known MUP") and the ancestors ("dominates a known MUP, can't itself be a MUP") of every
 * subsequent candidate.
 *
 * <p><b>Typo fixes.</b> The published Algorithm 3 contains two bugs that we correct here, both
 * fixed the same way in the Python reference.
 *
 * <p><b>Typo 1 — the spurious "P dominates M" branch.</b> Algorithm 3 lines 8-9 read:
 *
 * <pre>
 * 6: if P is dominated by M then continue
 * 8: else if P dominates M then uncoveredFlag = true
 * </pre>
 *
 * The "P dominates M" branch flags P as uncovered without checking its coverage. That's wrong: if
 * P dominates some known MUP M', M' is a <em>descendant</em> of P, which only tells us P itself
 * cannot be a MUP (MUPs are maximal); it does not tell us P is uncovered. An ancestor of an
 * uncovered node can easily be covered. The buggy branch makes the climb-up loop emit a spurious
 * MUP that dominates the real MUP we already found, which violates the maximality of MUPs. We
 * drop this branch entirely and rely on the {@code dominatedByMup} prune above plus the plain
 * coverage check below — which is exactly what the Python reference does.
 *
 * <p><b>Typo 2 — the inner climb stack.</b> As printed:
 *
 * <pre>
 * 14: if uncoveredFlag is true then
 * 15:   Let S' = an empty stack         // S' is empty
 * 16:   while S' is not empty do        // ... and stays empty, so this loop never runs
 * 17:     P' = pop a node from S'
 * 18:     P0 = generates parent nodes of P' by replacing one deterministic cell with X.
 * 19:     for P'' ∈ P0 do
 * 20:       cnt' = cov(P'', D)
 * 21:       if cnt' < τ then push P'' to S'; break
 * 22:     end for
 * 23:     add P to M                    // ... and even if it did, this adds P, not P'
 * 24:   end while
 * </pre>
 *
 * <p>The prose immediately above the pseudocode describes the correct behaviour: "after finding an
 * uncovered node, DEEPDIVER ... checks the parents of the current node to see if any of them are
 * uncovered. If there exists such a parent, it moves to the parent and continues until it finds a
 * MUP." That's what we implement here. The two corrections, both also present in the Python
 * reference implementation:
 *
 * <ol>
 *   <li>The current pattern is pushed onto {@code S'} before the inner loop starts, so the loop
 *       actually runs.
 *   <li>What gets added to {@code M} is the topmost uncovered ancestor we climb to — i.e. the
 *       final {@code P'} — not the original {@code P}.
 * </ol>
 *
 * <p>We don't bother with an explicit second stack: a single mutable "current" pattern suffices,
 * because we never branch during the climb (we move to the first uncovered parent and stop).
 */
public final class DeepDiver {

    private DeepDiver() {
        // utility class
    }

    public static Set<Pattern> deepdiver(
            List<List<String>> dataset, List<List<String>> domains, int tau) {
        Pattern root = Pattern.allX(domains.size());
        MupIndex mups = new MupIndex();
        Deque<Pattern> stack = new ArrayDeque<>();
        stack.push(root);
        CoverageCache cache = new CoverageCache(dataset);

        while (!stack.isEmpty()) {
            Pattern pattern = stack.pop();

            // First chance to prune: if a known MUP already dominates this pattern, we don't need
            // to look at it. (Lines 6-7 of Algorithm 3.)
            if (mups.dominatedByMup(pattern)) {
                continue;
            }

            // Lines 8-12 of the paper. The paper inserts an extra branch here ("else if P
            // dominates M then uncoveredFlag = true") that mishandles the dominance relation.
            // If P dominates some known MUP M', then M' is a descendant of P, which only tells us
            // P cannot itself be a MUP (MUPs are maximal). It does not tell us P is uncovered —
            // an ancestor of an uncovered node can still have coverage well above τ. Setting
            // uncoveredFlag = true here causes DEEPDIVER to climb up from P and emit spurious
            // MUPs that dominate true MUPs. The Python reference omits this branch entirely;
            // we do the same and just check coverage. (This is the second pseudocode bug in
            // Algorithm 3, after the missing push-current-onto-S' on line 15 documented at the
            // top of this class.)
            boolean uncovered = cache.coverage(pattern) < tau;

            if (uncovered) {
                // Climb-up loop (lines 14-24, with the typo fixed): start from `pattern` and walk
                // to the first uncovered parent at each step. The topmost uncovered ancestor is a
                // MUP because (a) it is uncovered, and (b) by construction none of its parents are
                // uncovered (otherwise we'd have climbed there instead).
                Pattern current = pattern;
                while (true) {
                    Pattern uncoveredParent = firstUncoveredParent(current, cache, tau);
                    if (uncoveredParent == null) {
                        break;
                    }
                    current = uncoveredParent;
                }
                mups.add(current);
                continue;
            }

            // Covered node: expand via Rule 1 (same as PATTERN-BREAKER).
            //
            // The Python reference pushes children in reverse so that the DFS visits them in
            // domain order, which makes traces easier to read. We do the same.
            List<Pattern> children = PatternBreaker.rule1Children(pattern, domains);
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }

        return mups.patterns();
    }

    private static Pattern firstUncoveredParent(
            Pattern pattern, CoverageCache cache, int tau) {
        for (Pattern parent : Patterns.parents(pattern)) {
            if (cache.coverage(parent) < tau) {
                return parent;
            }
        }
        return null;
    }
}
