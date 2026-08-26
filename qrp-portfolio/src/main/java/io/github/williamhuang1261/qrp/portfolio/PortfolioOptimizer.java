package io.github.williamhuang1261.qrp.portfolio;

/**
 * Turns a set of per-instrument views and a risk estimate into target weights.
 *
 * <p>A "view" here is an expected return per instrument, however it was
 * produced — an {@code Indicator} output, a strategy's signal, or a flat
 * prior. The optimizer does not care where {@code expectedReturns} came from;
 * it only has to allocate capital across the instruments it is given, subject
 * to {@link PortfolioConstraints} and the weights the portfolio already holds.
 *
 * <p>Implementations must be stateless and safe to call from several threads:
 * a parameter sweep or a multi-period backtest calls the same instance once
 * per rebalance date. All state belongs in the returned array.
 */
public interface PortfolioOptimizer {

    /** Stable identifier used by the registry, the CLI and reports. */
    String id();

    default String displayName() {
        return id();
    }

    /**
     * @param expectedReturns one expected return per instrument, same order as
     *                        {@code covariance}'s rows/columns
     * @param covariance      an {@code n x n} covariance matrix, symmetric and
     *                        positive semi-definite, in the same instrument order
     * @param previousWeights the portfolio's current weights, same order and
     *                        length as {@code expectedReturns}; all zero for a
     *                        cold start
     * @param constraints     bounds the optimizer must respect exactly, not
     *                        merely approach
     * @return target weights, same order and length as {@code expectedReturns}
     * @throws IllegalArgumentException if the inputs are inconsistent in length
     *                                  or the covariance matrix is not square
     */
    double[] optimize(
            double[] expectedReturns,
            double[][] covariance,
            double[] previousWeights,
            PortfolioConstraints constraints);
}
