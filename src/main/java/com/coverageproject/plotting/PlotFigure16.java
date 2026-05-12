package com.coverageproject.plotting;

import com.coverageproject.experiments.Figure16;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Plots Figure 16: coverage-enhancement runtime vs threshold rate, one curve per method (Naive,
 * Greedy at level 2, Greedy at level 3, ...). Mirrors {@code plot_figure_16.py} with log-log axes.
 */
public final class PlotFigure16 {

    private PlotFigure16() {
        // utility class
    }

    public static Path plot(List<Figure16.Row> rows, String datasetName, Path outputDir) throws IOException {
        Map<String, List<Figure16.Row>> byMethod = new LinkedHashMap<>();
        for (Figure16.Row row : rows) {
            byMethod.computeIfAbsent(row.method(), k -> new java.util.ArrayList<>()).add(row);
        }
        for (List<Figure16.Row> seriesRows : byMethod.values()) {
            seriesRows.sort(Comparator.comparingInt(Figure16.Row::tau));
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        for (Map.Entry<String, List<Figure16.Row>> entry : byMethod.entrySet()) {
            XYSeries series = new XYSeries(entry.getKey());
            for (Figure16.Row row : entry.getValue()) {
                if (row.thresholdRate() > 0 && row.runtimeSeconds() > 0) {
                    // Log axes can't accept zero or negative.
                    series.add(row.thresholdRate(), row.runtimeSeconds());
                }
            }
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Fig. 16 (" + datasetName + "): Coverage Enhancement, Varying Threshold",
                "Threshold rate (τ/n)",
                "Runtime (s)",
                dataset,
                PlotOrientation.VERTICAL,
                true, false, false);

        XYPlot plot = chart.getXYPlot();
        plot.setDomainAxis(new LogAxis("Threshold rate (τ/n)"));
        plot.setRangeAxis(new LogAxis("Runtime (s)"));

        return PlotFigure10.saveChart(chart, outputDir, "Figure_16.png");
    }
}
