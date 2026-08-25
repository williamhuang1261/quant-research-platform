package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;
import io.github.williamhuang1261.qrp.stats.ConfidenceInterval;
import io.github.williamhuang1261.qrp.stats.MonteCarloSimulation;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Renders a run as plain text.
 *
 * <p>Fixed width and no colour, so the output pastes into a ticket unchanged. The
 * caveats at the bottom are part of the report rather than a footnote in the
 * README: a metrics table that travels without them is a table someone will quote
 * without them.
 */
public final class ReportFormatter {

    /** Dates, not instants: the workbench prints these, and the two must agree. */
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private ReportFormatter() {
    }

    public static String format(
            BacktestResult result,
            String strategyId,
            String engineId,
            Optional<MonteCarloSimulation.Report> monteCarlo) {

        BarSeries series = result.series();
        PerformanceMetrics metrics = result.metrics();
        StringBuilder out = new StringBuilder();

        out.append(rule())
                .append(String.format(Locale.ROOT, "  %s on %s %s%n",
                        strategyId, series.instrument(), series.timeframe().id()))
                .append(String.format(Locale.ROOT, "  %d bars, %s to %s, compute engine: %s%n",
                        series.size(), date(series.start()), date(series.end()), engineId))
                .append(rule())
                .append(row("initial equity", money(metrics.initialEquity())))
                .append(row("final equity", money(metrics.finalEquity())))
                .append(row("total return", percent(metrics.totalReturn())))
                .append(row("CAGR", percent(metrics.cagr())))
                .append(row("annualised volatility", unsignedPercent(metrics.annualisedVolatility())))
                .append(row("Sharpe ratio", ratio(metrics.sharpeRatio())))
                .append(row("max drawdown", unsignedPercent(metrics.maxDrawdown())))
                .append(row("trades", String.valueOf(metrics.tradeCount())))
                .append(row("time in market", unsignedPercent(metrics.timeInMarket())));

        monteCarlo.ifPresent(report -> out.append(rule())
                .append(String.format(Locale.ROOT, "  Monte Carlo: %d resampled paths%n", report.paths()))
                .append(rule())
                .append(row("median final equity", money(report.medianFinalEquity())))
                .append(row("final equity " + level(report.finalEquity()), interval(report.finalEquity())))
                .append(row("max drawdown " + level(report.maxDrawdown()),
                        percentInterval(report.maxDrawdown())))
                .append(row("probability of loss", unsignedPercent(report.probabilityOfLoss()))));

        return out.append(rule())
                .append("  Costs are modelled; financing, borrow and taxes are not.\n")
                .append("  Resampled paths reorder the observed returns: they describe this\n")
                .append("  strategy on this history, not its behaviour on unseen data.\n")
                .append(rule())
                .toString();
    }

    private static String date(Instant instant) {
        return DATE.format(instant);
    }

    private static String rule() {
        return "  " + "-".repeat(60) + "\n";
    }

    private static String row(String label, String value) {
        return String.format(Locale.ROOT, "  %-28s %31s%n", label, value);
    }

    private static String level(ConfidenceInterval interval) {
        return String.format(Locale.ROOT, "(%.0f%%)", interval.level() * 100.0);
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%,.2f", value);
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%+.2f%%", value * 100.0);
    }

    /** For quantities that have no direction: a drawdown is never "+24 %". */
    private static String unsignedPercent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static String ratio(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%+.2f", value);
    }

    private static String interval(ConfidenceInterval interval) {
        return money(interval.lower()) + " .. " + money(interval.upper());
    }

    private static String percentInterval(ConfidenceInterval interval) {
        return String.format(Locale.ROOT, "%.2f%% .. %.2f%%",
                interval.lower() * 100.0, interval.upper() * 100.0);
    }
}
