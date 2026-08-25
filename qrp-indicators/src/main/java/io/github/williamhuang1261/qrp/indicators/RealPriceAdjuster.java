package io.github.williamhuang1261.qrp.indicators;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import java.util.Arrays;
import java.util.Objects;

/**
 * Restates nominal closing prices in the purchasing power of a base period,
 * given a consumer price index aligned to the same bars.
 *
 * <p>Also deliberately not an {@code Indicator}: it needs a second, exogenous
 * series, and {@code Indicator.compute} takes only the bars and numeric
 * parameters. Widening the SPI so one economic transform could fit would make
 * every future indicator carry a parameter it does not use.
 *
 * <p>The index must be supplied by the caller, aligned index-for-index with the
 * bar series. This module ships no CPI data: an inflation series is a published
 * statistic with its own vintages and revisions, and bundling a stale copy would
 * invite backtests against numbers nobody can reproduce.
 */
public final class RealPriceAdjuster {

    private RealPriceAdjuster() {
    }

    /**
     * @param priceIndex CPI level per bar, same length as {@code series}
     * @param baseIndexLevel the index level whose dollars the result is expressed in
     * @return real closes; {@code NaN} wherever the index is undefined
     */
    public static DoubleSeries toRealPrices(
            BarSeries series, DoubleSeries priceIndex, double baseIndexLevel) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(priceIndex, "priceIndex");
        if (priceIndex.size() != series.size()) {
            throw new IllegalArgumentException("priceIndex has " + priceIndex.size()
                    + " values but the series has " + series.size() + " bars");
        }
        if (!Double.isFinite(baseIndexLevel) || baseIndexLevel <= 0.0) {
            throw new IllegalArgumentException(
                    "baseIndexLevel must be finite and positive, got: " + baseIndexLevel);
        }

        double[] closes = series.closes();
        double[] real = new double[closes.length];
        Arrays.fill(real, Double.NaN);
        for (int i = 0; i < closes.length; i++) {
            double level = priceIndex.get(i);
            if (Double.isNaN(level)) {
                continue;
            }
            if (level <= 0.0) {
                throw new IllegalArgumentException(
                        "priceIndex must be positive, got " + level + " at index " + i);
            }
            real[i] = closes[i] * baseIndexLevel / level;
        }
        return DoubleSeries.of(real);
    }
}
