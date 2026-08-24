package io.github.williamhuang1261.qrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DoubleSeriesTest {

    @Test
    @DisplayName("NaN marks the warm-up rather than zero")
    void warmupIsNaN() {
        DoubleSeries series = DoubleSeries.of(Double.NaN, Double.NaN, 3.0, 4.0);

        assertFalse(series.isDefined(0));
        assertTrue(series.isDefined(2));
        assertEquals(2, series.firstDefinedIndex());
    }

    @Test
    @DisplayName("undefined() builds an all-NaN series of the requested length")
    void undefinedSeries() {
        DoubleSeries series = DoubleSeries.undefined(3);

        assertEquals(3, series.size());
        assertEquals(-1, series.firstDefinedIndex());
        assertEquals(OptionalDouble.empty(), series.lastDefined());
    }

    @Test
    @DisplayName("lastDefined skips trailing NaN")
    void lastDefinedSkipsTrailingNaN() {
        DoubleSeries series = DoubleSeries.of(1.0, 2.0, Double.NaN);

        assertEquals(OptionalDouble.of(2.0), series.lastDefined());
    }

    @Test
    @DisplayName("copies on the way in and on the way out")
    void isDefensivelyCopied() {
        double[] source = {1.0, 2.0};
        DoubleSeries series = DoubleSeries.of(source);

        source[0] = 99.0;
        double[] exported = series.toArray();
        exported[1] = 99.0;

        assertEquals(1.0, series.get(0), 1e-12);
        assertEquals(2.0, series.get(1), 1e-12);
    }

    @Test
    @DisplayName("equality is by value")
    void equalityIsByValue() {
        assertEquals(DoubleSeries.of(1.0, 2.0), DoubleSeries.of(1.0, 2.0));
    }
}
