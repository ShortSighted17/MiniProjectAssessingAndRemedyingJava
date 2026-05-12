package com.coverageproject.plotting;

import com.coverageproject.experiments.Figure10;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Plots Figure 10: overall accuracy, subgroup accuracy, and subgroup F1 vs. number of subgroup
 * rows added back to training. One PNG per chosen MUP. Mirrors the Python {@code plot_figure_10}.
 *
 * <p>The Python original uses a twin-axis matplotlib layout with a bar chart for F1 behind the
 * accuracy lines. JFreeChart can do that but the extra complexity is not worth it here; we draw
 * all three series on a single y-axis ranging over [0, 1], which is what all three metrics live in.
 */
public final class PlotFigure10 {

    private PlotFigure10() {
        // utility class
    }

    public static Path plot(Figure10.Run run, String datasetName, Path outputDir, String filename)
            throws IOException {
        if (filename == null) {
            filename = "Figure_10.png";
        }
        XYSeries overall = new XYSeries("Overall Accuracy");
        XYSeries subgroup = new XYSeries("Subgroup Accuracy");
        XYSeries f1 = new XYSeries("Subgroup F1");
        for (Figure10.Row row : run.rows()) {
            overall.add(row.subgroupTrainSize(), row.overallAccuracy());
            subgroup.add(row.subgroupTrainSize(), row.subgroupAccuracy());
            f1.add(row.subgroupTrainSize(), row.subgroupF1());
        }
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(overall);
        dataset.addSeries(subgroup);
        dataset.addSeries(f1);

        Figure10.Metadata md = run.metadata();
        String rankPart = " candidate #" + md.rank();
        String baselinePart = " — Omitted-subgroup F1: "
                + String.format("%.3f", md.selectionBaselineSubgroupF1());
        String title = "Fig. 10 (" + datasetName + rankPart
                + "): Effect of Lack of Coverage on Classification"
                + "  |  MUP: " + md.chosenMupString() + baselinePart;

        JFreeChart chart = ChartFactory.createXYLineChart(
                title,
                "Number of subgroup records added to training",
                "Accuracy / F1",
                dataset,
                PlotOrientation.VERTICAL,
                true,  // legend
                false, // tooltips
                false  // urls
        );
        return saveChart(chart, outputDir, filename);
    }

    static Path saveChart(JFreeChart chart, Path outputDir, String filename) throws IOException {
        Files.createDirectories(outputDir);
        Path outPath = outputDir.resolve(filename);
        ChartUtils.saveChartAsPNG(new File(outPath.toString()), chart, 800, 540);
        return outPath;
    }
}
