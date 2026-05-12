package com.coverageproject.enhancement;

import com.coverageproject.core.Coverage;
import com.coverageproject.core.Pattern;
import com.coverageproject.core.Patterns;
import java.util.ArrayList;
import java.util.List;

/**
 * NAIVE coverage-enhancement baseline (§ IV-A of the conference version).
 *
 * <p>For every uncovered level-{@code maxLevel} pattern, add as many tuples as the pattern's
 * deficit (= τ − coverage). Each tuple is built by picking the pattern's deterministic cells where
 * present and the first value of the column's domain everywhere else.
 *
 * <p>This baseline ignores the fact that a single tuple may help several patterns at once, so it
 * tends to add far more tuples than {@link Greedy}. That's precisely what Figure 16 is supposed to
 * show.
 */
public final class Naive {

    private Naive() {
        // utility class
    }

    public static List<List<String>> naiveCoverageEnhancement(
            List<List<String>> dataset,
            List<List<String>> domains,
            int tau,
            int maxLevel) {
        List<List<String>> added = new ArrayList<>();
        for (Pattern pattern : Patterns.allPatternsAtLevel(domains, maxLevel)) {
            int deficit = tau - Coverage.coverage(pattern, dataset);
            if (deficit <= 0) {
                continue;
            }
            List<String> candidate = new ArrayList<>(pattern.size());
            for (int i = 0; i < pattern.size(); i++) {
                String cell = pattern.get(i);
                candidate.add(cell != Pattern.X ? cell : domains.get(i).get(0));
            }
            List<String> frozen = List.copyOf(candidate);
            for (int k = 0; k < deficit; k++) {
                added.add(frozen);
            }
        }
        return added;
    }
}
