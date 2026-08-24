package io.github.williamhuang1261.qrp.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.Timeframe;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyntheticSeriesGeneratorTest {

    private static final LocalDate START = LocalDate.of(2022, 1, 3);

    private static SyntheticSeriesGenerator.Spec specWithSeed(long seed) {
        SyntheticSeriesGenerator.Spec base = SyntheticSeriesGenerator.defaultSpecs().get(0);
        return new SyntheticSeriesGenerator.Spec(base.instrument(), Timeframe.DAY_1, base.file(),
                seed, base.startPrice(), base.annualDrift(), base.annualVolatility(), base.baseVolume());
    }

    @Test
    @DisplayName("the same seed produces the same bars, which is why the data can be committed")
    void isDeterministic() {
        List<Bar> first = SyntheticSeriesGenerator.generate(specWithSeed(42L), START, 50);
        List<Bar> second = SyntheticSeriesGenerator.generate(specWithSeed(42L), START, 50);

        assertEquals(first, second);
    }

    @Test
    @DisplayName("a different seed produces a different path")
    void seedChangesThePath() {
        List<Bar> first = SyntheticSeriesGenerator.generate(specWithSeed(42L), START, 50);
        List<Bar> second = SyntheticSeriesGenerator.generate(specWithSeed(43L), START, 50);

        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("emits the requested number of weekday bars in order")
    void emitsWeekdaysOnly() {
        List<Bar> bars = SyntheticSeriesGenerator.generate(specWithSeed(7L), START, 30);

        assertEquals(30, bars.size());
        for (int i = 0; i < bars.size(); i++) {
            DayOfWeek day = bars.get(i).timestamp().atZone(ZoneOffset.UTC).getDayOfWeek();
            assertNotEquals(DayOfWeek.SATURDAY, day);
            assertNotEquals(DayOfWeek.SUNDAY, day);
            if (i > 0) {
                assertTrue(bars.get(i).timestamp().isAfter(bars.get(i - 1).timestamp()));
            }
        }
    }

    @Test
    @DisplayName("prices survive rounding to cents without breaking the bar invariants")
    void roundingKeepsBarsValid() {
        // Bar's constructor enforces high >= max(open, close) and low <= min(open, close);
        // generating a long series is the assertion that rounding never violates them.
        List<Bar> bars = SyntheticSeriesGenerator.generate(specWithSeed(99L), START, 504);

        for (Bar bar : bars) {
            assertEquals(bar.open(), round2(bar.open()), 1e-9);
            assertEquals(bar.close(), round2(bar.close()), 1e-9);
            assertTrue(bar.high() >= Math.max(bar.open(), bar.close()));
            assertTrue(bar.low() <= Math.min(bar.open(), bar.close()));
            assertTrue(bar.volume() > 0L);
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
