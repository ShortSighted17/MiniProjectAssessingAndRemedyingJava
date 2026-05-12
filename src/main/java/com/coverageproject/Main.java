package com.coverageproject;

import com.coverageproject.data.DatasetBundle;
import com.coverageproject.data.DatasetLoader;
import com.coverageproject.experiments.Figure10;
import com.coverageproject.experiments.Figure11;
import com.coverageproject.experiments.Figure16;
import com.coverageproject.experiments.Figure17;
import com.coverageproject.plotting.CsvWriter;
import com.coverageproject.plotting.PlotFigure10;
import com.coverageproject.plotting.PlotFigure11;
import com.coverageproject.plotting.PlotFigure16;
import com.coverageproject.plotting.PlotFigure17;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Entry point. Asks the user for a dataset directory and a set of figures to generate, then runs
 * the chosen experiments and writes CSVs + PNGs to {@code <dataset_name>_graphs}. Mirrors the
 * Python {@code main.py}.
 *
 * <p>Usage:
 *
 * <pre>
 *   java -jar coverage-project.jar [dataset_dir [figures]]
 * </pre>
 *
 * <p>Both arguments are optional and prompted for when missing. {@code figures} is a
 * comma/space-separated list of {@code 10}, {@code 11}, {@code 16}, {@code 17} or the literal
 * string {@code all}.
 */
public final class Main {

    private static final Set<String> VALID_FIGURES = Set.of("10", "11", "16", "17");

    private Main() {
        // entry point
    }

