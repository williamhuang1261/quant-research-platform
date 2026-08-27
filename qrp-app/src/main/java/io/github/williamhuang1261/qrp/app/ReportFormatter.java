package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;
import io.github.williamhuang1261.qrp.engine.Trade;
import io.github.williamhuang1261.qrp.stats.ConfidenceInterval;
import io.github.williamhuang1261.qrp.stats.MonteCarloSimulation;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
            String executionId,
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
                .append(row("time in market", unsignedPercent(metrics.timeInMarket())))
                .append(executionSection(result, executionId));

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

    /**
     * Fill rate and realized slippage vs. the reference price, computed the
     * same way regardless of which {@link io.github.williamhuang1261.qrp.engine.ExecutionModel}
     * produced the trades, so a {@code market-open} run and a {@code lob} run
     * on the same strategy and data are honestly comparable.
     *
     * <p>"Attempted" fills are counted from the exposure series rather than a
     * separate counter: {@code BacktestEngine} advances the held target every
     * time it hands a pending target to the execution model, whether or not
     * that model actually filled it, so a change in {@code exposure} between
     * consecutive bars is exactly one fill attempt.
     */
    private static String executionSection(BacktestResult result, String executionId) {
        DoubleSeries exposure = result.exposure();
        int attempts = 0;
        for (int i = 1; i < exposure.size(); i++) {
            if (exposure.get(i) != exposure.get(i - 1)) {
                attempts++;
            }
        }

        List<Trade> trades = result.trades();
        int filled = trades.size();
        double fillRate = attempts == 0 ? Double.NaN : (double) filled / attempts;

        double totalSlippage = 0.0;
        double referenceNotional = 0.0;
        for (Trade trade : trades) {
            totalSlippage += trade.slippageCost();
            referenceNotional += Math.abs(trade.shares()) * trade.referencePrice();
        }
        double averageSlippageBps = referenceNotional > 0.0
                ? totalSlippage / referenceNotional * 10_000.0
                : Double.NaN;

        return rule()
                .concat(String.format(Locale.ROOT, "  execution: %s%n", executionId))
                .concat(rule())
                .concat(row("fill attempts", String.valueOf(attempts)))
                .concat(row("fills completed", String.valueOf(filled)))
                .concat(row("fill rate", unsignedPercent(fillRate)))
                .concat(row("total slippage vs. reference", money(totalSlippage)))
                .concat(row("avg. slippage vs. reference", bps(averageSlippageBps)));
    }

    private static String bps(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.2f bps", value);
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
