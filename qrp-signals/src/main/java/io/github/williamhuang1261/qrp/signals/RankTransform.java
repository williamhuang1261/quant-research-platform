package io.github.williamhuang1261.qrp.signals;

import java.util.Arrays;

/**
 * Converts a value array into ranks, the input Spearman correlation needs.
 *
 * <p>Ties are resolved by average rank: two equal values each get the mean of
 * the positions they jointly occupy, so a tie never breaks the way sort order
 * happens to break it. This is the standard convention behind Spearman's rho;
 * a naive "sort and index" rank would silently pick a favorite among equal
 * values.
 */
public final class RankTransform {

    private RankTransform() {
    }

    /**
     * @return one rank per input value, in the original order, on a
     *         {@code 1..n} scale with average ranks for ties
     */
    public static double[] ranks(double[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        int n = values.length;
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("values must be finite, got: " + value);
            }
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(values[a], values[b]));

        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && values[order[j + 1]] == values[order[i]]) {
                j++;
            }
            // Positions i..j (0-indexed) are tied; their shared rank is the
            // average of the 1-indexed positions they occupy.
            double averageRank = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) {
                ranks[order[k]] = averageRank;
            }
            i = j + 1;
        }
        return ranks;
    }
}