    public static void main(String[] args) throws Exception {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        String datasetName = args.length >= 1 ? args[0] : prompt(stdin, "Enter dataset directory name: ");
        if (datasetName == null || datasetName.isBlank()) {
            throw new IllegalArgumentException("Dataset directory name cannot be empty.");
        }

        String figureSelectionRaw;
        if (args.length >= 2) {
            figureSelectionRaw = args[1];
        } else {
            System.out.println("Which graphs do you want to generate?");
            System.out.println("  Enter one or more of: 10, 11, 16, 17");
            System.out.println("  Examples: 10 11    or    10,16,17    or    all");
            figureSelectionRaw = prompt(stdin, "Graphs to generate [all]: ");
        }
        Set<String> selectedFigures = parseFigureSelection(figureSelectionRaw == null ? "" : figureSelectionRaw);

        DatasetBundle bundle = DatasetLoader.load(datasetName.trim(), Path.of("datasets"));
        Path outputDir = Path.of(bundle.name() + "_graphs");

        System.out.println("Loaded dataset '" + bundle.name() + "': n=" + bundle.n() + ", d=" + bundle.d());
        System.out.println("Feature columns: " + bundle.featureCols());
        System.out.println("Label column: " + bundle.labelCol());
        System.out.println("Output directory: " + outputDir);
        System.out.println("Selected graphs: " + new TreeSet<>(selectedFigures));

        if (selectedFigures.contains("10")) {
            System.out.println("\n=== Running Figure 10 ===");
            Figure10.RunResult result = Figure10.run(bundle, true);

            // Summary CSV: one row per screened candidate, with named pattern columns appended at
            // the end. We collect all pattern keys observed across all summary rows to build the
            // header.
            Set<String> patternKeys = new java.util.LinkedHashSet<>();
            for (Figure10.SummaryRow s : result.summary()) {
                patternKeys.addAll(s.patternNamed().keySet());
            }
            List<String> header = new ArrayList<>(List.of(
                    "rank", "pattern", "level", "coverage",
                    "baseline_overall_accuracy",
                    "baseline_subgroup_accuracy",
                    "baseline_subgroup_f1"));
            for (String key : patternKeys) {
                header.add("pattern_" + key);
            }
            List<List<String>> csvRows = new ArrayList<>();
            for (Figure10.SummaryRow s : result.summary()) {
                List<String> row = new ArrayList<>();
                row.add(Integer.toString(s.rank()));
                row.add(s.pattern());
                row.add(Integer.toString(s.level()));
                row.add(Integer.toString(s.coverage()));
                row.add(format(s.baselineOverallAccuracy()));
                row.add(format(s.baselineSubgroupAccuracy()));
                row.add(format(s.baselineSubgroupF1()));
                for (String key : patternKeys) {
                    row.add(s.patternNamed().getOrDefault(key, ""));
                }
                csvRows.add(row);
            }
            Path summaryPath = outputDir.resolve("Figure_10_subgroup_selection.csv");
            CsvWriter.write(summaryPath, header, csvRows);
            System.out.println("  Saved: " + summaryPath);

            for (int i = 0; i < result.runs().size(); i++) {
                String filename = i == 0 ? "Figure_10.png"
                        : String.format("Figure_10_candidate_%02d.png", i + 1);
                Path path = PlotFigure10.plot(result.runs().get(i), bundle.name(), outputDir, filename);
                System.out.println("  Saved: " + path);
            }
        }

        if (selectedFigures.contains("11")) {
            System.out.println("\n=== Running Figure 11 ===");
            List<Figure11.Row> result = Figure11.run(bundle, true);
            Path csvPath = outputDir.resolve("Figure_11.csv");
            CsvWriter.write(csvPath,
                    List.of("tau", "threshold_rate", "algorithm", "runtime_seconds", "mup_count"),
                    result.stream().map(r -> List.of(
                            Integer.toString(r.tau()),
                            format(r.thresholdRate()),
                            r.algorithm(),
                            format(r.runtimeSeconds()),
                            Integer.toString(r.mupCount()))).collect(Collectors.toList()));
            System.out.println("  Saved: " + csvPath);
            Path path = PlotFigure11.plot(result, bundle.name(), outputDir);
            System.out.println("  Saved: " + path);
        }

        if (selectedFigures.contains("16")) {
            System.out.println("\n=== Running Figure 16 ===");
            List<Figure16.Row> result = Figure16.run(bundle, true);
            Path csvPath = outputDir.resolve("Figure_16.csv");
            CsvWriter.write(csvPath,
                    List.of("tau", "threshold_rate", "method", "level", "runtime_seconds", "num_added_rows"),
                    result.stream().map(r -> List.of(
                            Integer.toString(r.tau()),
                            format(r.thresholdRate()),
                            r.method(),
                            Integer.toString(r.level()),
                            format(r.runtimeSeconds()),
                            Integer.toString(r.numAddedRows()))).collect(Collectors.toList()));
            System.out.println("  Saved: " + csvPath);
            Path path = PlotFigure16.plot(result, bundle.name(), outputDir);
            System.out.println("  Saved: " + path);
        }

        if (selectedFigures.contains("17")) {
            System.out.println("\n=== Running Figure 17 ===");
            List<Figure17.Row> result = Figure17.run(bundle, true);
            Path csvPath = outputDir.resolve("Figure_17.csv");
            CsvWriter.write(csvPath,
                    List.of("dimension_count", "tau", "level", "runtime_seconds", "num_added_rows"),
                    result.stream().map(r -> List.of(
                            Integer.toString(r.dimensionCount()),
                            Integer.toString(r.tau()),
                            Integer.toString(r.level()),
                            format(r.runtimeSeconds()),
                            Integer.toString(r.numAddedRows()))).collect(Collectors.toList()));
            System.out.println("  Saved: " + csvPath);
            Path path = PlotFigure17.plot(result, bundle.name(), outputDir);
            System.out.println("  Saved: " + path);
        }

        System.out.println("\nDone.");
    }

    private static String prompt(BufferedReader stdin, String message) throws java.io.IOException {
        System.out.print(message);
        System.out.flush();
        return stdin.readLine();
    }

    private static Set<String> parseFigureSelection(String raw) {
        String cleaned = raw.trim().toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty() || "all".equals(cleaned)) {
            return new TreeSet<>(VALID_FIGURES);
        }
        Set<String> selected = new TreeSet<>();
        for (String part : cleaned.replace(",", " ").split("\\s+")) {
            if (!part.isBlank()) {
                selected.add(part);
            }
        }
        Set<String> invalid = new TreeSet<>(selected);
        invalid.removeAll(VALID_FIGURES);
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown graph selection: " + invalid + ". Valid choices are 10, 11, 16, 17, or all.");
        }
        return selected;
    }

    private static String format(double value) {
        // Keep numeric output locale-independent so the CSV is round-trippable everywhere.
        return String.format(Locale.ROOT, "%.10g", value);
    }
}
