package io.github.williamhuang1261.qrp.indicators;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Builds a bar series from a list of closes, so tests read as the numbers they check. */
final class IndicatorFixtures {

    static final Instrument TEST = Instrument.equity("TEST");
    private static final Instant DAY0 = Instant.parse("2024-01-02T21:00:00Z");

    private IndicatorFixtures() {
    }

    static BarSeries seriesOf(double... closes) {
        List<Bar> bars = new ArrayList<>(closes.length);
        for (int i = 0; i < closes.length; i++) {
            double close = closes[i];
            bars.add(new Bar(DAY0.plus(Duration.ofDays(i)), close, close + 0.5, close - 0.5, close, 1_000L));
        }
        return BarSeries.of(TEST, Timeframe.DAY_1, bars);
    }
}
