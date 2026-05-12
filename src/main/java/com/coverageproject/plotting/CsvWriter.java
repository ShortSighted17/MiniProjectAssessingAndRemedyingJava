package com.coverageproject.plotting;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tiny CSV writer used by the experiment runners to dump their tabular output alongside the PNG
 * plots. Mirrors what the Python project's {@code pd.DataFrame.to_csv} produces: comma-separated
 * fields, header row, quote fields that contain commas or quotes.
 */
public final class CsvWriter {

    private CsvWriter() {
        // utility class
    }

    public static void write(Path path, List<String> header, List<List<String>> rows) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write(joinAndQuote(header));
            w.newLine();
            for (List<String> row : rows) {
                w.write(joinAndQuote(row));
                w.newLine();
            }
        }
    }

    private static String joinAndQuote(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quoteIfNeeded(fields.get(i)));
        }
        return sb.toString();
    }

    private static String quoteIfNeeded(String field) {
        if (field == null) {
            return "";
        }
        boolean needs = field.indexOf(',') >= 0
                || field.indexOf('"') >= 0
                || field.indexOf('\n') >= 0
                || field.indexOf('\r') >= 0;
        if (!needs) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }
}
