package io.github.williamhuang1261.qrp.stats;

/**
 * The standard normal density and distribution function.
 *
 * <p>This class exists because {@link NormalQuantile} already carried a private
 * {@code erfc} for its Halley refinement, and option pricing needs the forward
 * CDF that the same {@code erfc} provides. Two copies of a Chebyshev expansion
 * in one repository is one copy too many, so the routine moved here and
 * {@code NormalQuantile} delegates to it. Its published values are unchanged and
 * {@code NormalQuantileTest} still pins them.
 *
 * <p>{@link #cdf} is built on {@code erfc} rather than on a direct rational
 * approximation such as Hart's, because {@code erfc} underflows gracefully: at
 * {@code x = -40} the answer is around 1e-350 and the relative error stays
 * bounded, where a polynomial in the tail returns a flat zero. Deep out of the
 * money options are exactly where a pricer gets asked for that number.
 */
public final class NormalDistribution {

    private static final double INV_SQRT_2PI = 0.3989422804014327;
    private static final double INV_SQRT_2 = 0.7071067811865476;

    private NormalDistribution() {
    }

    /** Standard normal probability density at {@code x}. */
    public static double pdf(double x) {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("x must be finite, got: " + x);
        }
        return INV_SQRT_2PI * Math.exp(-0.5 * x * x);
    }

    /** Standard normal cumulative probability, {@code P(Z <= x)}. */
    public static double cdf(double x) {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("x must be finite, got: " + x);
        }
        return 0.5 * erfc(-x * INV_SQRT_2);
    }

    /**
     * Complementary error function, Numerical Recipes' Chebyshev form.
     *
     * <p>Accurate to roughly 1.2e-7 relative, which is the limit this whole
     * family of approximations sits at; {@link NormalQuantile} adds a Halley
     * step on top when it needs more.
     */
    public static double erfc(double x) {
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
