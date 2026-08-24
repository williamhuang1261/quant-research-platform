package io.github.williamhuang1261.qrp.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BarSeriesTest {

    private static final Instrument AAPL = Instrument.equity("aapl");
    private static final Instant DAY0 = Instant.parse("2024-01-02T21:00:00Z");

    private static Bar barAt(Instant timestamp, double close) {
        return new Bar(timestamp, close, close + 1.0, close - 1.0, close, 1_000L);
    }

    private static BarSeries series(int days) {
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            bars.add(barAt(DAY0.plus(Duration.ofDays(i)), 100.0 + i));
        }
        return BarSeries.of(AAPL, Timeframe.DAY_1, bars);
    }

    @Test
    @DisplayName("rejects out-of-order timestamps instead of sorting them")
    void rejectsOutOfOrderBars() {
        List<Bar> bars = List.of(
                barAt(DAY0.plus(Duration.ofDays(1)), 101.0),
                barAt(DAY0, 100.0));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> BarSeries.of(AAPL, Timeframe.DAY_1, bars));
        assertTrue(thrown.getMessage().contains("strictly increasing"));
    }

    @Test
    @DisplayName("rejects duplicate timestamps")
    void rejectsDuplicateTimestamps() {
        List<Bar> bars = List.of(barAt(DAY0, 100.0), barAt(DAY0, 101.0));

        assertThrows(IllegalArgumentException.class, () -> BarSeries.of(AAPL, Timeframe.DAY_1, bars));
    }

    @Test
    @DisplayName("is immutable: later edits to the source list do not leak in")
    void isImmutable() {
        List<Bar> source = new ArrayList<>(List.of(barAt(DAY0, 100.0)));
        BarSeries built = BarSeries.of(AAPL, Timeframe.DAY_1, source);

        source.add(barAt(DAY0.plus(Duration.ofDays(1)), 101.0));

        assertEquals(1, built.size());
        assertThrows(UnsupportedOperationException.class,
                () -> built.bars().add(barAt(DAY0.plus(Duration.ofDays(2)), 102.0)));
    }

    @Test
    @DisplayName("hands out a fresh close array on every call")
    void closesAreDefensivelyCopied() {
        BarSeries built = series(3);

        double[] first = built.closes();
        first[0] = -1.0;

        assertArrayEquals(new double[] {100.0, 101.0, 102.0}, built.closes(), 1e-12);
    }

    @Test
    @DisplayName("normalises the instrument symbol")
    void normalisesSymbol() {
        assertEquals("AAPL", series(1).instrument().symbol());
    }

    @Test
    @DisplayName("visibleAt exposes the current bar and nothing after it")
    void visibleAtPreventsLookAhead() {
        BarSeries built = series(5);

        BarSeries visible = built.visibleAt(2);

        assertEquals(3, visible.size());
        assertSame(built.get(2), visible.last());
    }

    @Test
    @DisplayName("reports only gaps longer than the stated tolerance")
    void findsGapsBeyondTolerance() {
        List<Bar> bars = List.of(
                barAt(DAY0, 100.0),
                barAt(DAY0.plus(Duration.ofDays(1)), 101.0),
                barAt(DAY0.plus(Duration.ofDays(6)), 102.0));
        BarSeries built = BarSeries.of(AAPL, Timeframe.DAY_1, bars);

        List<BarSeries.Gap> gaps = built.gapsLongerThan(Duration.ofDays(3));

        assertEquals(1, gaps.size());
        assertEquals(Duration.ofDays(5), gaps.get(0).duration());
        assertEquals(DAY0.plus(Duration.ofDays(1)), gaps.get(0).before().timestamp());
    }

    @Test
    @DisplayName("an empty series is legal but has no start or end")
    void emptySeriesIsLegal() {
        BarSeries empty = BarSeries.empty(AAPL, Timeframe.DAY_1);

        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());
        assertThrows(IllegalStateException.class, empty::start);
    }

    @Test
    @DisplayName("slice validates its bounds")
    void sliceValidatesBounds() {
        BarSeries built = series(3);

        assertEquals(2, built.slice(1, 3).size());
        assertThrows(IndexOutOfBoundsException.class, () -> built.slice(0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> built.slice(2, 1));
    }
}
