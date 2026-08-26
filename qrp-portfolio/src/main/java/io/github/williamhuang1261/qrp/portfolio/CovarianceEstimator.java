package io.github.williamhuang1261.qrp.portfolio;

import io.github.williamhuang1261.qrp.stats.JackknifeCorrelation;
import java.util.Objects;

/**
 * Builds a sample covariance matrix from several instruments' return series.
 *
 * <p>The diagonal is each instrument's sample variance. Every off-diagonal
 * entry reuses {@link JackknifeCorrelation#correlation(double[], double[])} —
 * the same pairwise correlation the platform already uses to judge whether a
 * signal is real — scaled back up by the two instruments' standard
 * deviations: {@code cov(i, j) = corr(i, j) * std(i) * std(j)}. This is
 * exactly the sample covariance formula; going through the correlation
 * function rather than a separate covariance sum means the two ever agree by
 * construction, not by two implementations happening to match.
 *
 * <p>Works on plain arrays, matching {@code qrp-stats}'s convention: nothing
 * here depends on how the returns were computed.
 */
public final class CovarianceEstimator {

    private CovarianceEstimator() {}

    /**
     * @param returns one return series per instrument, {@code returns[i]} for
     *                instrument {@code i}; every series must have the same
     *                length, at least 2
     * @return an {@code n x n} symmetric covariance matrix, same instrument
     *         order as {@code returns}
     */
    public static double[][] estimate(double[][] returns) {
        Objects.requireNonNull(returns, "returns");
        int n = returns.length;
        if (n == 0) {
            throw new IllegalArgumentException("need at least one instrument, got 0");
        }
        int length = returns[0].length;
        if (length < 2) {
            throw new IllegalArgumentException(
                    "need at least 2 observations per instrument, got: " + length);
        }
        for (int i = 0; i < n; i++) {
            Objects.requireNonNull(returns[i], "returns[" + i + "]");
            if (returns[i].length != length) {
                throw new IllegalArgumentException(
                        "every return series must have the same length; instrument 0 has "
                                + length + ", instrument " + i + " has " + returns[i].length);
            }
        }

        double[] stdDev = new double[n];
        double[][] covariance = new double[n][n];
        for (int i = 0; i < n; i++) {
            double variance = sampleVariance(returns[i]);
            stdDev[i] = Math.sqrt(variance);
            covariance[i][i] = variance;
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double correlation = JackknifeCorrelation.correlation(returns[i], returns[j]);
                double cov = correlation * stdDev[i] * stdDev[j];
                covariance[i][j] = cov;
                covariance[j][i] = cov;
            }
        }
        return covariance;
    }

    private static double sampleVariance(double[] x) {
        int n = x.length;
        double mean = 0.0;
        for (double v : x) {
            mean += v;
        }
        mean /= n;

        double sumSquares = 0.0;
        for (double v : x) {
            double deviation = v - mean;
            sumSquares += deviation * deviation;
        }
        return sumSquares / (n - 1);
    }
}
