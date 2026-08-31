package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.app.BacktestRunner;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;
import io.github.williamhuang1261.qrp.warehouse.BacktestRunRecord;

/**
 * A run's summary, over the wire. Deliberately not a serialization of
 * {@link io.github.williamhuang1261.qrp.engine.BacktestResult}: the wire
 * format is a stable, hand-picked summary (the same numbers the CLI's report
 * prints) rather than whatever shape the internal result record happens to
 * have this month.
 *
 * <p>{@code id} and {@code cached} are new: every run this record describes
 * is now a persisted {@code fact_backtest_run} row, so the same shape serves
 * a fresh computation, a cache hit on an identical repeat request, and a
 * {@code GET /api/runs/{id}} lookup -- {@link #from(BacktestRunRecord,
 * String, boolean)} builds it from the persisted row directly for the latter
 * two. {@code strategyId} is passed in rather than read off the record
 * because a record only carries {@code dim_strategy}'s numeric foreign key;
 * the caller already knows the string id (from the request, for a cache hit,
 * or from a {@code StrategyDimensionRepository} lookup, for a {@code GET}).
 */
public record RunResponse(
        long id,
        boolean cached,
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

    static RunResponse from(BacktestRunner.Outcome outcome, long id, boolean cached) {
        PerformanceMetrics metrics = outcome.result().metrics();
        return new RunResponse(
                id,
                cached,
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

    static RunResponse from(BacktestRunRecord record, String strategyId, boolean cached) {
        return new RunResponse(
                record.id(),
                cached,
                strategyId,
                record.engineId(),
                record.executionModel(),
                record.initialEquity(),
                record.finalEquity(),
                record.totalReturn(),
                record.cagr(),
                record.annualisedVolatility(),
                record.sharpe(),
                record.maxDrawdown(),
                record.trades(),
                record.timeInMarket(),
                record.equityCurve());
    }
}
