package io.github.williamhuang1261.qrp.core;

import java.time.Duration;

/**
 * Bar sampling interval.
 *
 * <p>{@link #duration()} is the <em>nominal</em> spacing between consecutive
 * bars. Real series are sparser than nominal because markets close, so nothing
 * in the platform assumes bar {@code i + 1} sits exactly one duration after bar
 * {@code i}; see {@link BarSeries#gapsLongerThan(Duration)}.
 */
public enum Timeframe {
    MINUTE_1("1m", Duration.ofMinutes(1)),
    MINUTE_5("5m", Duration.ofMinutes(5)),
    MINUTE_15("15m", Duration.ofMinutes(15)),
    HOUR_1("1h", Duration.ofHours(1)),
    DAY_1("1d", Duration.ofDays(1)),
    WEEK_1("1w", Duration.ofDays(7));

    private final String id;
    private final Duration duration;

    Timeframe(String id, Duration duration) {
        this.id = id;
        this.duration = duration;
    }

    /** Stable short identifier used in file names, the CLI and the SPI. */
    public String id() {
        return id;
    }

    public Duration duration() {
        return duration;
    }

    public static Timeframe fromId(String id) {
        for (Timeframe tf : values()) {
            if (tf.id.equalsIgnoreCase(id)) {
                return tf;
            }
        }
        throw new IllegalArgumentException("unknown timeframe id: " + id);
    }
}
