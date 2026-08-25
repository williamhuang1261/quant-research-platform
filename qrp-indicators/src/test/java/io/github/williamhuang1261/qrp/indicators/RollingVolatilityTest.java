package io.github.williamhuang1261.qrp.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RollingVolatilityTest {

    private final RollingVolatility indicator = new RollingVolatility();

    @Test
    @DisplayName("a constant compounding rate has zero dispersion")
    void constantGrowthHasNoVolatility() {
        double[] closes = new double[10];
        closes[0] = 100.0;
        for (int i = 1; i < closes.length; i++) {
            closes[i] = closes[i - 1] * 1.01;
        }

        DoubleSeries volatility = indicator.compute(
                IndicatorFixtures.seriesOf(closes), Params.of("period", 5));

        assertEquals(0.0, volatility.get(9), 1e-12);
    }

    @Test
    @DisplayName("matches the sample standard deviation of log returns, annualised")
    void matchesHandComputedValue() {
        BarSeries series = IndicatorFixtures.seriesOf(100, 110, 105, 115, 108);
        int period = 4;

        double value = indicator.compute(series, Params.of("period", period)).get(4);

        double[] returns = {
                Math.log(110.0 / 100.0), Math.log(105.0 / 110.0),
                Math.log(115.0 / 105.0), Math.log(108.0 / 115.0)};
        double mean = (returns[0] + returns[1] + returns[2] + returns[3]) / period;
        double sumSquares = 0.0;
        for (double r : returns) {
            sumSquares += (r - mean) * (r - mean);
        }
        double expected = Math.sqrt(sumSquares / (period - 1)) * Math.sqrt(252.0);

        assertEquals(expected, value, 1e-12);
    }

    @Test
    @DisplayName("annualisation is configurable and scales by its square root")
    void annualisationIsConfigurable() {
        BarSeries series = IndicatorFixtures.seriesOf(100, 110, 105, 115, 108);
        Params daily = Params.of("period", 4);
        Params monthly = daily.with(RollingVolatility.ANNUALIZATION, 12);

        double annualised = indicator.compute(series, daily).get(4);
        double monthlyScaled = indicator.compute(series, monthly).get(4);

        assertEquals(Math.sqrt(252.0 / 12.0), annualised / monthlyScaled, 1e-12);
    }

    @Test
    @DisplayName("a higher volatility series reports a higher number")
    void ordersSeriesByDispersion() {
        DoubleSeries calm = indicator.compute(
                IndicatorFixtures.seriesOf(100, 101, 100, 101, 100, 101), Params.of("period", 5));
        DoubleSeries wild = indicator.compute(
                IndicatorFixtures.seriesOf(100, 130, 90, 140, 85, 135), Params.of("period", 5));

        assertTrue(wild.get(5) > calm.get(5));
    }

    @Test
    @DisplayName("rejects a non-positive annualisation factor")
    void rejectsBadAnnualisation() {
        BarSeries series = IndicatorFixtures.seriesOf(100, 110, 105, 115, 108);
        Params params = Params.of("period", 4).with(RollingVolatility.ANNUALIZATION, -1);

        assertThrows(IllegalArgumentException.class, () -> indicator.compute(series, params));
    }
}
