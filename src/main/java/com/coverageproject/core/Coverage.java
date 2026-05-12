package com.coverageproject.core;

import java.util.List;

/**
 * Coverage of a pattern over a dataset, computed as Definition 2 of the paper: the number of
 * tuples (rows) in the dataset that match the pattern.
 *
 * <p>This implementation is a direct pass over the dataset, exactly as the paper writes the
 * definition. The paper's appendices and the original Python project both describe inverted-index
 * speedups (bit vectors per (column, value)); we deliberately omit them here because the goal of
 * this port is to replicate the paper's algorithms verbatim, not to outperform them.
 */
public final class Coverage {

    private Coverage() {
        // utility class
    }

    /** Number of rows in {@code dataset} that match {@code pattern}. */
    public static int coverage(Pattern pattern, List<List<String>> dataset) {
        int count = 0;
        for (List<String> row : dataset) {
            if (pattern.matchesRow(row)) {
                count++;
            }
        }
        return count;
    }

    /** Whether {@code pattern}'s coverage is strictly less than {@code tau}. */
    public static boolean isUncovered(Pattern pattern, List<List<String>> dataset, int tau) {
        return coverage(pattern, dataset) < tau;
    }
}
