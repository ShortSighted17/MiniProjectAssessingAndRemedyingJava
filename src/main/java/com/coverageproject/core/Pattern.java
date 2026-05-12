package com.coverageproject.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * A pattern in the sense of the paper: a tuple of cells, where each cell is either a domain value
 * ("deterministic") or {@code null} ("non-deterministic", written {@code X} in the paper).
 *
 * <p>Patterns are immutable and compared structurally. They are used as map/set keys throughout the
 * algorithms, so {@link #equals(Object)} and {@link #hashCode()} are based on the cell contents.
 */
public final class Pattern {
    /**
     * Marker for a non-deterministic cell. Stored as a Java {@code null} inside the cells list to
     * mirror Python's {@code X = None}.
     */
    public static final String X = null;

    private final List<String> cells;
    private final int hash;

    public Pattern(List<String> cells) {
        // Defensive copy: we never want a caller mutating the cells from under us, because
        // pattern_set membership depends on the hash matching the cells.
        this.cells = Collections.unmodifiableList(new ArrayList<>(cells));
        this.hash = this.cells.hashCode();
    }

    public static Pattern of(String... cells) {
        return new Pattern(Arrays.asList(cells));
    }

    public int size() {
        return cells.size();
    }

    public String get(int index) {
        return cells.get(index);
    }

    public List<String> cells() {
        return cells;
    }

    /** Number of deterministic cells (the paper's level {@code l(P)}). */
    public int level() {
        int count = 0;
        for (String cell : cells) {
            if (cell != X) {
                count++;
            }
        }
        return count;
    }

    /** Compact string form used in logs and plots: "0X1", "XX", etc. */
    public String asString() {
        StringBuilder sb = new StringBuilder(cells.size());
        for (String cell : cells) {
            sb.append(cell == X ? "X" : cell);
        }
        return sb.toString();
    }

    /**
     * Return a map of deterministic cells keyed by the corresponding column name. Used to render
     * patterns in human-readable form in Figure 10's logs and CSV.
     */
    public Map<String, String> asNamedMap(List<String> columns) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i) != X) {
                result.put(columns.get(i), cells.get(i));
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pattern other)) {
            return false;
        }
        // We check the cached hash first because most non-equal pairs will have mismatching hashes
        // and we want set lookups (which call equals() on hash collisions) to short-circuit.
        return hash == other.hash && cells.equals(other.cells);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return asString();
    }

    /** Build a copy of this pattern with cell {@code index} replaced by {@code value}. */
    public Pattern withCell(int index, String value) {
        ArrayList<String> copy = new ArrayList<>(cells);
        copy.set(index, value);
        return new Pattern(copy);
    }

    /** Pattern of length {@code d} consisting entirely of X. */
    public static Pattern allX(int d) {
        ArrayList<String> cells = new ArrayList<>(d);
        for (int i = 0; i < d; i++) {
            cells.add(X);
        }
        return new Pattern(cells);
    }

    /**
     * True iff {@code row} matches {@code this}, i.e. equals it on every deterministic cell. The
     * argument has length d and never contains X.
     */
    public boolean matchesRow(List<String> row) {
        Objects.requireNonNull(row, "row");
        if (row.size() != cells.size()) {
            throw new IllegalArgumentException(
                    "Row size " + row.size() + " does not match pattern size " + cells.size());
        }
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i);
            if (cell != X && !cell.equals(row.get(i))) {
                return false;
            }
        }
        return true;
    }
}
