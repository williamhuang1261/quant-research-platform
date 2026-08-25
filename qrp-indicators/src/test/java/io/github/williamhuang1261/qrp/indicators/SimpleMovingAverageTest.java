package io.github.williamhuang1261.qrp.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimpleMovingAverageTest {

    private final SimpleMovingAverage indicator = new SimpleMovingAverage();

    @Test
    @DisplayName("matches values computed by hand")
    void matchesHandComputedValues() {
        BarSeries series = IndicatorFixtures.seriesOf(1, 2, 3, 4, 5);

        DoubleSeries sma = indicator.compute(series, Params.of("period", 3));

        assertFalse(sma.isDefined(0));
        assertFalse(sma.isDefined(1));
        assertEquals(2.0, sma.get(2), 1e-12);   // (1 + 2 + 3) / 3
        assertEquals(3.0, sma.get(3), 1e-12);   // (2 + 3 + 4) / 3
        assertEquals(4.0, sma.get(4), 1e-12);   // (3 + 4 + 5) / 3
    }

    @Test
    @DisplayName("the running sum agrees with a direct mean across a long series")
    void runningSumDoesNotDrift() {
        double[] closes = new double[500];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100.0 + 30.0 * Math.sin(i / 9.0) + i * 0.05;
        }
        BarSeries series = IndicatorFixtures.seriesOf(closes);
        int period = 20;

        DoubleSeries sma = indicator.compute(series, Params.of("period", period));

        for (int i = period - 1; i < closes.length; i++) {
            double direct = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                direct += closes[j];
            }
            assertEquals(direct / period, sma.get(i), 1e-9, "drift at index " + i);
        }
    }

    @Test
    @DisplayName("a period longer than the series yields no values, not an exception")
    void handlesShortSeries() {
        DoubleSeries sma = indicator.compute(IndicatorFixtures.seriesOf(1, 2), Params.of("period", 5));

        assertEquals(2, sma.size());
        assertEquals(-1, sma.firstDefinedIndex());
    }

    @Test
    @DisplayName("rejects a period below one or a missing period")
    void rejectsBadParameters() {
        BarSeries series = IndicatorFixtures.seriesOf(1, 2, 3);

        assertThrows(IllegalArgumentException.class, () -> indicator.compute(series, Params.of("period", 0)));
        assertThrows(IllegalArgumentException.class, () -> indicator.compute(series, Params.empty()));
        assertThrows(IllegalArgumentException.class, () -> indicator.compute(series, Params.of("period", 2.5)));
    }
}
