package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.BarSeries;
import java.time.Duration;
import java.util.List;

/**
 * Summary statistics of one backtest.
 *
 * <p>Every rate is a fraction, not a percentage: {@code 0.12} is 12 %. Ratios
 * that are genuinely undefined return {@link Double#NaN} rather than 0, because
 * a zero Sharpe and an unmeasurable one lead to different decisions.
 *
 * @param maxDrawdown the deepest peak-to-trough fall in equity, as a positive fraction
 * @param timeInMarket fraction of bars holding a non-zero position
 */
public record PerformanceMetrics(
        double initialEquity,
        double finalEquity,
        double totalReturn,
        double cagr,
        double annualisedVolatility,
        double sharpeRatio,
        double maxDrawdown,
        int tradeCount,
        double timeInMarket) {

    private static final double DAYS_PER_YEAR = 365.25;

    static PerformanceMetrics from(
            double[] equity, boolean[] invested, List<Trade> trades, BarSeries series, double periodsPerYear) {

        double initial = equity[0];
        double last = equity[equity.length - 1];
        double totalReturn = last / initial - 1.0;

        double[] returns = new double[equity.length - 1];
        for (int i = 1; i < equity.length; i++) {
            returns[i - 1] = equity[i] / equity[i - 1] - 1.0;
        }

        double mean = mean(returns);
        double standardDeviation = sampleStandardDeviation(returns, mean);
        double annualisedVolatility = standardDeviation * Math.sqrt(periodsPerYear);
        // Undefined without dispersion: a flat curve has no risk to divide by.
        double sharpe = standardDeviation > 0.0
                ? mean / standardDeviation * Math.sqrt(periodsPerYear)
                : Double.NaN;

        double investedBars = 0;
        for (boolean holding : invested) {
            if (holding) {
                investedBars++;
            }
        }

        return new PerformanceMetrics(
                initial,
                last,
                totalReturn,
                cagr(initial, last, series),
                annualisedVolatility,
                sharpe,
                maxDrawdown(equity),
                trades.size(),
                invested.length == 0 ? 0.0 : investedBars / invested.length);
    }

    /**
     * The deepest peak-to-trough decline, as a positive fraction of the peak.
     *
     * <p>Delegates to {@code qrp-stats} so a backtested path and a resampled one
     * are measured by the same definition; two implementations of "drawdown"
     * would make the Monte Carlo comparison meaningless.
     */
    public static double maxDrawdown(double[] equity) {
        return io.github.williamhuang1261.qrp.stats.EquityCurve.maxDrawdown(equity);
    }

    private static double cagr(double initial, double last, BarSeries series) {
        if (series.size() < 2 || initial <= 0.0 || last <= 0.0) {
            return Double.NaN;
        }
        double years = Duration.between(series.start(), series.end()).toDays() / DAYS_PER_YEAR;
        if (years <= 0.0) {
            return Double.NaN;
        }
        return Math.pow(last / initial, 1.0 / years) - 1.0;
    }

    private static double mean(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double sampleStandardDeviation(double[] values, double mean) {
        if (values.length < 2) {
            return 0.0;
        }
        double sumSquares = 0.0;
        for (double value : values) {
            double deviation = value - mean;
            sumSquares += deviation * deviation;
        }
        return Math.sqrt(sumSquares / (values.length - 1));
    }
}
