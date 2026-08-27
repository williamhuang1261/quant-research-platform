package io.github.williamhuang1261.qrp.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.core.spi.Indicator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrossSectionalSignalGeneratorTest {

    private static final Instant DAY0 = Instant.parse("2024-01-02T21:00:00Z");

    private static Bar barAt(Instant timestamp, double close) {
        return new Bar(timestamp, close, close + 1.0, close - 1.0, close, 1_000L);
    }

    private static BarSeries flat(String symbol, double close, int bars) {
        List<Bar> series = new ArrayList<>();
        for (int i = 0; i < bars; i++) {
            series.add(barAt(DAY0.plus(Duration.ofDays(i)), close));
        }
        return BarSeries.of(Instrument.equity(symbol), Timeframe.DAY_1, series);
    }

    /** Echoes the close price as-is; a trivial "indicator" for controlling the test's cross-section directly. */
    private static final class IdentityIndicator implements Indicator {
        @Override
        public String id() {
            return "identity";
        }

        @Override
        public DoubleSeries compute(BarSeries series, Params params) {
            return DoubleSeries.of(series.closes());
        }
    }

    @Test
    @DisplayName("the top-ranked instrument each bar gets the maximum forecast, the bottom-ranked the minimum")
    void rankOrderingDrivesTheForecast() {
        BarSeries low = flat("LOW", 10.0, 5);
        BarSeries mid = flat("MID", 50.0, 5);
        BarSeries high = flat("HIGH", 90.0, 5);

        List<DoubleSeries> forecasts = CrossSectionalSignalGenerator.generate(
                List.of(low, mid, high), new IdentityIndicator(), Params.empty(), 0.02);

        for (int t = 0; t < 5; t++) {
            assertEquals(-0.01, forecasts.get(0).get(t), 1e-12, "low instrument at bar " + t);
            assertEquals(0.0, forecasts.get(1).get(t), 1e-12, "mid instrument at bar " + t);
            assertEquals(0.01, forecasts.get(2).get(t), 1e-12, "high instrument at bar " + t);
        }
    }

    @Test
    @DisplayName("a tie splits the forecast evenly between the tied instruments")
    void tieSplitsForecastEvenly() {
        BarSeries a = flat("A", 50.0, 3);
        BarSeries b = flat("B", 50.0, 3);
        BarSeries c = flat("C", 90.0, 3);

        List<DoubleSeries> forecasts = CrossSectionalSignalGenerator.generate(
                List.of(a, b, c), new IdentityIndicator(), Params.empty(), 0.03);

        // Tied ranks average to 1.5 each; the untied top instrument keeps rank 3.
        for (int t = 0; t < 3; t++) {
            assertEquals(forecasts.get(0).get(t), forecasts.get(1).get(t), 1e-12);
            assertTrue(forecasts.get(2).get(t) > forecasts.get(0).get(t));
        }
    }

    @Test
    @DisplayName("a bar where any instrument's indicator has not warmed up forecasts NaN for every instrument")
    void warmupPropagatesAsNaNAcrossTheWholeCrossSection() {
        BarSeries a = flat("A", 10.0, 30);
        BarSeries b = flat("B", 20.0, 30);
        BarSeries c = flat("C", 30.0, 30);

        List<DoubleSeries> forecasts = CrossSectionalSignalGenerator.generate(
                List.of(a, b, c), new io.github.williamhuang1261.qrp.indicators.SimpleMovingAverage(),
                Params.of("period", 20), 0.02);

        for (int t = 0; t < 19; t++) {
            assertTrue(Double.isNaN(forecasts.get(0).get(t)), "expected NaN before warmup at bar " + t);
            assertTrue(Double.isNaN(forecasts.get(1).get(t)), "expected NaN before warmup at bar " + t);
            assertTrue(Double.isNaN(forecasts.get(2).get(t)), "expected NaN before warmup at bar " + t);
        }
        assertTrue(!Double.isNaN(forecasts.get(0).get(19)), "expected a defined forecast once every instrument has warmed up");
    }

    @Test
    @DisplayName("fewer than 3 instruments is rejected")
    void tooFewInstrumentsRejected() {
        BarSeries a = flat("A", 10.0, 5);
        BarSeries b = flat("B", 20.0, 5);
        assertThrows(IllegalArgumentException.class,
                () -> CrossSectionalSignalGenerator.generate(List.of(a, b), new IdentityIndicator(), Params.empty(), 0.02));
    }

    @Test
    @DisplayName("mismatched series lengths are rejected")
    void mismatchedLengthsRejected() {
        BarSeries a = flat("A", 10.0, 5);
        BarSeries b = flat("B", 20.0, 5);
        BarSeries c = flat("C", 30.0, 6);
        assertThrows(IllegalArgumentException.class,
                () -> CrossSectionalSignalGenerator.generate(List.of(a, b, c), new IdentityIndicator(), Params.empty(), 0.02));
    }

    @Test
    @DisplayName("a non-positive target spread is rejected")
    void nonPositiveTargetSpreadRejected() {
        BarSeries a = flat("A", 10.0, 5);
        BarSeries b = flat("B", 20.0, 5);
        BarSeries c = flat("C", 30.0, 5);
        assertThrows(IllegalArgumentException.class,
                () -> CrossSectionalSignalGenerator.generate(List.of(a, b, c), new IdentityIndicator(), Params.empty(), 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> CrossSectionalSignalGenerator.generate(List.of(a, b, c), new IdentityIndicator(), Params.empty(), -0.02));
    }
}
