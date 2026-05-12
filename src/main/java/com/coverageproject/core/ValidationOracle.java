package com.coverageproject.core;

import java.util.List;

/**
 * Pluggable predicate that says whether a candidate row is semantically meaningful. Used by the
 * coverage-enhancement algorithms (GREEDY and NAIVE) to skip combinations that the user knows are
 * infeasible — for example "age < 20 AND marital_status = married" in the paper's COMPAS example.
 *
 * <p>The default oracle accepts every row. Override it via {@link #accept} when running an
 * experiment that needs the human-in-the-loop validation behaviour from § IV-C of the paper.
 */
@FunctionalInterface
public interface ValidationOracle {

    ValidationOracle ACCEPT_ALL = row -> true;

    boolean accept(List<String> row);
}
