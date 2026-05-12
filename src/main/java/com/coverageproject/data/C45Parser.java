package com.coverageproject.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the small C4.5-style {@code .names} metadata files that UCI datasets ship with.
 *
 * <p>Expected shape:
 *
 * <pre>
 * | comments start with pipe
 * class_a, class_b, class_c.
 * attr_name: value_a, value_b.
 * other_attr: x, y, z.
 * </pre>
 *
 * <p>This parser is intentionally narrow and replaces the same file in the Python project. It does
 * not try to handle every C4.5 names dialect; if a dataset needs something fancier, write a
 * dataset-specific loader.
 */
public final class C45Parser {

    private C45Parser() {
        // utility class
    }

    public record C45Metadata(List<String> classValues, Map<String, List<String>> domains) {}

    public static C45Metadata parse(Path path) throws IOException {
        List<String> classValues = new ArrayList<>();
        Map<String, List<String>> domains = new LinkedHashMap<>();

        for (String raw : Files.readAllLines(path)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("|")) {
                continue;
            }

            int colon = line.indexOf(':');
            if (colon >= 0) {
                String name = line.substring(0, colon).trim();
                String valuesText = line.substring(colon + 1);
                List<String> values = splitAndClean(valuesText);
                if (!values.isEmpty()) {
                    domains.put(name, values);
                }
            } else if (classValues.isEmpty()) {
                classValues = splitAndClean(line);
            }
        }

        if (classValues.isEmpty()) {
            throw new IllegalArgumentException(
                    "Could not find class values in C4.5 names file: " + path);
        }
        if (domains.isEmpty()) {
            throw new IllegalArgumentException(
                    "Could not find attribute domains in C4.5 names file: " + path);
        }
        return new C45Metadata(classValues, domains);
    }

    private static List<String> splitAndClean(String text) {
        List<String> result = new ArrayList<>();
        for (String token : text.split(",")) {
            // C4.5 names files terminate the value list with a period (".") on the same line.
            // Strip both surrounding whitespace and trailing periods.
            String cleaned = token.trim();
            while (cleaned.endsWith(".")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
            }
            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }
        return result;
    }
}
