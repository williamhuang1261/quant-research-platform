package io.github.williamhuang1261.qrp.signals;

import io.github.williamhuang1261.qrp.stats.NormalDistribution;

/**
 * Whether a signal's mean information coefficient is distinguishable from
 * zero, judged across the IC time series rather than from a single period.
 *
 * <p>The test is a large-sample z-approximation: {@code z = mean(IC) /
 * (stdDev(IC) / sqrt(n))}, with a two-sided p-value read off the standard
 * normal CDF. This is not a Student's-t test — the platform has no
 * t-distribution implementation, and this class does not add one. The normal
 * approximation is reasonable once the IC series has a few dozen periods and
 * understates the true uncertainty at very few, the same asymptotic caveat
 * {@code JackknifeCorrelation}'s own interval already carries for a small
 * sample. Documented here rather than silently assumed.
 *
 * @param meanIc        mean IC across all periods
 * @param standardError {@code stdDev(IC) / sqrt(periods)}
 * @param zStatistic    {@code meanIc / standardError}; {@code 0} when both
 *                      are zero (every period agreed on an IC of exactly
 *                      zero), signed infinity when the IC never varied but
 *                      was never zero either
 * @param pValue        two-sided p-value against the null that the true mean
 *                      IC is zero
 * @param periods       number of periods the statistic was computed over
 */
public record SignalSignificance(double meanIc, double standardError, double zStatistic, double pValue, int periods) {

    public SignalSignificance {
        if (standardError < 0.0) {
            throw new IllegalArgumentException("standardError must not be negative, got: " + standardError);
        }
        if (!(pValue >= 0.0 && pValue <= 1.0)) {
            throw new IllegalArgumentException("pValue must lie in [0, 1], got: " + pValue);
        }
        if (periods < 2) {
            throw new IllegalArgumentException("periods must be at least 2, got: " + periods);
        }
    }

    /**
     * @param icSeries one IC value per period; every value must be finite —
     *                 a caller with periods of zero cross-sectional variation
     *                 (an {@code InformationCoefficient} of {@code NaN}) must
     *                 filter those periods out before calling, since a
     *                 significance test cannot be computed over an undefined
     *                 correlation
     */
    public static SignalSignificance of(double[] icSeries) {
        if (icSeries.length < 2) {
            throw new IllegalArgumentException(
                    "need at least 2 periods to estimate a standard error, got: " + icSeries.length);
        }
        int n = icSeries.length;
        double mean = 0.0;
        for (double ic : icSeries) {
            if (!Double.isFinite(ic)) {
                throw new IllegalArgumentException("icSeries must be finite; filter NaN periods before calling");
            }
            mean += ic;
        }
        mean /= n;

        double sumSquares = 0.0;
        for (double ic : icSeries) {
            double deviation = ic - mean;
            sumSquares += deviation * deviation;
        }
        double sampleVariance = sumSquares / (n - 1);
        double standardError = Math.sqrt(sampleVariance / n);

        double z;
        double pValue;
        if (standardError == 0.0) {
            if (mean == 0.0) {
                z = 0.0;
                pValue = 1.0;
            } else {
                z = mean > 0.0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                pValue = 0.0;
            }
        } else {
            z = mean / standardError;
            pValue = 2.0 * (1.0 - NormalDistribution.cdf(Math.abs(z)));
        }

        return new SignalSignificance(mean, standardError, z, pValue, n);
    }

    /** True when {@code pValue} falls below {@code alpha}, e.g. {@code alpha = 0.05}. */
    public boolean isSignificant(double alpha) {
        if (!(alpha > 0.0 && alpha < 1.0)) {
            throw new IllegalArgumentException("alpha must lie strictly in (0, 1), got: " + alpha);
        }
        return pValue < alpha;
    }
}
