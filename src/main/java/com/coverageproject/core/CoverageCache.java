package com.coverageproject.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memoizes coverage values per pattern. Used by PATTERN-BREAKER and DEEPDIVER, which legitimately
 * call {@code cov(P, D)} on the same pattern from multiple traversal paths. (PATTERN-COMBINER
 * doesn't need this; it builds coverage at level l-1 by summing coverages at level l.)
 *
 * <p>Caching is part of the algorithm's stated structure (see the Python reference's
 * {@code coverage_cache} in {@code top_down.py} and {@code deepdiver.py}). It is not the inverted
 * index speedup from Appendix A; the cached values are still computed by the plain
 * {@link Coverage#coverage} pass on first sight of each pattern.
 */
public final class CoverageCache {

    private final List<List<String>> dataset;
    private final Map<Pattern, Integer> cache = new HashMap<>();

    public CoverageCache(List<List<String>> dataset) {
        this.dataset = dataset;
    }

    public int coverage(Pattern pattern) {
        Integer cached = cache.get(pattern);
        if (cached != null) {
            return cached;
        }
        int value = Coverage.coverage(pattern, dataset);
        cache.put(pattern, value);
        return value;
    }

    public boolean isUncovered(Pattern pattern, int tau) {
        return coverage(pattern) < tau;
    }
}
