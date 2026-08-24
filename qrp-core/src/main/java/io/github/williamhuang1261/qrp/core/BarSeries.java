package io.github.williamhuang1261.qrp.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable, strictly time-ordered sequence of {@link Bar}s for one
 * instrument at one {@link Timeframe}.
 *
 * <p>Strict ordering is enforced at construction: duplicate or out-of-order
 * timestamps are rejected rather than sorted away, because a series that needed
 * sorting is a series whose loader has a bug worth seeing.
 *
 * <p>{@link #slice(int, int)} returns a <em>view</em>. The backtest engine hands
 * a strategy the bars it is allowed to see and nothing after them, so the view
 * has to be cheap enough to build on every bar.
 */
public final class BarSeries {

    private final Instrument instrument;
    private final Timeframe timeframe;
    private final List<Bar> bars;

    private BarSeries(Instrument instrument, Timeframe timeframe, List<Bar> bars) {
        this.instrument = instrument;
        this.timeframe = timeframe;
        this.bars = bars;
    }

    /**
     * @throws IllegalArgumentException if timestamps are not strictly increasing
     */
    public static BarSeries of(Instrument instrument, Timeframe timeframe, List<Bar> bars) {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(bars, "bars");

        List<Bar> copy = List.copyOf(bars);
        for (int i = 1; i < copy.size(); i++) {
            Instant previous = copy.get(i - 1).timestamp();
            Instant current = copy.get(i).timestamp();
            if (!current.isAfter(previous)) {
                throw new IllegalArgumentException(
                        "timestamps must be strictly increasing, but bar " + i + " (" + current
                                + ") does not follow bar " + (i - 1) + " (" + previous + ")");
            }
        }
        return new BarSeries(instrument, timeframe, copy);
    }

    public static BarSeries empty(Instrument instrument, Timeframe timeframe) {
        return of(instrument, timeframe, List.of());
    }

    public Instrument instrument() {
        return instrument;
    }

    public Timeframe timeframe() {
        return timeframe;
    }

    /** Unmodifiable; safe to hand out. */
    public List<Bar> bars() {
        return bars;
    }

    public int size() {
        return bars.size();
    }

    public boolean isEmpty() {
        return bars.isEmpty();
    }

    public Bar get(int index) {
        return bars.get(index);
    }

    /** The most recent bar, i.e. the one a strategy is currently reacting to. */
    public Bar last() {
        requireNotEmpty();
        return bars.get(bars.size() - 1);
    }

    public Instant start() {
        requireNotEmpty();
        return bars.get(0).timestamp();
    }

    public Instant end() {
        return last().timestamp();
    }

    /** Closing prices in order; a fresh array, so callers may mutate it freely. */
    public double[] closes() {
        double[] out = new double[bars.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = bars.get(i).close();
        }
        return out;
    }

    /**
     * A view over {@code [fromInclusive, toExclusive)} sharing this series'
     * backing list. O(1), because the engine builds one per bar.
     */
    public BarSeries slice(int fromInclusive, int toExclusive) {
        if (fromInclusive < 0 || toExclusive > bars.size() || fromInclusive > toExclusive) {
            throw new IndexOutOfBoundsException(
                    "slice(" + fromInclusive + ", " + toExclusive + ") out of range for size " + bars.size());
        }
        return new BarSeries(instrument, timeframe,
                Collections.unmodifiableList(bars.subList(fromInclusive, toExclusive)));
    }

    /**
     * A view of everything up to and including {@code index}: exactly what a
     * strategy is allowed to look at on that bar. Making look-ahead impossible
     * structurally beats documenting that it is forbidden.
     */
    public BarSeries visibleAt(int index) {
        Objects.checkIndex(index, bars.size());
        return slice(0, index + 1);
    }

    /**
     * Consecutive bars separated by more than {@code maxSpacing}.
     *
     * <p>Takes the tolerance as an argument instead of deriving it from the
     * timeframe: without an exchange calendar the platform cannot tell a holiday
     * from missing data, and guessing would report every weekend as a hole. The
     * caller states what a suspicious gap is for their data.
     */
    public List<Gap> gapsLongerThan(Duration maxSpacing) {
        Objects.requireNonNull(maxSpacing, "maxSpacing");
        if (maxSpacing.isNegative() || maxSpacing.isZero()) {
            throw new IllegalArgumentException("maxSpacing must be positive, got: " + maxSpacing);
        }
        List<Gap> gaps = new ArrayList<>();
        for (int i = 1; i < bars.size(); i++) {
            Bar before = bars.get(i - 1);
            Bar after = bars.get(i);
            if (Duration.between(before.timestamp(), after.timestamp()).compareTo(maxSpacing) > 0) {
                gaps.add(new Gap(before, after));
            }
        }
        return List.copyOf(gaps);
    }

    /** A hole between two consecutive bars. */
    public record Gap(Bar before, Bar after) {
        public Duration duration() {
            return Duration.between(before.timestamp(), after.timestamp());
        }
    }

    private void requireNotEmpty() {
        if (bars.isEmpty()) {
            throw new IllegalStateException("series is empty: " + instrument + " " + timeframe.id());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof BarSeries other
                && instrument.equals(other.instrument)
                && timeframe == other.timeframe
                && bars.equals(other.bars);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrument, timeframe, bars);
    }

    @Override
    public String toString() {
        return "BarSeries[" + instrument + " " + timeframe.id() + ", " + bars.size() + " bars]";
    }
}
