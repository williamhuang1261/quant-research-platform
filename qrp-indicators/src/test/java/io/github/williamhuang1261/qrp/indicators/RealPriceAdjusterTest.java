package io.github.williamhuang1261.qrp.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RealPriceAdjusterTest {

    @Test
    @DisplayName("a flat price index leaves prices untouched")
    void flatIndexChangesNothing() {
        BarSeries series = IndicatorFixtures.seriesOf(100, 110, 120);
        DoubleSeries cpi = DoubleSeries.of(100.0, 100.0, 100.0);

        DoubleSeries real = RealPriceAdjuster.toRealPrices(series, cpi, 100.0);

        assertEquals(100.0, real.get(0), 1e-12);
        assertEquals(110.0, real.get(1), 1e-12);
        assertEquals(120.0, real.get(2), 1e-12);
    }

    @Test
    @DisplayName("a nominal gain that only matches inflation is flat in real terms")
    void inflationOnlyGainIsFlat() {
        BarSeries series = IndicatorFixtures.seriesOf(100, 110);
        DoubleSeries cpi = DoubleSeries.of(100.0, 110.0);

        DoubleSeries real = RealPriceAdjuster.toRealPrices(series, cpi, 100.0);

        assertEquals(100.0, real.get(0), 1e-12);
        assertEquals(100.0, real.get(1), 1e-12);
    }

    @Test
    @DisplayName("the base level chooses which period's dollars the result is in")
    void baseLevelSetsTheUnit() {
        BarSeries series = IndicatorFixtures.seriesOf(100, 100);
        DoubleSeries cpi = DoubleSeries.of(100.0, 125.0);

        DoubleSeries real = RealPriceAdjuster.toRealPrices(series, cpi, 125.0);

        assertEquals(125.0, real.get(0), 1e-12);
        assertEquals(100.0, real.get(1), 1e-12);
    }

    @Test
    @DisplayName("an undefined index level leaves that bar undefined")
    void undefinedIndexPropagates() {
        BarSeries series = IndicatorFixtures.seriesOf(100, 110);
        DoubleSeries cpi = DoubleSeries.of(Double.NaN, 110.0);

        DoubleSeries real = RealPriceAdjuster.toRealPrices(series, cpi, 100.0);

        assertFalse(real.isDefined(0));
        assertTrue(real.isDefined(1));
    }

    @Test
    @DisplayName("a misaligned index is a programming error, not a silent truncation")
    void rejectsMisalignedIndex() {
        BarSeries series = IndicatorFixtures.seriesOf(100, 110, 120);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> RealPriceAdjuster.toRealPrices(series, DoubleSeries.of(100.0, 110.0), 100.0));

        assertTrue(thrown.getMessage().contains("2 values"), thrown.getMessage());
    }

    @Test
    @DisplayName("rejects a non-positive base or index level")
    void rejectsNonPositiveLevels() {
        BarSeries series = IndicatorFixtures.seriesOf(100, 110);

        assertThrows(IllegalArgumentException.class,
                () -> RealPriceAdjuster.toRealPrices(series, DoubleSeries.of(100.0, 110.0), 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> RealPriceAdjuster.toRealPrices(series, DoubleSeries.of(100.0, -1.0), 100.0));
    }
}
