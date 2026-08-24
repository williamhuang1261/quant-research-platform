package io.github.williamhuang1261.qrp.core.spi;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;

/**
 * A transformation from a bar series to one value per bar.
 *
 * <p>Implementations must be stateless and safe to call from several threads:
 * a parameter sweep runs the same indicator instance over many series at once.
 * All state belongs in the returned {@link DoubleSeries}.
 *
 * <p>The result must have the same length as the input series, with
 * {@link Double#NaN} for every index before {@link #warmup(Params)}.
 */
public interface Indicator {

    /** Stable identifier used by the registry, the CLI and saved configurations. */
    String id();

    default String displayName() {
        return id();
    }

    /**
     * Number of leading bars that cannot produce a value, e.g. 19 for a 20-bar
     * moving average. Callers use it to align comparisons across indicators.
     */
    default int warmup(Params params) {
        return 0;
    }

    /**
     * @return a series of {@code series.size()} values, aligned index-for-index
     * @throws IllegalArgumentException if the parameters are invalid for this indicator
     */
    DoubleSeries compute(BarSeries series, Params params);
}
