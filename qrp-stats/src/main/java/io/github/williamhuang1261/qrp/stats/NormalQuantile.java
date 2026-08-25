package io.github.williamhuang1261.qrp.stats;

/**
 * Inverse standard normal CDF, via Acklam's rational approximation with one
 * Halley refinement.
 *
 * <p>Needed for the jackknife's normal-approximation interval. Accurate to
 * roughly 1e-15 after refinement, which is far more than an interval built on an
 * asymptotic argument deserves, but it costs nothing and removes a lookup table
 * that would have limited callers to three confidence levels.
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
        double error = 0.5 * erfc(-x / Math.sqrt(2.0)) - p;
        double u = error * Math.sqrt(2.0 * Math.PI) * Math.exp(x * x / 2.0);
        return x - u / (1.0 + x * u / 2.0);
    }

    /** Complementary error function, Numerical Recipes' Chebyshev form. */
    private static double erfc(double x) {
        double z = Math.abs(x);
        double t = 2.0 / (2.0 + z);
        double ty = 4.0 * t - 2.0;
        double[] coefficients = {
                -1.3026537197817094, 6.4196979235649026e-1, 1.9476473204185836e-2,
                -9.561514786808631e-3, -9.46595344482036e-4, 3.66839497852761e-4,
                4.2523324806907e-5, -2.0278578112534e-5, -1.624290004647e-6,
                1.303655835580e-6, 1.5626441722e-8, -8.5238095915e-8,
                6.529054439e-9, 5.059343495e-9, -9.91364156e-10,
                -2.27365122e-10, 9.6467911e-11, 2.394038e-12,
                -6.886027e-12, 8.94487e-13, 3.13092e-13,
                -1.12708e-13, 3.81e-16, 7.106e-15};
        double d = 0.0;
        double dd = 0.0;
        for (int j = coefficients.length - 1; j > 0; j--) {
            double tmp = d;
            d = ty * d - dd + coefficients[j];
            dd = tmp;
        }
        double answer = t * Math.exp(-z * z + 0.5 * (coefficients[0] + ty * d) - dd);
        return x >= 0.0 ? answer : 2.0 - answer;
    }
}
