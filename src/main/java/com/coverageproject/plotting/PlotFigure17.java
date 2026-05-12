package com.coverageproject.plotting;

import com.coverageproject.experiments.Figure17;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
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
 * Plots Figure 17: coverage-enhancement runtime vs number of attributes, one curve per level.
 * X-axis is linear (integer attribute count), Y-axis is logarithmic (runtimes span decades).
 */
public final class PlotFigure17 {

    private PlotFigure17() {
        // utility class
    }

    public static Path plot(List<Figure17.Row> rows, String datasetName, Path outputDir) throws IOException {
        TreeMap<Integer, List<Figure17.Row>> byLevel = new TreeMap<>();
        for (Figure17.Row row : rows) {
            byLevel.computeIfAbsent(row.level(), k -> new java.util.ArrayList<>()).add(row);
        }
        for (List<Figure17.Row> series : byLevel.values()) {
            series.sort(Comparator.comparingInt(Figure17.Row::dimensionCount));
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        for (Map.Entry<Integer, List<Figure17.Row>> entry : byLevel.entrySet()) {
            XYSeries series = new XYSeries("l=" + entry.getKey());
            for (Figure17.Row row : entry.getValue()) {
                if (row.runtimeSeconds() > 0) {
                    series.add(row.dimensionCount(), row.runtimeSeconds());
                }
            }
            dataset.addSeries(series);
        }

        int tau = rows.isEmpty() ? 0 : rows.get(0).tau();
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Fig. 17 (" + datasetName + "): Coverage Enhancement, Varying Dimensions (τ=" + tau + ")",
                "Dimensions",
                "Runtime (s)",
                dataset,
                PlotOrientation.VERTICAL,
                true, false, false);

        XYPlot plot = chart.getXYPlot();
        plot.setDomainAxis(new NumberAxis("Dimensions"));
        plot.setRangeAxis(new LogAxis("Runtime (s)"));

        return PlotFigure10.saveChart(chart, outputDir, "Figure_17.png");
    }
}
