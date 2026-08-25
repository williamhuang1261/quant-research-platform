package io.github.williamhuang1261.qrp.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RelativeStrengthIndexTest {

    private final RelativeStrengthIndex indicator = new RelativeStrengthIndex();

    @Test
    @DisplayName("an uninterrupted rise gives 100, and no window has a value before bar `period`")
    void unbrokenRiseSaturates() {
        BarSeries series = IndicatorFixtures.seriesOf(1, 2, 3, 4, 5, 6, 7);

        DoubleSeries rsi = indicator.compute(series, Params.of("period", 3));

        assertFalse(rsi.isDefined(2));
        assertEquals(3, rsi.firstDefinedIndex());
        assertEquals(100.0, rsi.get(3), 1e-12);
        assertEquals(100.0, rsi.get(6), 1e-12);
    }

    @Test
    @DisplayName("an uninterrupted fall gives 0")
    void unbrokenFallBottoms() {
        DoubleSeries rsi = indicator.compute(
                IndicatorFixtures.seriesOf(7, 6, 5, 4, 3), Params.of("period", 3));

        assertEquals(0.0, rsi.get(3), 1e-12);
    }

    @Test
    @DisplayName("a seed window with equal gains and losses sits at 50, then leans with each move")
    void balancedSeedSitsAtFifty() {
        // +1 -1 +1 -1 balances the seed window exactly; after it, Wilder smoothing
        // weights the newest change most, so the value leans in its direction.
        DoubleSeries rsi = indicator.compute(
                IndicatorFixtures.seriesOf(10, 11, 10, 11, 10, 11, 10, 11), Params.of("period", 4));

        assertEquals(50.0, rsi.get(4), 1e-9);
        assertTrue(rsi.get(5) > 50.0, "an up move should lift it above 50");
        assertTrue(rsi.get(6) < rsi.get(5), "a down move should pull it back");
    }

    @Test
    @DisplayName("stays inside [0, 100] on a noisy series")
    void staysBounded() {
        double[] closes = new double[200];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 50.0 + 10.0 * Math.sin(i / 4.0) + ((i % 7) - 3) * 0.4;
        }

        DoubleSeries rsi = indicator.compute(IndicatorFixtures.seriesOf(closes), Params.of("period", 14));

        for (int i = 14; i < closes.length; i++) {
            assertTrue(rsi.get(i) >= 0.0 && rsi.get(i) <= 100.0, "out of range at " + i + ": " + rsi.get(i));
        }
    }

    @Test
    @DisplayName("needs a period of at least two")
    void rejectsDegeneratePeriod() {
        BarSeries series = IndicatorFixtures.seriesOf(1, 2, 3);

        assertThrows(IllegalArgumentException.class, () -> indicator.compute(series, Params.of("period", 1)));
    }
}
