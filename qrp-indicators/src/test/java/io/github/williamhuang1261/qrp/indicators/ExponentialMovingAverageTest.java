package io.github.williamhuang1261.qrp.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExponentialMovingAverageTest {

    private final ExponentialMovingAverage indicator = new ExponentialMovingAverage();

    @Test
    @DisplayName("seeds on the simple average of the first window")
    void seedsOnSimpleAverage() {
        BarSeries series = IndicatorFixtures.seriesOf(1, 2, 3, 4, 5);

        DoubleSeries ema = indicator.compute(series, Params.of("period", 3));

        assertFalse(ema.isDefined(1));
        assertEquals(2.0, ema.get(2), 1e-12);                       // (1 + 2 + 3) / 3
        assertEquals(0.5 * 4 + 0.5 * 2.0, ema.get(3), 1e-12);       // alpha = 2 / (3 + 1)
        assertEquals(0.5 * 5 + 0.5 * 3.0, ema.get(4), 1e-12);
    }

    @Test
    @DisplayName("a flat series stays at its level")
    void flatSeriesStaysFlat() {
        DoubleSeries ema = indicator.compute(
                IndicatorFixtures.seriesOf(7, 7, 7, 7, 7, 7), Params.of("period", 3));

        for (int i = 2; i < 6; i++) {
            assertEquals(7.0, ema.get(i), 1e-12);
        }
    }

    @Test
    @DisplayName("reacts faster than the simple average to a step change")
    void reactsFasterThanSma() {
        BarSeries series = IndicatorFixtures.seriesOf(10, 10, 10, 10, 20, 20, 20);
        Params params = Params.of("period", 4);

        double ema = indicator.compute(series, params).get(6);
        double sma = new SimpleMovingAverage().compute(series, params).get(6);

        assertTrue(ema > sma, "ema " + ema + " should lead sma " + sma);
    }
}
