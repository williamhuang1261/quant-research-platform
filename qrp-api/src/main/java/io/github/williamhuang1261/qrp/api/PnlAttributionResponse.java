package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.app.BacktestRunner;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;
import io.github.williamhuang1261.qrp.pnl.PnlAttribution;

/**
 * A run's P&amp;L attribution, over the wire. {@code impliedCarryPnl} is
 * called out separately from {@code totalReconciledPnl} on purpose: it is a
 * memo-only figure at a caller-supplied rate, never part of the reconciled
 * price/cost total, so a caller cannot mistake it for money the run actually
 * made.
 */
public record PnlAttributionResponse(
        String strategyId,
        String executionId,
        double initialEquity,
        double finalEquity,
        double priceMovePnl,
        double executionCostPnl,
        double totalReconciledPnl,
        double impliedCarryPnl,
        int tradeCount) {

    static PnlAttributionResponse from(BacktestRunner.Outcome outcome, PnlAttribution attribution) {
        PerformanceMetrics metrics = outcome.result().metrics();
        return new PnlAttributionResponse(
                outcome.strategyId(),
                outcome.executionId(),
                metrics.initialEquity(),
                metrics.finalEquity(),
                attribution.priceMovePnl(),
                attribution.executionCostPnl(),
                attribution.totalReconciledPnl(),
                attribution.impliedCarryPnl(),
                metrics.tradeCount());
    }
}
