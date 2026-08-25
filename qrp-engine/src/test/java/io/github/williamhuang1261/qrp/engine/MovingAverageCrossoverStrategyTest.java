package io.github.williamhuang1261.qrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.PluginRegistry;
import io.github.williamhuang1261.qrp.core.Signal;
import io.github.williamhuang1261.qrp.core.spi.Strategy;
import io.github.williamhuang1261.qrp.engine.strategies.MovingAverageCrossoverStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MovingAverageCrossoverStrategyTest {

    private final MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy();
    private final Params params = Params.of(MovingAverageCrossoverStrategy.FAST, 2)
            .with(MovingAverageCrossoverStrategy.SLOW, 4);

    @Test
    @DisplayName("is discovered through the Strategy SPI, not by name")
    void isDiscoverable() {
        PluginRegistry<Strategy> registry = PluginRegistry.load(Strategy.class, Strategy::id);

        assertTrue(registry.ids().contains("sma-crossover"), "found: " + registry.ids());
        assertEquals(MovingAverageCrossoverStrategy.class, registry.require("sma-crossover").getClass());
    }

    @Test
    @DisplayName("goes long once the fast average crosses above the slow one")
    void longsTheUpCross() {
        BarSeries series = TestSeries.flatOpens(10, 10, 10, 10, 20, 30, 40);
        strategy.onStart(series, params);

        // Bar 3 still averages flat; by bar 5 the fast average has pulled ahead.
        assertEquals(Signal.flat(), strategy.onBar(series.visibleAt(3), params));
        assertEquals(Signal.fullyLong(), strategy.onBar(series.visibleAt(5), params));
    }

    @Test
    @DisplayName("returns to flat when the fast average falls back")
    void exitsOnTheDownCross() {
        BarSeries series = TestSeries.flatOpens(40, 40, 40, 40, 30, 20, 10);
        strategy.onStart(series, params);

        assertEquals(Signal.flat(), strategy.onBar(series.visibleAt(6), params));
    }

    @Test
    @DisplayName("stays flat while the slow average is still warming up")
    void staysFlatDuringWarmup() {
        BarSeries series = TestSeries.flatOpens(10, 20, 30, 40, 50);
        strategy.onStart(series, params);

        assertEquals(Signal.flat(), strategy.onBar(series.visibleAt(1), params));
        assertEquals(3, strategy.warmup(params), "the slow average needs 4 bars");
    }

    @Test
    @DisplayName("refuses a fast window that is not shorter than the slow one")
    void refusesInvertedWindows() {
        BarSeries series = TestSeries.flatOpens(10, 20, 30, 40, 50);
        Params inverted = Params.of(MovingAverageCrossoverStrategy.FAST, 10)
                .with(MovingAverageCrossoverStrategy.SLOW, 5);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> strategy.onStart(series, inverted));
        assertTrue(thrown.getMessage().contains("shorter"), thrown.getMessage());
    }

    @Test
    @DisplayName("refuses to decide before onStart has resolved its indicator")
    void refusesToRunUnstarted() {
        BarSeries series = TestSeries.flatOpens(10, 20, 30, 40, 50);

        assertThrows(IllegalStateException.class,
                () -> new MovingAverageCrossoverStrategy().onBar(series.visibleAt(4), params));
    }
}
