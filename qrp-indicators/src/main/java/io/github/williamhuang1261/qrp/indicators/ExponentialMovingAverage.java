package io.github.williamhuang1261.qrp.indicators;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.spi.Indicator;
import java.util.Arrays;

/**
 * Exponentially weighted mean of closing prices, smoothing factor
 * {@code 2 / (period + 1)}.
 *
 * <p>Seeded with the simple average of the first {@code period} closes rather
 * than with the first close. Seeding on one price makes the early values a
 * function of a single observation, and a strategy that trades the first weeks
 * of a series would be trading that artefact.
 *
 * <p>Parameters: {@code period} (>= 1).
 */
public final class ExponentialMovingAverage implements Indicator {

    @Override
    public String id() {
        return "ema";
    }

    @Override
    public String displayName() {
        return "Exponential Moving Average";
    }

    @Override
    public int warmup(Params params) {
        return Periods.require(params, id(), 1) - 1;
    }

    @Override
    public DoubleSeries compute(BarSeries series, Params params) {
        int period = Periods.require(params, id(), 1);
        double[] closes = series.closes();
        double[] out = new double[closes.length];
        Arrays.fill(out, Double.NaN);
        if (closes.length < period) {
            return DoubleSeries.of(out);
        }

        double alpha = 2.0 / (period + 1.0);
        double seed = 0.0;
        for (int i = 0; i < period; i++) {
            seed += closes[i];
        }
        double value = seed / period;
        out[period - 1] = value;

        for (int i = period; i < closes.length; i++) {
            value = alpha * closes[i] + (1.0 - alpha) * value;
            out[i] = value;
        }
        return DoubleSeries.of(out);
    }
}
