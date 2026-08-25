package io.github.williamhuang1261.qrp.indicators;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.spi.Indicator;
import java.util.Arrays;

/**
 * Wilder's Relative Strength Index over closing prices, in {@code [0, 100]}.
 *
 * <p>Uses Wilder's smoothing, {@code avg = (avg * (period - 1) + current) / period},
 * not a simple mean of the last {@code period} changes. The two differ by enough
 * to move a 30/70 crossing by several bars, and every published RSI level assumes
 * the smoothed form.
 *
 * <p>A window with no losses returns 100 rather than dividing by zero.
 *
 * <p>Parameters: {@code period} (>= 2, conventionally 14).
 */
public final class RelativeStrengthIndex implements Indicator {

    @Override
    public String id() {
        return "rsi";
    }

    @Override
    public String displayName() {
        return "Relative Strength Index";
    }

    @Override
    public int warmup(Params params) {
        return Periods.require(params, id(), 2);
    }

    @Override
    public DoubleSeries compute(BarSeries series, Params params) {
        int period = Periods.require(params, id(), 2);
        double[] closes = series.closes();
        double[] out = new double[closes.length];
        Arrays.fill(out, Double.NaN);
        if (closes.length <= period) {
            return DoubleSeries.of(out);
        }

        double averageGain = 0.0;
        double averageLoss = 0.0;
        for (int i = 1; i <= period; i++) {
            double change = closes[i] - closes[i - 1];
            averageGain += Math.max(change, 0.0);
            averageLoss += Math.max(-change, 0.0);
        }
        averageGain /= period;
        averageLoss /= period;
        out[period] = rsi(averageGain, averageLoss);

        for (int i = period + 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            averageGain = (averageGain * (period - 1) + Math.max(change, 0.0)) / period;
            averageLoss = (averageLoss * (period - 1) + Math.max(-change, 0.0)) / period;
            out[i] = rsi(averageGain, averageLoss);
        }
        return DoubleSeries.of(out);
    }

    private static double rsi(double averageGain, double averageLoss) {
        if (averageLoss == 0.0) {
            return 100.0;
        }
        return 100.0 - 100.0 / (1.0 + averageGain / averageLoss);
    }
}
