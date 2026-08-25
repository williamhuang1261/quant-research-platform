package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Bar series built from explicit open/close pairs, so fills can be checked by hand. */
final class TestSeries {

    static final Instrument TEST = Instrument.equity("TEST");
    private static final Instant DAY0 = Instant.parse("2024-01-02T21:00:00Z");

    private TestSeries() {
    }

    /** Each row is {open, close}; high and low are widened to keep the bar valid. */
    static BarSeries of(double[][] openClose) {
        List<Bar> bars = new ArrayList<>(openClose.length);
        for (int i = 0; i < openClose.length; i++) {
            double open = openClose[i][0];
            double close = openClose[i][1];
            bars.add(new Bar(
                    DAY0.plus(Duration.ofDays(i)),
                    open,
                    Math.max(open, close) + 0.5,
                    Math.min(open, close) - 0.5,
                    close,
                    1_000L));
        }
        return BarSeries.of(TEST, Timeframe.DAY_1, bars);
    }

    /** A series whose open and close are the same price on every bar. */
    static BarSeries flatOpens(double... prices) {
        double[][] rows = new double[prices.length][2];
        for (int i = 0; i < prices.length; i++) {
            rows[i][0] = prices[i];
            rows[i][1] = prices[i];
        }
        return of(rows);
    }
}
