package com.coverageproject.mups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coverageproject.core.Pattern;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Cross-validates the three MUP algorithms against each other and against the paper's worked
 * examples. The MUP set is an algorithm-independent property of a dataset and τ, so any two of
 * these algorithms returning different sets is a bug.
 */
class MupAlgorithmsTest {

    /**
     * Paper's Example 1: three binary attributes, dataset {010, 001, 000, 011, 001}. With τ=1 the
     * unique MUP is 1XX. Used in the paper's discussion of all three algorithms.
     */
    private static final List<List<String>> EXAMPLE1_DATASET = List.of(
            List.of("0", "1", "0"),
            List.of("0", "0", "1"),
            List.of("0", "0", "0"),
            List.of("0", "1", "1"),
            List.of("0", "0", "1"));

    private static final List<List<String>> BINARY_DOMAINS_3 =
            List.of(List.of("0", "1"), List.of("0", "1"), List.of("0", "1"));

    @Test
    void patternBreakerOnExample1() {
        Set<Pattern> mups = PatternBreaker.patternBreaker(EXAMPLE1_DATASET, BINARY_DOMAINS_3, 1);
        assertEquals(Set.of(Pattern.of("1", null, null)), mups);
    }

    @Test
    void patternCombinerOnExample1() {
        Set<Pattern> mups = PatternCombiner.patternCombiner(EXAMPLE1_DATASET, BINARY_DOMAINS_3, 1);
        assertEquals(Set.of(Pattern.of("1", null, null)), mups);
    }

    @Test
    void deepDiverOnExample1() {
        Set<Pattern> mups = DeepDiver.deepdiver(EXAMPLE1_DATASET, BINARY_DOMAINS_3, 1);
        assertEquals(Set.of(Pattern.of("1", null, null)), mups);
    }

    @Test
    void allThreeAlgorithmsAgreeOnRichExample() {
        // 8 binary tuples, missing some combinations to create multiple MUPs.
        List<List<String>> dataset = List.of(
                List.of("0", "0", "0"),
                List.of("0", "0", "1"),
                List.of("0", "1", "0"),
                List.of("1", "0", "0"),
                List.of("0", "0", "0"),
                List.of("0", "1", "0"),
                List.of("1", "0", "0"),
                List.of("1", "0", "1"));

        // Hand-computed expected MUPs for τ = 2:
        // 011 (uncovered, parents 0X1 and 01X both covered enough? Let's not assert by hand —
        // we just check cross-algorithm agreement.)
        Set<Pattern> breakerMups = PatternBreaker.patternBreaker(dataset, BINARY_DOMAINS_3, 2);
        Set<Pattern> combinerMups = PatternCombiner.patternCombiner(dataset, BINARY_DOMAINS_3, 2);
        Set<Pattern> diverMups = DeepDiver.deepdiver(dataset, BINARY_DOMAINS_3, 2);

        assertEquals(breakerMups, combinerMups,
                "PATTERN-BREAKER and PATTERN-COMBINER must produce identical MUP sets");
        assertEquals(breakerMups, diverMups,
                "PATTERN-BREAKER and DEEPDIVER must produce identical MUP sets");
    }

    @Test
    void fullyCoveredDatasetHasNoMups() {
        // 8 binary tuples covering every cell of the 3-binary pattern graph at high multiplicity.
        List<List<String>> dataset = new java.util.ArrayList<>();
        for (int rep = 0; rep < 4; rep++) {
            for (String a : List.of("0", "1")) {
                for (String b : List.of("0", "1")) {
                    for (String c : List.of("0", "1")) {
                        dataset.add(List.of(a, b, c));
                    }
                }
            }
        }
        // With τ = 1 every pattern is covered, so no MUPs.
        assertEquals(Set.of(), PatternBreaker.patternBreaker(dataset, BINARY_DOMAINS_3, 1));
        assertEquals(Set.of(), PatternCombiner.patternCombiner(dataset, BINARY_DOMAINS_3, 1));
        assertEquals(Set.of(), DeepDiver.deepdiver(dataset, BINARY_DOMAINS_3, 1));
    }

    @Test
    void emptyDatasetHasRootMupAtTau1() {
        List<List<String>> dataset = List.of();
        // With no data, every pattern is uncovered, so XXX is the unique MUP (every more specific
        // pattern is dominated by XXX).
        Set<Pattern> expected = Set.of(Pattern.allX(3));
        assertEquals(expected, PatternBreaker.patternBreaker(dataset, BINARY_DOMAINS_3, 1));
        assertEquals(expected, PatternCombiner.patternCombiner(dataset, BINARY_DOMAINS_3, 1));
        assertEquals(expected, DeepDiver.deepdiver(dataset, BINARY_DOMAINS_3, 1));
    }

    @Test
    void agreementAcrossSeveralTauValuesOnTernaryAttribute() {
        List<List<String>> domains = List.of(List.of("a", "b", "c"), List.of("0", "1"));
        List<List<String>> dataset = List.of(
                List.of("a", "0"), List.of("a", "0"), List.of("a", "1"),
                List.of("b", "0"), List.of("b", "1"), List.of("b", "1"));

        for (int tau : new int[]{1, 2, 3}) {
            Set<Pattern> breakerMups = PatternBreaker.patternBreaker(dataset, domains, tau);
            Set<Pattern> combinerMups = PatternCombiner.patternCombiner(dataset, domains, tau);
            Set<Pattern> diverMups = DeepDiver.deepdiver(dataset, domains, tau);
            assertEquals(breakerMups, combinerMups, "tau=" + tau + " (breaker vs combiner)");
            assertEquals(breakerMups, diverMups, "tau=" + tau + " (breaker vs diver)");
            // MUPs should be maximal: no MUP dominates another.
            for (Pattern a : breakerMups) {
                for (Pattern b : breakerMups) {
                    if (!a.equals(b)) {
                        assertTrue(!com.coverageproject.core.Patterns.dominates(a, b),
                                "MUPs must be incomparable but " + a + " dominates " + b);
                    }
                }
            }
        }
    }
}
