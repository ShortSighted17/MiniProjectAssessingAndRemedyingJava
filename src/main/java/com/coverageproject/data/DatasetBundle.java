package com.coverageproject.data;

import java.nio.file.Path;
import java.util.List;

/**
 * In-memory representation of a loaded dataset. Carries everything the algorithms and experiments
 * need: the original {@link DatasetConfig}, the feature rows (each row is a fixed-order list of
 * strings matching {@link DatasetConfig#featureCols()}), the per-column domains in the same order,
 * the label column extracted into its own list, and the directory the dataset was loaded from.
 *
 * <p>Rows are stored as immutable {@code List<String>} so they can safely be used as map keys
 * (e.g. inside PATTERN-COMBINER's leaf-count map, and in equality-based deduplication).
 */
public record DatasetBundle(
        DatasetConfig config,
        List<List<String>> dataset,
        List<String> labels,
        List<List<String>> domains,
        List<String> featureCols,
        String labelCol,
        Path datasetDir,
        List<List<String>> rawRowsWithLabel) {

    public String name() {
        return config.name();
    }

    public int n() {
        return dataset.size();
    }

    public int d() {
        return domains.size();
    }
}
