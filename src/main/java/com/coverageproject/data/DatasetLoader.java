package com.coverageproject.data;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reads a dataset directory and returns a fully-populated {@link DatasetBundle}. Mirrors the
 * Python {@code load_dataset_bundle}: it reads the data file according to the dataset's
 * {@link DatasetConfig}, validates that every observed feature value appears in the configured
 * domain, and packages everything up for the algorithms and experiments.
 */
public final class DatasetLoader {

    private DatasetLoader() {
        // utility class
    }

    public static DatasetBundle load(String datasetNameOrDir, Path datasetsRoot) throws IOException {
        Path candidate = Path.of(datasetNameOrDir);
        Path datasetDir = Files.exists(candidate) ? candidate : datasetsRoot.resolve(datasetNameOrDir);
        DatasetConfig config = DatasetConfig.load(datasetDir);

        if (!"uci_delimited".equals(config.format())) {
            throw new IllegalArgumentException(
                    "Unsupported dataset format '" + config.format() + "'. Add a reader in "
                            + "DatasetLoader.java when a new format is needed.");
        }

        List<List<String>> rawRows = readDelimited(datasetDir, config);
        validateAgainstDomains(rawRows, config);

        // Extract feature columns and the label column in the order the config defines them.
        List<List<String>> featureRows = new ArrayList<>(rawRows.size());
        List<String> labels = new ArrayList<>(rawRows.size());
        for (List<String> row : rawRows) {
            List<String> features = new ArrayList<>(config.featureCols().size());
            for (String col : config.featureCols()) {
                features.add(row.get(config.columns().indexOf(col)));
            }
            featureRows.add(List.copyOf(features));
            labels.add(row.get(config.columns().indexOf(config.labelCol())));
        }

        List<List<String>> domains = new ArrayList<>(config.featureCols().size());
        for (String col : config.featureCols()) {
            List<String> values = config.domains().get(col);
            if (values == null) {
                throw new IllegalArgumentException(
                        "Missing domain definition for feature column: " + col);
            }
            domains.add(List.copyOf(values));
        }

        return new DatasetBundle(
                config,
                List.copyOf(featureRows),
                List.copyOf(labels),
                List.copyOf(domains),
                List.copyOf(config.featureCols()),
                config.labelCol(),
                datasetDir,
                List.copyOf(rawRows));
    }

    private static List<List<String>> readDelimited(Path datasetDir, DatasetConfig config) throws IOException {
        Path path = datasetDir.resolve(config.dataFile());
        if (!Files.exists(path)) {
            throw new java.io.FileNotFoundException("Missing dataset file: " + path);
        }

        char delim = config.delimiter().charAt(0);
        CSVFormat.Builder formatBuilder = CSVFormat.DEFAULT.builder().setDelimiter(delim);
        if (config.hasHeader()) {
            formatBuilder.setHeader().setSkipHeaderRecord(true);
        }
        CSVFormat format = formatBuilder.build();

        List<List<String>> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path);
                CSVParser parser = CSVParser.parse(reader, format)) {
            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>(record.size());
                for (int i = 0; i < record.size(); i++) {
                    row.add(record.get(i).trim());
                }
                rows.add(List.copyOf(row));
            }
        }

        // Sanity check: every row should have at least as many columns as the config promises. We
        // don't pad short rows; the original Python project would fail later if columns are missing.
        if (!rows.isEmpty() && rows.get(0).size() < config.columns().size()) {
            throw new IllegalArgumentException(
                    "Dataset has fewer columns than expected: got " + rows.get(0).size()
                            + ", expected " + config.columns().size());
        }
        return rows;
    }

    private static void validateAgainstDomains(List<List<String>> rows, DatasetConfig config) {
        List<String> problems = new ArrayList<>();
        for (String col : config.featureCols()) {
            int colIndex = config.columns().indexOf(col);
            Set<String> observed = new HashSet<>();
            for (List<String> row : rows) {
                observed.add(row.get(colIndex));
            }
            Set<String> allowed = new HashSet<>(config.domains().get(col));
            List<String> extra = observed.stream()
                    .filter(v -> !allowed.contains(v))
                    .sorted()
                    .collect(Collectors.toList());
            if (!extra.isEmpty()) {
                List<String> preview = extra.subList(0, Math.min(10, extra.size()));
                problems.add(col + ": observed values not listed in config domain: " + preview);
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dataset contains feature values that are not listed in config.json domains.\n"
                            + "Fix the config domains or clean the dataset before running.\n"
                            + String.join("\n", problems));
        }
    }
}
