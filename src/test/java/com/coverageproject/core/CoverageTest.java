package com.coverageproject.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CoverageTest {

    /** Paper's Example 1: D = {010, 001, 000, 011, 001} over three binary attributes. */
    private static final List<List<String>> EXAMPLE1_DATASET = List.of(
            List.of("0", "1", "0"),
            List.of("0", "0", "1"),
            List.of("0", "0", "0"),
            List.of("0", "1", "1"),
            List.of("0", "0", "1"));

    @Test
    void coverageOfRootIsDatasetSize() {
        assertEquals(5, Coverage.coverage(Pattern.allX(3), EXAMPLE1_DATASET));
    }

    @Test
    void coverageMatchesPaperExample() {
        // From the paper appendix, cov(0X1) = 3 in the bit-vector example (a different dataset).
        // Here we just verify against the manually computable values of Example 1.
        assertEquals(5, Coverage.coverage(Pattern.of("0", null, null), EXAMPLE1_DATASET));
        assertEquals(0, Coverage.coverage(Pattern.of("1", null, null), EXAMPLE1_DATASET));
        assertEquals(2, Coverage.coverage(Pattern.of("0", "1", null), EXAMPLE1_DATASET));
        assertEquals(2, Coverage.coverage(Pattern.of(null, null, "0"), EXAMPLE1_DATASET));
        assertEquals(1, Coverage.coverage(Pattern.of("0", "1", "0"), EXAMPLE1_DATASET));
    }

    @Test
    void isUncoveredRespectsTau() {
        Pattern p = Pattern.of("1", null, null); // never seen in Example 1, coverage 0
        assertTrue(Coverage.isUncovered(p, EXAMPLE1_DATASET, 1));
        assertFalse(Coverage.isUncovered(Pattern.of("0", null, null), EXAMPLE1_DATASET, 1));
    }

    @Test
    void coverageCacheReturnsConsistentValues() {
        CoverageCache cache = new CoverageCache(EXAMPLE1_DATASET);
        Pattern p = Pattern.of("0", "1", null);
        assertEquals(2, cache.coverage(p));
        // Second call hits the cache; value must be the same.
        assertEquals(2, cache.coverage(p));
    }
}
