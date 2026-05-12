package com.coverageproject.plotting;

import com.coverageproject.experiments.Figure11;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Plots Figure 11: MUP-identification runtime vs threshold τ, one curve per algorithm.
 *
 * <p>The Python figure uses dual axes — a log-y line plot for runtime, with bar overlays showing
 * the MUP counts. We drop the dual axis (it's purely visual; the MUP counts are in the CSV) and
 * keep a single log-y runtime plot, which is the part of the figure that actually compares the
 * three algorithms.
 */
public final class PlotFigure11 {

    private PlotFigure11() {
        // utility class
    }

    public static Path plot(List<Figure11.Row> rows, String datasetName, Path outputDir) throws IOException {
        // Index runtimes by algorithm then τ. Preserve algorithm order of first appearance.
        Map<String, Map<Integer, Double>> byAlgorithm = new LinkedHashMap<>();
        TreeMap<Integer, Void> taus = new TreeMap<>();
        for (Figure11.Row row : rows) {
            byAlgorithm.computeIfAbsent(row.algorithm(), k -> new LinkedHashMap<>())
                    .put(row.tau(), row.runtimeSeconds());
            taus.put(row.tau(), null);
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        for (Map.Entry<String, Map<Integer, Double>> entry : byAlgorithm.entrySet()) {
            XYSeries series = new XYSeries(entry.getKey());
            for (int tau : taus.keySet()) {
                Double v = entry.getValue().get(tau);
                if (v != null && v > 0) {
                    // LogAxis cannot accept 0 or negatives, so we drop those points rather than
                    // letting the renderer crash on them.
                    series.add(tau, v);
                }
            }
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Fig. 11 (" + datasetName + "): MUP Identification, Varying Threshold",
                "Threshold (τ)",
                "Runtime (s)",
                dataset,
                PlotOrientation.VERTICAL,
                true, false, false);

        XYPlot plot = chart.getXYPlot();
        // Linear x: τ is an integer count, log scaling buys us nothing here.
        plot.setDomainAxis(new NumberAxis("Threshold (τ)"));
        // Log y: runtimes span orders of magnitude in the paper's plots.
        plot.setRangeAxis(new LogAxis("Runtime (s)"));

        return PlotFigure10.saveChart(chart, outputDir, "Figure_11.png");
    }
}
