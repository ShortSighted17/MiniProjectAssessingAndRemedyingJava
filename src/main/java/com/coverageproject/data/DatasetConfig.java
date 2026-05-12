package com.coverageproject.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-dataset configuration loaded from {@code <dataset_dir>/config.json}.
 *
 * <p>Mirrors the Python {@code DatasetConfig}. Each dataset has:
 *
 * <ul>
 *   <li>The data file name and how to read it (delimiter, header).
 *   <li>Column names, the feature columns, and the label column.
 *   <li>The allowed domain values for each feature column.
 *   <li>Optional figure-specific overrides (e.g. {@code tau_values} for Figure 11).
 *   <li>Optionally a C4.5 {@code .names} metadata file that supplies domains/labels.
 * </ul>
 */
public final class DatasetConfig {

    private final String name;
    private final String format;
    private final String dataFile;
    private final String delimiter;
    private final boolean hasHeader;
    private final List<String> columns;
    private final List<String> featureCols;
    private final String labelCol;
    private final Map<String, List<String>> domains;
    private final List<String> classValues;
    private final String metadataFile;
    private final String metadataFormat;
    private final Map<String, JsonNode> figure10;
    private final Map<String, JsonNode> figure11;
    private final Map<String, JsonNode> figure16;
    private final Map<String, JsonNode> figure17;

    private DatasetConfig(Builder b) {
        this.name = b.name;
        this.format = b.format;
        this.dataFile = b.dataFile;
        this.delimiter = b.delimiter;
        this.hasHeader = b.hasHeader;
        this.columns = List.copyOf(b.columns);
        this.featureCols = List.copyOf(b.featureCols);
        this.labelCol = b.labelCol;
        this.domains = Map.copyOf(b.domains);
        this.classValues = List.copyOf(b.classValues);
        this.metadataFile = b.metadataFile;
        this.metadataFormat = b.metadataFormat;
        this.figure10 = Map.copyOf(b.figure10);
        this.figure11 = Map.copyOf(b.figure11);
        this.figure16 = Map.copyOf(b.figure16);
        this.figure17 = Map.copyOf(b.figure17);
    }

    public String name() { return name; }
    public String format() { return format; }
    public String dataFile() { return dataFile; }
    public String delimiter() { return delimiter; }
    public boolean hasHeader() { return hasHeader; }
    public List<String> columns() { return columns; }
    public List<String> featureCols() { return featureCols; }
    public String labelCol() { return labelCol; }
    public Map<String, List<String>> domains() { return domains; }
    public List<String> classValues() { return classValues; }
    public String metadataFile() { return metadataFile; }
    public String metadataFormat() { return metadataFormat; }
    public Map<String, JsonNode> figure10() { return figure10; }
    public Map<String, JsonNode> figure11() { return figure11; }
    public Map<String, JsonNode> figure16() { return figure16; }
    public Map<String, JsonNode> figure17() { return figure17; }

    /**
     * Load {@code <datasetDir>/config.json}, optionally pulling domain/label/column info from a
     * C4.5 metadata file referenced by {@code metadata_file} + {@code metadata_format: "c45"}.
     */
    public static DatasetConfig load(Path datasetDir) throws IOException {
        Path configPath = datasetDir.resolve("config.json");
        if (!Files.exists(configPath)) {
            throw new java.io.FileNotFoundException("Missing dataset config: " + configPath);
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode raw = mapper.readTree(Files.readString(configPath));

        Builder b = new Builder();
        b.name = textOrDefault(raw, "name", datasetDir.getFileName().toString());
        b.format = textOrDefault(raw, "format", "uci_delimited");
        b.dataFile = requireText(raw, "data_file");
        b.delimiter = textOrDefault(raw, "delimiter", ",");
        b.hasHeader = raw.path("has_header").asBoolean(false);
        b.metadataFile = textOrNull(raw, "metadata_file");
        b.metadataFormat = textOrNull(raw, "metadata_format");

        // If the dataset advertises a C4.5 names file, fill in domains/columns/labels from it
        // before reading the rest of the config (the explicit fields override these defaults).
        if (b.metadataFile != null && "c45".equals(b.metadataFormat)) {
            C45Parser.C45Metadata metadata = C45Parser.parse(datasetDir.resolve(b.metadataFile));
            b.classValues.addAll(metadata.classValues());
            b.domains.putAll(metadata.domains());
            b.featureCols.addAll(metadata.domains().keySet());
            b.labelCol = "class";
            b.columns.addAll(metadata.domains().keySet());
            b.columns.add(b.labelCol);
        }

        // Explicit fields in config.json override anything filled in from metadata.
        if (raw.has("columns")) {
            b.columns.clear();
            raw.get("columns").forEach(n -> b.columns.add(n.asText()));
        }
        if (raw.has("feature_cols")) {
            b.featureCols.clear();
            raw.get("feature_cols").forEach(n -> b.featureCols.add(n.asText()));
        }
        if (raw.has("label_col")) {
            b.labelCol = raw.get("label_col").asText();
        }
        if (raw.has("domains")) {
            b.domains.clear();
            Iterator<Map.Entry<String, JsonNode>> it = raw.get("domains").fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                List<String> values = new ArrayList<>();
                entry.getValue().forEach(n -> values.add(n.asText()));
                b.domains.put(entry.getKey(), values);
            }
        }
        if (raw.has("class_values")) {
            b.classValues.clear();
            raw.get("class_values").forEach(n -> b.classValues.add(n.asText()));
        }

        b.figure10 = jsonObjectAsMap(raw.path("figure_10"));
        b.figure11 = jsonObjectAsMap(raw.path("figure_11"));
        b.figure16 = jsonObjectAsMap(raw.path("figure_16"));
        b.figure17 = jsonObjectAsMap(raw.path("figure_17"));

        return new DatasetConfig(b);
    }

    private static Map<String, JsonNode> jsonObjectAsMap(JsonNode node) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        if (node == null || node.isMissingNode() || node instanceof MissingNode || !node.isObject()) {
            return result;
        }
        node.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            throw new IllegalArgumentException("Required field missing in config.json: " + field);
        }
        return child.asText();
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? fallback : child.asText();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    private static final class Builder {
        String name;
        String format = "uci_delimited";
        String dataFile;
        String delimiter = ",";
        boolean hasHeader;
        List<String> columns = new ArrayList<>();
        List<String> featureCols = new ArrayList<>();
        String labelCol = "";
        Map<String, List<String>> domains = new LinkedHashMap<>();
        List<String> classValues = new ArrayList<>();
        String metadataFile;
        String metadataFormat;
        Map<String, JsonNode> figure10 = new LinkedHashMap<>();
        Map<String, JsonNode> figure11 = new LinkedHashMap<>();
        Map<String, JsonNode> figure16 = new LinkedHashMap<>();
        Map<String, JsonNode> figure17 = new LinkedHashMap<>();
    }
}
