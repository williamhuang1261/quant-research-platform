package io.github.williamhuang1261.qrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PerformanceMetricsTest {

    @Test
    @DisplayName("max drawdown is the deepest peak to trough fall, not the last one")
    void maxDrawdownFindsTheDeepestFall() {
        double[] equity = {100.0, 120.0, 90.0, 130.0, 117.0};

        // The 120 -> 90 fall is 25 %; the later 130 -> 117 fall is only 10 %.
        assertEquals(0.25, PerformanceMetrics.maxDrawdown(equity), 1e-12);
    }

    @Test
    @DisplayName("a curve that only rises has no drawdown")
    void risingCurveHasNoDrawdown() {
        assertEquals(0.0, PerformanceMetrics.maxDrawdown(new double[] {100.0, 101.0, 105.0}), 1e-12);
    }

    @Test
    @DisplayName("a flat equity curve has an undefined Sharpe rather than zero")
    void flatCurveHasUndefinedSharpe() {
        BacktestResult result = BacktestEngine.run(new BacktestRequest(
                TestSeries.flatOpens(100.0, 100.0, 100.0, 100.0),
                TestStrategies.alwaysFlat(),
                io.github.williamhuang1261.qrp.core.Params.empty(),
                new MarketOpenExecutionModel(CostModel.none()),
                10_000.0));

        assertTrue(Double.isNaN(result.metrics().sharpeRatio()));
        assertEquals(0.0, result.metrics().totalReturn(), 1e-12);
        assertEquals(0.0, result.metrics().maxDrawdown(), 1e-12);
    }

    @Test
    @DisplayName("time in market counts the bars actually holding a position")
    void timeInMarketCountsHoldingBars() {
        // Long from the third bar onward: the fill lands on bar 3 (index 2).
        BacktestResult result = BacktestEngine.run(new BacktestRequest(
                TestSeries.flatOpens(100.0, 100.0, 100.0, 100.0, 100.0),
                TestStrategies.byBarIndex("late-long",
                        index -> index >= 1
                                ? io.github.williamhuang1261.qrp.core.Signal.fullyLong()
                                : io.github.williamhuang1261.qrp.core.Signal.flat()),
                io.github.williamhuang1261.qrp.core.Params.empty(),
                new MarketOpenExecutionModel(CostModel.none()),
                10_000.0));

        assertEquals(3.0 / 5.0, result.metrics().timeInMarket(), 1e-12);
    }
}
