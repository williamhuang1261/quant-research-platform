package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.app.BacktestRunner;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;

/**
 * A run's summary, over the wire. Deliberately not a serialization of
 * {@link io.github.williamhuang1261.qrp.engine.BacktestResult}: the wire
 * format is a stable, hand-picked summary (the same numbers the CLI's report
 * prints) rather than whatever shape the internal result record happens to
 * have this month.
 */
public record RunResponse(
        String strategyId,
        String engineId,
        String executionId,
        double initialEquity,
        double finalEquity,
        double totalReturn,
        double cagr,
        double annualisedVolatility,
        double sharpeRatio,
        double maxDrawdown,
        int tradeCount,
        double timeInMarket,
        double[] equityCurve) {

    static RunResponse from(BacktestRunner.Outcome outcome) {
        PerformanceMetrics metrics = outcome.result().metrics();
        return new RunResponse(
                outcome.strategyId(),
                outcome.engineId(),
                outcome.executionId(),
                metrics.initialEquity(),
                metrics.finalEquity(),
                metrics.totalReturn(),
                metrics.cagr(),
                metrics.annualisedVolatility(),
                metrics.sharpeRatio(),
                metrics.maxDrawdown(),
                metrics.tradeCount(),
                metrics.timeInMarket(),
                outcome.result().equityCurve().toArray());
    }
}
