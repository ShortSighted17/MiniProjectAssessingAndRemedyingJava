package com.coverageproject.core;

import java.util.HashSet;
import java.util.Set;

/**
 * Index of discovered MUPs, used by DEEPDIVER for its dominance pruning step (Algorithm 3, line
 * "if P is dominated by M then continue").
 *
 * <p>This is the literal "walk the set" implementation: dominance is checked by scanning the
 * stored MUPs and applying {@link Patterns#dominates}. The paper's Appendix B describes a faster
 * inverted-index implementation; we intentionally don't use it here. The point of this port is to
 * mirror the paper's algorithmic structure, not to chase the appendix's micro-optimisations.
 */
public final class MupIndex {

    private final Set<Pattern> patterns = new HashSet<>();

    /** Add a discovered MUP. No-op if it's already present. */
    public void add(Pattern pattern) {
        patterns.add(pattern);
    }

    public Set<Pattern> patterns() {
        // Snapshot so callers can't accidentally mutate the index through the returned view.
        return new HashSet<>(patterns);
    }

    public int size() {
        return patterns.size();
    }

    /** True iff some already-discovered MUP dominates {@code pattern}. */
    public boolean dominatedByMup(Pattern pattern) {
        for (Pattern mup : patterns) {
            if (Patterns.dominates(mup, pattern)) {
                return true;
            }
        }
        return false;
    }

    /** True iff {@code pattern} dominates some already-discovered MUP. */
    public boolean dominatesMup(Pattern pattern) {
        for (Pattern mup : patterns) {
            if (Patterns.dominates(pattern, mup)) {
                return true;
            }
        }
        return false;
    }
}
