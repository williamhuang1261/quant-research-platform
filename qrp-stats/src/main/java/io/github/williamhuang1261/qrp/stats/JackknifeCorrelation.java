package io.github.williamhuang1261.qrp.stats;

/**
 * Leave-one-out jackknife of the Pearson correlation between two series.
 *
 * <p>A correlation computed on 500 overlapping observations comes with no error
 * bar, and a signal is usually kept or discarded on exactly that number. The
 * jackknife gives a bias estimate and a standard error by recomputing the
 * statistic with each observation removed, which costs one pass per observation
 * and needs no distributional assumption about the data.
 *
 * <p>The interval is a normal approximation around the bias-corrected estimate.
 * That is an asymptotic argument: it is reasonable for a few hundred
 * observations and poor for a dozen, and the correlation's bounded range means
 * an interval near ±1 can extend past it. Both limitations are the reason the
 * bias and the standard error are reported separately rather than folded away.
 *
 * @param estimate       correlation on the full sample
 * @param biasCorrected  {@code n * r - (n - 1) * mean(leave-one-out r)}
 * @param bias           the jackknife bias estimate itself
 * @param standardError  jackknife standard error
 * @param interval       normal-approximation interval around the corrected estimate
 */
public record JackknifeCorrelation(
        double estimate,
        double biasCorrected,
        double bias,
        double standardError,
        ConfidenceInterval interval) {

    public static JackknifeCorrelation of(double[] x, double[] y, double level) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(
                    "series must be the same length, got " + x.length + " and " + y.length);
        }
        if (x.length < 3) {
            throw new IllegalArgumentException(
                    "the jackknife needs at least 3 observations, got: " + x.length);
        }
        if (!(level > 0.0 && level < 1.0)) {
            throw new IllegalArgumentException("level must lie strictly in (0, 1), got: " + level);
        }

        int n = x.length;
        double full = correlation(x, y);

        double[] partial = new double[n];
        for (int omitted = 0; omitted < n; omitted++) {
            partial[omitted] = correlation(without(x, omitted), without(y, omitted));
        }

        double partialMean = 0.0;
        for (double value : partial) {
            partialMean += value;
        }
        partialMean /= n;

        double biasCorrected = n * full - (n - 1) * partialMean;
        double bias = (n - 1) * (partialMean - full);

        double sumSquares = 0.0;
        for (double value : partial) {
            double deviation = value - partialMean;
            sumSquares += deviation * deviation;
        }
        double standardError = Math.sqrt((n - 1.0) / n * sumSquares);

        double z = NormalQuantile.inverseCdf(1.0 - (1.0 - level) / 2.0);
        ConfidenceInterval interval = new ConfidenceInterval(
                biasCorrected,
                biasCorrected - z * standardError,
                biasCorrected + z * standardError,
                level);

        return new JackknifeCorrelation(full, biasCorrected, bias, standardError, interval);
    }

    /** Pearson correlation; NaN when either series has no variation. */
    public static double correlation(double[] x, double[] y) {
        int n = x.length;
        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < n; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= n;
        meanY /= n;

        double covariance = 0.0;
        double varianceX = 0.0;
        double varianceY = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            covariance += dx * dy;
            varianceX += dx * dx;
            varianceY += dy * dy;
        }
        if (varianceX == 0.0 || varianceY == 0.0) {
            return Double.NaN;
        }
        return covariance / Math.sqrt(varianceX * varianceY);
    }

    private static double[] without(double[] values, int index) {
        double[] copy = new double[values.length - 1];
        System.arraycopy(values, 0, copy, 0, index);
        System.arraycopy(values, index + 1, copy, index, values.length - index - 1);
        return copy;
    }
}
