package io.github.williamhuang1261.qrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.engine.strategies.MovingAverageCrossoverStrategy;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End to end across every module: CSV data, an indicator resolved through the
 * SPI at runtime, the engine, and the metrics.
 *
 * <p>The expected numbers are a <em>characterisation</em> of the current
 * implementation, captured once and pinned here. They are not a claim that these
 * are the right returns; they are a tripwire. Change the fill timing, the share
 * rounding or the cost model and this test fails, which is exactly what should
 * happen when the meaning of every published result changes.
 *
 * <p>The result itself is a loss. A 20/50 crossover has no edge, and publishing
 * the number it actually produces is worth more than tuning parameters until the
 * README looks good.
 */
class BacktestIntegrationTest {

    private static final double CURRENCY_TOLERANCE = 1e-6;
    private static final double RATIO_TOLERANCE = 1e-9;

    private static BarSeries series;

    @BeforeAll
    static void loadSampleSeries() {
        series = CsvMarketDataProvider.ofDirectory(Path.of("..", "data", "sample"))
                .loadAll(Instrument.equity("SYNA"), Timeframe.DAY_1);
    }

    private static BacktestResult run(CostModel costs) {
        return BacktestEngine.run(new BacktestRequest(
                series,
                new MovingAverageCrossoverStrategy(),
                Params.of(MovingAverageCrossoverStrategy.FAST, 20)
                        .with(MovingAverageCrossoverStrategy.SLOW, 50),
                costs,
                100_000.0));
    }

    @Test
    @DisplayName("the 20/50 crossover on SYNA reproduces its recorded result exactly")
    void goldenRun() {
        BacktestResult result = run(CostModel.retail());
        PerformanceMetrics metrics = result.metrics();

        assertEquals(504, series.size());
        assertEquals(100_000.0, metrics.initialEquity(), CURRENCY_TOLERANCE);
        assertEquals(92_229.0094522352, metrics.finalEquity(), CURRENCY_TOLERANCE);
        assertEquals(-0.0777099055, metrics.totalReturn(), RATIO_TOLERANCE);
        assertEquals(-0.0411589578, metrics.cagr(), RATIO_TOLERANCE);
        assertEquals(0.1594036252, metrics.annualisedVolatility(), RATIO_TOLERANCE);
        assertEquals(-0.1745144272, metrics.sharpeRatio(), RATIO_TOLERANCE);
        assertEquals(0.2414947349, metrics.maxDrawdown(), RATIO_TOLERANCE);
        assertEquals(11, metrics.tradeCount());
        assertEquals(0.4365079365, metrics.timeInMarket(), RATIO_TOLERANCE);
    }

    @Test
    @DisplayName("the first fill lands on the bar after the crossover, at that bar's open")
    void firstFillIsOneBarLate() {
        Trade first = run(CostModel.retail()).trades().get(0);

        assertEquals(50, first.barIndex());
        assertEquals(843.0, first.shares(), CURRENCY_TOLERANCE);
        assertEquals(118.58, first.referencePrice(), CURRENCY_TOLERANCE);
        assertTrue(first.price() > first.referencePrice(), "a buy pays the concession");
        assertEquals(series.get(50).open(), first.referencePrice(), CURRENCY_TOLERANCE);
    }

    @Test
    @DisplayName("costs are what separate the two runs, and they cost real money here")
    void costsDragTheResult() {
        double free = run(CostModel.none()).finalEquity();
        double charged = run(CostModel.retail()).finalEquity();

        assertTrue(charged < free, charged + " should trail " + free);
        assertTrue(free - charged > 100.0,
                "11 round trips at retail costs should cost more than $100, was " + (free - charged));
    }

    @Test
    @DisplayName("the equity curve is aligned to the bars and never leaves the account short")
    void curveIsWellFormed() {
        BacktestResult result = run(CostModel.retail());

        assertEquals(series.size(), result.equityCurve().size());
        assertEquals(series.size(), result.exposure().size());
        for (int i = 0; i < series.size(); i++) {
            assertTrue(result.equityCurve().get(i) > 0.0, "equity went non-positive at bar " + i);
            double exposure = result.exposure().get(i);
            assertTrue(exposure == 0.0 || exposure == 1.0, "long-only strategy at bar " + i);
        }
    }
}
