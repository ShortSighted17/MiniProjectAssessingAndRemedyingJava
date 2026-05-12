package com.coverageproject.enhancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coverageproject.core.Coverage;
import com.coverageproject.core.Pattern;
import com.coverageproject.core.Patterns;
import com.coverageproject.core.ValidationOracle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnhancementTest {

    private static final List<List<String>> BINARY_DOMAINS_3 =
            List.of(List.of("0", "1"), List.of("0", "1"), List.of("0", "1"));

    @Test
    void greedyHitsEveryLevelTwoPatternToTauOne() {
        // Empty dataset, τ=1 at level 2: every pattern at level 2 has coverage 0 → deficit 1. With
        // 3 binary attributes there are C(3,2)*2^2 = 12 level-2 patterns. The greedy algorithm
        // should add tuples until each of them is covered at least once.
        List<List<String>> empty = List.of();
        List<List<String>> added = Greedy.greedyCoverageEnhancement(empty, BINARY_DOMAINS_3, 1, 2);
        assertTrue(added.size() > 0, "Greedy should add some tuples when patterns are uncovered");

        // Validate: every level-2 pattern is now covered by at least one of the added rows.
        List<Pattern> level2 = Patterns.allPatternsAtLevel(BINARY_DOMAINS_3, 2);
        for (Pattern p : level2) {
            int covered = Coverage.coverage(p, added);
            assertTrue(covered >= 1, "Pattern " + p + " not covered by greedy output");
        }
    }

    @Test
    void greedyReturnsEmptyWhenAlreadyCovered() {
        // Three binary attributes; supply every triple → every pattern is fully covered.
        List<List<String>> dataset = new ArrayList<>();
        for (String a : List.of("0", "1")) {
            for (String b : List.of("0", "1")) {
                for (String c : List.of("0", "1")) {
                    dataset.add(List.of(a, b, c));
                }
            }
        }
        List<List<String>> added = Greedy.greedyCoverageEnhancement(dataset, BINARY_DOMAINS_3, 1, 2);
        assertEquals(0, added.size());
    }

    @Test
    void naiveAddsOneRowPerDeficit() {
        List<List<String>> empty = List.of();
        List<List<String>> added = Naive.naiveCoverageEnhancement(empty, BINARY_DOMAINS_3, 2, 1);
        // 6 level-1 patterns, each with deficit 2 → 12 rows added.
        assertEquals(12, added.size());
    }

    @Test
    void greedyRespectsValidationOracle() {
        // Empty dataset; reject anything starting with "1". Greedy should still cover all
        // patterns that *can* be covered by rows starting with "0", and only those.
        List<List<String>> empty = List.of();
        ValidationOracle reject1 = row -> !"1".equals(row.get(0));
        // Pick maxLevel = 1 so that the unreachable patterns (the ones fixing the first column to
        // "1") would otherwise be required.
        try {
            Greedy.greedyCoverageEnhancement(empty, BINARY_DOMAINS_3, 1, 1, reject1);
            // We expect an exception because patterns like (1, X, X) cannot be hit if the oracle
            // forbids any row starting with "1".
            org.junit.jupiter.api.Assertions.fail("Expected greedy to fail when oracle blocks needed rows");
        } catch (IllegalStateException expected) {
            // ok
        }
    }
}
