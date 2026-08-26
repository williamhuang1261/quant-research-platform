package io.github.williamhuang1261.qrp.stats;

/**
 * Inverse standard normal CDF, via Acklam's rational approximation with one
 * Halley refinement.
 *
 * <p>Needed for the jackknife's normal-approximation interval. Accurate to
 * roughly 1e-15 after refinement, which is far more than an interval built on an
 * asymptotic argument deserves, but it costs nothing and removes a lookup table
 * that would have limited callers to three confidence levels.
 *
 * <p>The refinement step needs the forward CDF, which now lives in
 * {@link NormalDistribution} so the option pricers can share it. This class kept
 * its own private copy until then; the values it publishes are unchanged.
 */
public final class NormalQuantile {

    private static final double[] A = {
            -3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
            1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
    private static final double[] B = {
            -5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
            6.680131188771972e+01, -1.328068155288572e+01};
    private static final double[] C = {
            -7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
            -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
    private static final double[] D = {
            7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
            3.754408661907416e+00};
    private static final double LOW = 0.02425;

    private NormalQuantile() {
    }

    /** @param p in {@code (0, 1)} */
    public static double inverseCdf(double p) {
        if (!(p > 0.0 && p < 1.0)) {
            throw new IllegalArgumentException("p must lie strictly in (0, 1), got: " + p);
        }

        double x;
        if (p < LOW) {
            double q = Math.sqrt(-2.0 * Math.log(p));
            x = (((((C[0] * q + C[1]) * q + C[2]) * q + C[3]) * q + C[4]) * q + C[5])
                    / ((((D[0] * q + D[1]) * q + D[2]) * q + D[3]) * q + 1.0);
        } else if (p <= 1.0 - LOW) {
            double q = p - 0.5;
            double r = q * q;
            x = (((((A[0] * r + A[1]) * r + A[2]) * r + A[3]) * r + A[4]) * r + A[5]) * q
                    / (((((B[0] * r + B[1]) * r + B[2]) * r + B[3]) * r + B[4]) * r + 1.0);
        } else {
            double q = Math.sqrt(-2.0 * Math.log(1.0 - p));
            x = -(((((C[0] * q + C[1]) * q + C[2]) * q + C[3]) * q + C[4]) * q + C[5])
                    / ((((D[0] * q + D[1]) * q + D[2]) * q + D[3]) * q + 1.0);
        }

        // One Halley step against the true CDF removes the approximation's error.
        double error = NormalDistribution.cdf(x) - p;
        double u = error * Math.sqrt(2.0 * Math.PI) * Math.exp(x * x / 2.0);
        return x - u / (1.0 + x * u / 2.0);
    }

}
