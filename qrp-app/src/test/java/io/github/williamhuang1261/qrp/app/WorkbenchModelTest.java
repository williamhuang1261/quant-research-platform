package io.github.williamhuang1261.qrp.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.app.workbench.WorkbenchModel;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The workbench's numbers, checked without a display. Everything a JavaFX test
 * would need a toolkit for is in the view; everything worth asserting is here.
 */
class WorkbenchModelTest {

    private static WorkbenchModel model;

    @BeforeAll
    static void runBacktest() {
        model = new WorkbenchModel(BacktestRunner.run(CliArguments.parse(
                List.of("--data", "../data/sample", "--paths", "500", "--seed", "3"))));
    }

    @Test
    @DisplayName("labels name the strategy, the instrument and the engine that ran")
    void labelsDescribeTheRun() {
        assertEquals("sma-crossover on SYNA.USD 1d", model.title());
        assertTrue(model.subtitle().contains("504 bars"), model.subtitle());
        assertTrue(model.subtitle().contains("2022-01-03"), model.subtitle());
        assertTrue(model.subtitle().contains("compute engine:"), model.subtitle());
    }

    @Test
    @DisplayName("chart points are capped, and both charts stay aligned")
    void chartPointsAreCappedAndAligned() {
        List<WorkbenchModel.Point> equity = model.equityPoints();
        List<WorkbenchModel.Point> drawdown = model.drawdownPoints();

        assertTrue(equity.size() <= WorkbenchModel.MAX_CHART_POINTS, "points: " + equity.size());
        assertEquals(equity.size(), drawdown.size());
        assertEquals(equity.get(0).label(), drawdown.get(0).label());
    }

    @Test
    @DisplayName("drawdown is plotted as a non-positive percentage")
    void drawdownIsNegativeOrZero() {
        for (WorkbenchModel.Point point : model.drawdownPoints()) {
            assertTrue(point.value() <= 0.0, "positive drawdown at " + point.label());
            assertTrue(point.value() >= -100.0);
        }
        assertEquals(0.0, model.drawdownPoints().get(0).value(), 1e-12);
    }

    @Test
    @DisplayName("the metrics table carries the run and its Monte Carlo rows")
    void metricRowsCoverTheRun() {
        List<WorkbenchModel.MetricRow> rows = model.metricRows();
        String names = rows.stream().map(WorkbenchModel.MetricRow::name).toList().toString();

        assertTrue(names.contains("Final equity"), names);
        assertTrue(names.contains("Max drawdown"), names);
        assertTrue(names.contains("P(loss)"), names);
        assertTrue(rows.stream().anyMatch(row -> row.value().equals("92,229")), rows.toString());
        assertTrue(rows.stream().anyMatch(row -> row.value().equals("500 paths")), rows.toString());
    }

    @Test
    @DisplayName("drawdown percentages are unsigned, returns are signed")
    void signsMatchTheQuantity() {
        List<WorkbenchModel.MetricRow> rows = model.metricRows();

        assertTrue(value(rows, "Total return").startsWith("-"), rows.toString());
        assertTrue(!value(rows, "Max drawdown").startsWith("+"), rows.toString());
    }

    private static String value(List<WorkbenchModel.MetricRow> rows, String name) {
        return rows.stream().filter(row -> row.name().equals(name)).findFirst().orElseThrow().value();
    }
}
