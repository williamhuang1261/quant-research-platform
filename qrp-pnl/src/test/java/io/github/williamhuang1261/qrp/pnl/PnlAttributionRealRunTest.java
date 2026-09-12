package io.github.williamhuang1261.qrp.pnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.engine.BacktestEngine;
import io.github.williamhuang1261.qrp.engine.BacktestRequest;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.CostModel;
import io.github.williamhuang1261.qrp.engine.MarketOpenExecutionModel;
import io.github.williamhuang1261.qrp.engine.strategies.MovingAverageCrossoverStrategy;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A hand-built case can pass by construction. This reruns the exact 20/50
 * crossover over the real {@code SYNA_1d.csv} sample data already
 * characterised by {@code qrp-engine}'s {@code BacktestIntegrationTest}
 * (11 real fills, a genuine loss, real commission and slippage on every
 * trade) and checks the same reconciliation identity against that real,
 * multi-fill output.
 */
class PnlAttributionRealRunTest {

    private static final double TOLERANCE = 1e-6;

    @Test
    void reconcilesExactlyAgainstTheRealTwentyFiftyCrossoverRun() {
        BarSeries series = CsvMarketDataProvider.ofDirectory(Path.of("..", "data", "sample"))
                .loadAll(Instrument.equity("SYNA"), Timeframe.DAY_1);

        BacktestResult result = BacktestEngine.run(new BacktestRequest(
                series,
                new MovingAverageCrossoverStrategy(),
                Params.of(MovingAverageCrossoverStrategy.FAST, 20)
                        .with(MovingAverageCrossoverStrategy.SLOW, 50),
                new MarketOpenExecutionModel(CostModel.retail()),
                100_000.0));

        assertEquals(11, result.trades().size(), "sanity check against the engine's own characterised run");

        PnlAttribution attribution = PnlAttribution.of(result, 0.0);

        double expectedChange = result.equityCurve().get(result.equityCurve().size() - 1) - result.equityCurve().get(0);
        assertEquals(expectedChange, attribution.totalReconciledPnl(), TOLERANCE);
        assertTrue(attribution.reconciles(expectedChange, TOLERANCE));

        // The 20/50 crossover on SYNA is a real, recorded loss (see
        // BacktestIntegrationTest.goldenRun): the price move alone should be
        // negative, and execution costs should further drag on it, not offset it.
        assertTrue(attribution.priceMovePnl() < 0.0, "the characterised run is a loss driven by price moves");
        assertTrue(attribution.executionCostPnl() < 0.0, "commission and slippage always cost money, never earn it");
    }

    @Test
    void impliedCarryIsAPositiveMemoFigureOnAPositiveRateAndNeverAltersTheReconciledTotal() {
        BarSeries series = CsvMarketDataProvider.ofDirectory(Path.of("..", "data", "sample"))
                .loadAll(Instrument.equity("SYNA"), Timeframe.DAY_1);

        BacktestResult result = BacktestEngine.run(new BacktestRequest(
                series,
                new MovingAverageCrossoverStrategy(),
                Params.of(MovingAverageCrossoverStrategy.FAST, 20)
                        .with(MovingAverageCrossoverStrategy.SLOW, 50),
                new MarketOpenExecutionModel(CostModel.retail()),
                100_000.0));

        PnlAttribution noCarry = PnlAttribution.of(result, 0.0);
        PnlAttribution withCarry = PnlAttribution.of(result, 0.05);

        assertEquals(noCarry.totalReconciledPnl(), withCarry.totalReconciledPnl(), TOLERANCE,
                "the carry rate must never change the reconciled price/cost total");
        assertTrue(withCarry.impliedCarryPnl() > 0.0,
                "a long-only strategy holds cash on the sidelines while flat, which should earn positive carry at a positive rate");
    }
}
