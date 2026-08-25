package io.github.williamhuang1261.qrp.indicators;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.spi.Indicator;
import java.util.Arrays;

/**
 * Annualised standard deviation of trailing log returns.
 *
 * <p>Log returns, because they add across periods and keep the annualisation by
 * {@code sqrt(periodsPerYear)} coherent. The sample standard deviation uses
 * {@code n - 1}, which is why the window must hold at least two returns.
 *
 * <p>Parameters: {@code period} (>= 2, number of returns in the window),
 * {@code annualization} (periods per year, default 252).
 */
public final class RollingVolatility implements Indicator {

    static final String ANNUALIZATION = "annualization";
    private static final double DEFAULT_ANNUALIZATION = 252.0;

    @Override
    public String id() {
        return "volatility";
    }

    @Override
    public String displayName() {
        return "Rolling Volatility (annualised)";
    }

    @Override
    public int warmup(Params params) {
        // One extra bar: n returns need n + 1 prices.
        return Periods.require(params, id(), 2);
    }

    @Override
    public DoubleSeries compute(BarSeries series, Params params) {
        int period = Periods.require(params, id(), 2);
        double annualization = params.getOrDefault(ANNUALIZATION, DEFAULT_ANNUALIZATION);
        if (annualization <= 0.0) {
            throw new IllegalArgumentException(
                    id() + " needs a positive " + ANNUALIZATION + ", got: " + annualization);
        }

        double[] closes = series.closes();
        double[] out = new double[closes.length];
        Arrays.fill(out, Double.NaN);
        if (closes.length <= period) {
            return DoubleSeries.of(out);
        }

        double[] logReturns = new double[closes.length];
        Arrays.fill(logReturns, Double.NaN);
        for (int i = 1; i < closes.length; i++) {
            logReturns[i] = Math.log(closes[i] / closes[i - 1]);
        }

        double scale = Math.sqrt(annualization);
        for (int i = period; i < closes.length; i++) {
            double mean = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                mean += logReturns[j];
            }
            mean /= period;

            double sumSquares = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                double deviation = logReturns[j] - mean;
                sumSquares += deviation * deviation;
            }
            out[i] = Math.sqrt(sumSquares / (period - 1)) * scale;
        }
        return DoubleSeries.of(out);
    }
}
