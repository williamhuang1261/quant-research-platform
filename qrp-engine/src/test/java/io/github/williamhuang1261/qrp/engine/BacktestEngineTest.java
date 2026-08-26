package io.github.williamhuang1261.qrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.Signal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BacktestEngineTest {

    private static BacktestResult run(BarSeries series, io.github.williamhuang1261.qrp.core.spi.Strategy strategy,
            CostModel costs, double cash) {
        return BacktestEngine.run(new BacktestRequest(
                series, strategy, Params.empty(), new MarketOpenExecutionModel(costs), cash));
    }

    @Test
    @DisplayName("a strategy that never trades ends with exactly the cash it started with")
    void flatStrategyPreservesCash() {
        BacktestResult result = run(TestSeries.flatOpens(100.0, 110.0, 90.0, 105.0),
                TestStrategies.alwaysFlat(), CostModel.retail(), 10_000.0);

        assertEquals(10_000.0, result.finalEquity(), 1e-12);
        assertEquals(List.of(), result.trades());
        assertEquals(0.0, result.metrics().timeInMarket(), 1e-12);
    }

    @Test
    @DisplayName("a decision on one bar fills at the NEXT bar's open, never the same close")
    void fillsAtTheNextOpen() {
        // Bar 0 closes at 100, bar 1 opens at 130: a same-bar fill would buy at 100.
        BarSeries series = TestSeries.of(new double[][] {{100, 100}, {130, 130}, {130, 130}});

        BacktestResult result = run(series, TestStrategies.alwaysLong(), CostModel.none(), 13_000.0);

        assertEquals(1, result.trades().size());
        Trade fill = result.trades().get(0);
        assertEquals(1, fill.barIndex());
        assertEquals(130.0, fill.price(), 1e-12);
        assertEquals(0.0, result.exposure().get(0), 1e-12,
                "bar 0 is decided but not yet filled, so it carries no exposure");
        assertEquals(1.0, result.exposure().get(1), 1e-12);
    }

    @Test
    @DisplayName("the target stated on the last bar is never executed")
    void lastBarDecisionIsNotExecuted() {
        BarSeries series = TestSeries.flatOpens(100.0, 100.0, 100.0);

        BacktestResult result = run(series,
                TestStrategies.byBarIndex("long-at-the-end",
                        index -> index == 2 ? Signal.fullyLong() : Signal.flat()),
                CostModel.none(), 10_000.0);

        assertEquals(List.of(), result.trades());
        assertEquals(10_000.0, result.finalEquity(), 1e-12);
    }

    @Test
    @DisplayName("buy and hold tracks the price, minus the whole-share remainder")
    void buyAndHoldTracksThePrice() {
        // Buys 100 shares at the open of bar 1, holds to a close of 120.
        BarSeries series = TestSeries.of(new double[][] {{100, 100}, {100, 110}, {110, 120}});

        BacktestResult result = run(series, TestStrategies.alwaysLong(), CostModel.none(), 10_000.0);

        assertEquals(1, result.trades().size());
        assertEquals(100.0, result.trades().get(0).shares(), 1e-12);
        assertEquals(12_000.0, result.finalEquity(), 1e-12);
        assertEquals(0.2, result.metrics().totalReturn(), 1e-12);
    }

    @Test
    @DisplayName("costs come straight out of the result")
    void costsReduceEquity() {
        BarSeries series = TestSeries.of(new double[][] {{100, 100}, {100, 110}, {110, 120}});

        double free = run(series, TestStrategies.alwaysLong(), CostModel.none(), 10_000.0).finalEquity();
        double charged = run(series, TestStrategies.alwaysLong(), CostModel.retail(), 10_000.0).finalEquity();

        assertTrue(charged < free, "retail costs " + charged + " should trail free " + free);
    }

    @Test
    @DisplayName("slippage is charged on the fill and is auditable on the trade")
    void slippageIsAuditable() {
        BarSeries series = TestSeries.of(new double[][] {{100, 100}, {100, 100}, {100, 100}});
        CostModel costs = new CostModel(0.0, 0.0, 50.0);   // 50 bps

        BacktestResult result = run(series, TestStrategies.alwaysLong(), costs, 10_000.0);

        Trade fill = result.trades().get(0);
        assertEquals(100.50, fill.price(), 1e-12);
        assertEquals(100.0, fill.referencePrice(), 1e-12);
        assertEquals(99.0 * 0.50, fill.slippageCost(), 1e-9);
        assertTrue(fill.isBuy());
    }

    @Test
    @DisplayName("positions are whole shares, rounded down")
    void positionsAreWholeShares() {
        // 10,000 / 300 = 33.33 shares.
        BarSeries series = TestSeries.flatOpens(300.0, 300.0, 300.0);

        BacktestResult result = run(series, TestStrategies.alwaysLong(), CostModel.none(), 10_000.0);

        assertEquals(33.0, result.trades().get(0).shares(), 1e-12);
        assertEquals(10_000.0, result.finalEquity(), 1e-12, "the remainder stays in cash");
    }

    @Test
    @DisplayName("going flat sells the whole position back")
    void exitingSellsEverything() {
        BarSeries series = TestSeries.flatOpens(100.0, 100.0, 100.0, 100.0, 100.0);

        BacktestResult result = run(series,
                TestStrategies.byBarIndex("in-then-out",
                        index -> index <= 1 ? Signal.fullyLong() : Signal.flat()),
                CostModel.none(), 10_000.0);

        assertEquals(2, result.trades().size());
        assertEquals(100.0, result.trades().get(0).shares(), 1e-12);
        assertEquals(-100.0, result.trades().get(1).shares(), 1e-12);
        assertEquals(10_000.0, result.finalEquity(), 1e-12);
    }

    @Test
    @DisplayName("a short target sells first and profits from a fall")
    void shortPositionsAreSupported() {
        BarSeries series = TestSeries.of(new double[][] {{100, 100}, {100, 90}, {90, 80}});

        BacktestResult result = run(series,
                TestStrategies.byBarIndex("short", index -> Signal.fullyShort()),
                CostModel.none(), 10_000.0);

        assertEquals(-100.0, result.trades().get(0).shares(), 1e-12);
        assertEquals(12_000.0, result.finalEquity(), 1e-12);
    }

    @Test
    @DisplayName("a run is reproducible: the same request twice gives the same curve")
    void isReproducible() {
        BarSeries series = TestSeries.of(new double[][] {{100, 105}, {105, 95}, {95, 115}, {115, 110}});

        BacktestResult first = run(series, TestStrategies.alwaysLong(), CostModel.retail(), 10_000.0);
        BacktestResult second = run(series, TestStrategies.alwaysLong(), CostModel.retail(), 10_000.0);

        assertEquals(first.equityCurve(), second.equityCurve());
        assertEquals(first.finalEquity(), second.finalEquity(), 0.0);
    }

    @Test
    @DisplayName("rejects a request that cannot produce a result")
    void rejectsUnusableRequests() {
        BarSeries series = TestSeries.flatOpens(100.0, 100.0);

        assertThrows(IllegalArgumentException.class, () -> new BacktestRequest(
                series, TestStrategies.alwaysFlat(), Params.empty(),
                new MarketOpenExecutionModel(CostModel.none()), 0.0));
        assertThrows(IllegalArgumentException.class, () -> new BacktestRequest(
                TestSeries.flatOpens(100.0), TestStrategies.alwaysFlat(), Params.empty(),
                new MarketOpenExecutionModel(CostModel.none()), 10_000.0));
        assertThrows(NullPointerException.class, () -> new BacktestRequest(
                series, null, Params.empty(), new MarketOpenExecutionModel(CostModel.none()), 10_000.0));
    }
}
