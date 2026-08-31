package io.github.williamhuang1261.qrp.warehouse;

import java.time.Instant;

/**
 * One persisted row of {@code fact_backtest_run} -- everything
 * {@code qrp-api}'s {@code RunResponse} needs, so a cache hit or a
 * {@code GET /api/runs/{id}} never has to fall back to a smaller shape than
 * a freshly computed run returns.
 */
public record BacktestRunRecord(
        long id,
        long instrumentId,
        long strategyId,
        String paramsJson,
        double cash,
        String costModel,
        String executionModel,
        String engineId,
        double initialEquity,
        double finalEquity,
        double totalReturn,
        double cagr,
        double annualisedVolatility,
        double sharpe,
        double maxDrawdown,
        int trades,
        double timeInMarket,
        double[] equityCurve,
        Instant createdAt) {
}
