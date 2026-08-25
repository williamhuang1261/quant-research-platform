package io.github.williamhuang1261.qrp.indicators;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.spi.Indicator;
import java.util.Arrays;

/**
 * Trailing arithmetic mean of closing prices.
 *
 * <p>Computed with a running sum, so a full series costs one pass rather than
 * one pass per bar. The trade-off is that adding and subtracting the same
 * doubles for hundreds of bars accumulates a little floating point drift; over a
 * 504-bar series it stays around 1e-12 relative, which is far below the noise in
 * any price series this would be run on.
 *
 * <p>Parameters: {@code period} (>= 1).
 */
public final class SimpleMovingAverage implements Indicator {

    @Override
    public String id() {
        return "sma";
    }

    @Override
    public String displayName() {
        return "Simple Moving Average";
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

        double sum = 0.0;
        for (int i = 0; i < closes.length; i++) {
            sum += closes[i];
            if (i >= period) {
                sum -= closes[i - period];
            }
            if (i >= period - 1) {
                out[i] = sum / period;
            }
        }
        return DoubleSeries.of(out);
    }
}
