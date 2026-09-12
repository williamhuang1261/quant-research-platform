package io.github.williamhuang1261.qrp.pnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;
import io.github.williamhuang1261.qrp.engine.Trade;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A hand-built 3-bar case where every component is computed independently in
 * this test and checked against {@link PnlAttribution#of}, rather than
 * trusting the implementation's own arithmetic by inspection.
 */
class PnlAttributionTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void reconcilesAgainstAHandComputedThreeBarCase() {
        // Bar 0: no fill possible (nothing pending yet). Bar 1: buy 100 shares,
        // filled at 101.0 against a reference (open) price of 100.0 -- a 1.0
        // slippage concession per share -- plus a flat $5 commission. Bar 2: no
        // further fill; the 100 shares just ride the close-to-close price move.
        Bar bar0 = new Bar(Instant.parse("2026-01-01T21:00:00Z"), 100.0, 101.0, 99.0, 100.0, 10_000);
        Bar bar1 = new Bar(Instant.parse("2026-01-02T21:00:00Z"), 100.0, 103.0, 99.0, 102.0, 10_000);
        Bar bar2 = new Bar(Instant.parse("2026-01-05T21:00:00Z"), 102.0, 106.0, 101.0, 105.0, 10_000);
        BarSeries series = BarSeries.of(Instrument.equity("SYNA"), Timeframe.DAY_1, List.of(bar0, bar1, bar2));

        double initialCash = 100_000.0;
        double fillPrice = 101.0;
        double referencePrice = 100.0;
        double fillShares = 100.0;
        double commission = 5.0;

        double cashAfterFill = initialCash - fillShares * fillPrice - commission;
        double[] equity = {
            initialCash,
            cashAfterFill + fillShares * bar1.close(),
            cashAfterFill + fillShares * bar2.close()
        };

        Trade fill = new Trade(1, bar1.timestamp(), fillShares, fillPrice, referencePrice, commission);
        BacktestResult result = new BacktestResult(
                series,
                DoubleSeries.of(equity),
                DoubleSeries.of(0.0, 1.0, 1.0),
                List.of(fill),
                placeholderMetrics(equity));

        PnlAttribution attribution = PnlAttribution.of(result, 0.0);

        double expectedSlippageCost = fillShares * (fillPrice - referencePrice);
        double expectedPriceMovePnl =
                fillShares * (bar1.close() - referencePrice) // the new fill's move from execution to bar 1's close
                        + fillShares * (bar2.close() - bar1.close()); // the held position's move over bar 2
        double expectedExecutionCostPnl = -(expectedSlippageCost + commission);
        double expectedTotal = equity[2] - equity[0];

        assertEquals(expectedPriceMovePnl, attribution.priceMovePnl(), TOLERANCE);
        assertEquals(expectedExecutionCostPnl, attribution.executionCostPnl(), TOLERANCE);
        assertEquals(0.0, attribution.impliedCarryPnl(), TOLERANCE, "a zero carry rate must imply zero carry");
        assertEquals(expectedTotal, attribution.totalReconciledPnl(), TOLERANCE);
        assertTrue(attribution.reconciles(expectedTotal, TOLERANCE));
    }

    @Test
    void impliedCarryScalesWithCashHeldAndTheStatedRate() {
        Bar bar0 = new Bar(Instant.parse("2026-01-01T21:00:00Z"), 100.0, 101.0, 99.0, 100.0, 10_000);
        Bar bar1 = new Bar(Instant.parse("2026-01-02T21:00:00Z"), 100.0, 101.0, 99.0, 100.0, 10_000);
        BarSeries series = BarSeries.of(Instrument.equity("SYNA"), Timeframe.DAY_1, List.of(bar0, bar1));

        double[] equity = {100_000.0, 100_000.0};
        BacktestResult result = new BacktestResult(
                series, DoubleSeries.of(equity), DoubleSeries.of(0.0, 0.0), List.of(),
                placeholderMetrics(equity));

        double annualRate = 0.05;
        PnlAttribution attribution = PnlAttribution.of(result, annualRate);

        double expectedCarry = 100_000.0 * (annualRate / 252.0);
        assertEquals(expectedCarry, attribution.impliedCarryPnl(), TOLERANCE);
        assertEquals(0.0, attribution.totalReconciledPnl(), TOLERANCE,
                "carry must never leak into the reconciled total");
    }

    @Test
    void rejectsTooShortASeries() {
        Bar bar0 = new Bar(Instant.parse("2026-01-01T21:00:00Z"), 100.0, 101.0, 99.0, 100.0, 10_000);
        BarSeries series = BarSeries.of(Instrument.equity("SYNA"), Timeframe.DAY_1, List.of(bar0));
        double[] equity = {100_000.0};
        BacktestResult result = new BacktestResult(
                series, DoubleSeries.of(equity), DoubleSeries.of(0.0), List.of(),
                placeholderMetrics(equity));

        assertThrows(IllegalArgumentException.class, () -> PnlAttribution.of(result, 0.0));
    }

    /**
     * {@link PnlAttribution} never reads {@link PerformanceMetrics}; only the
     * equity/final values need to be non-degenerate for the record's own
     * validation to accept them. {@code PerformanceMetrics.from} is
     * package-private to {@code qrp-engine}, so this test builds one through
     * its public canonical constructor instead.
     */
    private static PerformanceMetrics placeholderMetrics(double[] equity) {
        double initial = equity[0];
        double last = equity[equity.length - 1];
        return new PerformanceMetrics(initial, last, last / initial - 1.0, 0.0, 0.0, Double.NaN, 0.0, 0, 0.0);
    }
}
