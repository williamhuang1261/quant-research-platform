package io.github.williamhuang1261.qrp.stats;

import java.util.Arrays;

/**
 * Historical tail risk, computed from the observed return distribution rather
 * than from a fitted normal.
 *
 * <p>Both figures are reported as positive loss magnitudes: a 95 % VaR of 0.021
 * means "on the worst 5 % of days, at least 2.1 % was lost". Returning them as
 * negative numbers reads naturally in code and badly in a report, and mixing the
 * two conventions is how a risk limit ends up with the wrong sign.
 *
 * <p>Historical, not parametric, because the empirical tail of a return series
 * is fatter than a normal fitted to it, and the whole point of the measure is
 * the tail.
 */
public final class RiskMetrics {

    private RiskMetrics() {
    }

    /**
     * @param level confidence, e.g. 0.95
     * @return the loss at the {@code 1 - level} quantile, as a positive number
     *         (0 when that quantile is a gain)
     */
    public static double valueAtRisk(double[] returns, double level) {
        requireUsable(returns, level);
        double quantile = Percentiles.of(returns, 1.0 - level);
        return Math.max(0.0, -quantile);
    }

    /**
     * Mean loss in the tail beyond the VaR, as a positive number: what the bad
     * days cost on average once they are bad. Always at least the VaR.
     */
    public static double expectedShortfall(double[] returns, double level) {
        requireUsable(returns, level);
        double[] sorted = returns.clone();
        Arrays.sort(sorted);
        double threshold = Percentiles.ofSorted(sorted, 1.0 - level);

        double sum = 0.0;
        int count = 0;
        for (double value : sorted) {
            if (value <= threshold) {
                sum += value;
                count++;
            }
        }
        if (count == 0) {
            return Math.max(0.0, -threshold);
        }
        return Math.max(0.0, -(sum / count));
    }

    private static void requireUsable(double[] returns, double level) {
        if (returns.length == 0) {
            throw new IllegalArgumentException("returns must not be empty");
        }
        if (!(level > 0.0 && level < 1.0)) {
            throw new IllegalArgumentException("level must lie strictly in (0, 1), got: " + level);
        }
    }
}
