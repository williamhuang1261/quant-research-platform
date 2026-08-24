package io.github.williamhuang1261.qrp.core;

import java.time.Instant;
import java.util.Objects;

/**
 * One OHLCV observation, validated at construction.
 *
 * <p>Bad bars are the most common cause of a backtest that looks brilliant, so
 * the invariants are enforced here rather than trusted from the data source: a
 * high below the open, a negative price or a negative volume cannot exist as an
 * object. {@code timestamp} is the <em>close</em> of the interval, which is the
 * only instant at which the bar is knowable.
 */
public record Bar(Instant timestamp, double open, double high, double low, double close, long volume)
        implements Comparable<Bar> {

    public Bar {
        Objects.requireNonNull(timestamp, "timestamp");
        requirePositiveFinite(open, "open");
        requirePositiveFinite(high, "high");
        requirePositiveFinite(low, "low");
        requirePositiveFinite(close, "close");
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative, got: " + volume);
        }
        if (high < low) {
            throw new IllegalArgumentException(
                    "high (" + high + ") must not be below low (" + low + ")");
        }
        if (high < Math.max(open, close)) {
            throw new IllegalArgumentException(
                    "high (" + high + ") must not be below open/close");
        }
        if (low > Math.min(open, close)) {
            throw new IllegalArgumentException(
                    "low (" + low + ") must not be above open/close");
        }
    }

    /** Mean of high, low and close: the reference price of several indicators. */
    public double typicalPrice() {
        return (high + low + close) / 3.0;
    }

    /** High minus low, the bar's traded range. */
    public double range() {
        return high - low;
    }

    @Override
    public int compareTo(Bar other) {
        return timestamp.compareTo(other.timestamp);
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got: " + value);
        }
        if (value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive, got: " + value);
        }
    }
}
