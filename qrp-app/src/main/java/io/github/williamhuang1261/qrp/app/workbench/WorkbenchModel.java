package io.github.williamhuang1261.qrp.app.workbench;

import io.github.williamhuang1261.qrp.app.BacktestRunner;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;
import io.github.williamhuang1261.qrp.engine.Trade;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything the workbench draws, computed without touching JavaFX.
 *
 * <p>Separating this from the view is what makes the screen testable: a JavaFX
 * test needs a toolkit, a display and a test harness, while the question worth
 * asking — are these the right points and the right labels — needs none of them.
 */
public final class WorkbenchModel {

    /** Beyond this, more points cost rendering time and add no visible detail. */
    public static final int MAX_CHART_POINTS = 1_500;

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    public record Point(String label, double value) {
    }

    public record MetricRow(String name, String value) {
    }

    private final BacktestRunner.Outcome outcome;

    public WorkbenchModel(BacktestRunner.Outcome outcome) {
        this.outcome = outcome;
    }

    public BacktestRunner.Outcome outcome() {
        return outcome;
    }

    public String title() {
        BacktestResult result = outcome.result();
        return String.format(Locale.ROOT, "%s on %s %s",
                outcome.strategyId(), result.series().instrument(), result.series().timeframe().id());
    }

    public String subtitle() {
        BacktestResult result = outcome.result();
        return String.format(Locale.ROOT, "%d bars, %s to %s, compute engine: %s",
                result.series().size(),
                DATE.format(result.series().start()),
                DATE.format(result.series().end()),
                outcome.engineId());
    }

    public List<Point> equityPoints() {
        double[] equity = outcome.result().equityCurve().toArray();
        return sample(equity, value -> value);
    }

    /** Percentage below the running peak, the shape people actually read. */
    public List<Point> drawdownPoints() {
        double[] equity = outcome.result().equityCurve().toArray();
        double[] drawdown = new double[equity.length];
        double peak = equity.length == 0 ? Double.NaN : equity[0];
        for (int i = 0; i < equity.length; i++) {
            peak = Math.max(peak, equity[i]);
            drawdown[i] = -100.0 * (peak - equity[i]) / peak;
        }
        return sample(drawdown, value -> value);
    }

    public List<MetricRow> metricRows() {
        PerformanceMetrics metrics = outcome.result().metrics();
        List<MetricRow> rows = new ArrayList<>(List.of(
                new MetricRow("Final equity", money(metrics.finalEquity())),
                new MetricRow("Total return", signedPercent(metrics.totalReturn())),
                new MetricRow("CAGR", signedPercent(metrics.cagr())),
                new MetricRow("Volatility (ann.)", percent(metrics.annualisedVolatility())),
                new MetricRow("Sharpe", ratio(metrics.sharpeRatio())),
                new MetricRow("Max drawdown", percent(metrics.maxDrawdown())),
                new MetricRow("Trades", String.valueOf(metrics.tradeCount())),
                new MetricRow("Time in market", percent(metrics.timeInMarket()))));

        outcome.monteCarlo().ifPresent(report -> {
            rows.add(new MetricRow("— Monte Carlo —", report.paths() + " paths"));
            rows.add(new MetricRow("Median final", money(report.medianFinalEquity())));
            rows.add(new MetricRow("Drawdown p2.5–p97.5",
                    percent(report.maxDrawdown().lower()) + " – " + percent(report.maxDrawdown().upper())));
            rows.add(new MetricRow("P(loss)", percent(report.probabilityOfLoss())));
        });
        return List.copyOf(rows);
    }

    /** Fills, for marking on the equity chart. */
    public List<Trade> trades() {
        return outcome.result().trades();
    }

    private List<Point> sample(double[] values, java.util.function.DoubleUnaryOperator transform) {
        int stride = Math.max(1, values.length / MAX_CHART_POINTS);
        List<Point> points = new ArrayList<>(values.length / stride + 1);
        for (int i = 0; i < values.length; i += stride) {
            points.add(new Point(
                    DATE.format(outcome.result().series().get(i).timestamp()),
                    transform.applyAsDouble(values[i])));
        }
        return points;
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%,.0f", value);
    }

    private static String signedPercent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%+.2f%%", value * 100.0);
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static String ratio(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%+.2f", value);
    }
}
