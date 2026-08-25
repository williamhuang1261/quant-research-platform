package io.github.williamhuang1261.qrp.stats;

import java.util.Arrays;

/**
 * Empirical percentiles by linear interpolation between order statistics.
 *
 * <p>This is the definition R calls type 7 and NumPy uses by default. There are
 * nine defensible ones; the point is that this code names which, because two
 * bootstrap intervals computed under different conventions differ by more than
 * readers expect at the tails.
 */
public final class Percentiles {

    private Percentiles() {
    }

    /**
     * @param values not modified; sorted internally
     * @param quantile in {@code [0, 1]}
     */
    public static double of(double[] values, double quantile) {
        if (values.length == 0) {
            throw new IllegalArgumentException("cannot take a percentile of an empty sample");
        }
        if (!(quantile >= 0.0 && quantile <= 1.0)) {
            throw new IllegalArgumentException("quantile must lie in [0, 1], got: " + quantile);
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return ofSorted(sorted, quantile);
    }

    /** Same definition, for a sample already sorted ascending. */
    public static double ofSorted(double[] sorted, double quantile) {
        double position = quantile * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted[lower];
        }
        double weight = position - lower;
        return sorted[lower] * (1.0 - weight) + sorted[upper] * weight;
    }
}
