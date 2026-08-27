package io.github.williamhuang1261.qrp.signals;

import io.github.williamhuang1261.qrp.stats.JackknifeCorrelation;

/**
 * Spearman rank correlation between a cross-sectional signal and the forward
 * return it is trying to predict, the "information coefficient" a factor
 * researcher scores a signal by.
 *
 * <p>Built on rank-transformed arrays fed into
 * {@link JackknifeCorrelation#correlation(double[], double[])} rather than a
 * second correlation formula — Spearman's rho is, by definition, Pearson
 * correlation computed on ranks, and the platform already has exactly one
 * implementation of Pearson correlation. {@code qrp-portfolio}'s
 * {@code CovarianceEstimator} makes the same choice for the same reason: one
 * "how related are these two series" routine in the codebase, not two that
 * could quietly disagree.
 */
public final class InformationCoefficient {

    private InformationCoefficient() {
    }

    /**
     * The IC for a single period: one signal value and one forward return per
     * instrument, in the same order.
     *
     * @return Spearman rank correlation, {@code NaN} if either array has no
     *         variation (every instrument tied)
     */
    public static double spearman(double[] signal, double[] forwardReturns) {
        if (signal.length != forwardReturns.length) {
            throw new IllegalArgumentException(
                    "signal and forwardReturns must be the same length, got " + signal.length
                            + " and " + forwardReturns.length);
        }
        if (signal.length < 3) {
            throw new IllegalArgumentException(
                    "need at least 3 instruments for a rank correlation, got: " + signal.length);
        }
        return JackknifeCorrelation.correlation(RankTransform.ranks(signal), RankTransform.ranks(forwardReturns));
    }

    /**
     * The IC time series: one Spearman IC per period.
     *
     * @param signals         {@code signals[t]} is the cross-sectional signal
     *                        (one value per instrument) at period {@code t}
     * @param forwardReturns  {@code forwardReturns[t]} is the matching
     *                        cross-sectional forward return at period
     *                        {@code t}, same shape as {@code signals}
     * @return one IC per period, same order and length as the inputs; a
     *         period where either row has no cross-sectional variation
     *         reports {@code NaN}
     */
    public static double[] perPeriod(double[][] signals, double[][] forwardReturns) {
        if (signals.length != forwardReturns.length) {
            throw new IllegalArgumentException(
                    "signals and forwardReturns must have the same number of periods, got "
                            + signals.length + " and " + forwardReturns.length);
        }
        double[] ic = new double[signals.length];
        for (int t = 0; t < signals.length; t++) {
            ic[t] = spearman(signals[t], forwardReturns[t]);
        }
        return ic;
    }
}
