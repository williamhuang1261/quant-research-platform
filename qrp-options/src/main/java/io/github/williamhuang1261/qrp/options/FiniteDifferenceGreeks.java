package io.github.williamhuang1261.qrp.options;

/**
 * Central-difference delta and gamma over any pricer, used to check the
 * analytic Greeks without assuming a closed form exists.
 *
 * <p>{@link BlackScholesMerton#greeks} already has its own analytic formulas;
 * this class exists so the <em>same</em> numerical check can also be pointed at
 * a pricer that has no closed form for its sensitivities, such as
 * {@link CoxRossRubinstein}. Only spot sensitivities are estimated here,
 * deliberately: a binomial tree's price is smooth in spot for a fixed step
 * count, but has kinks in volatility, time and the rate as a node crosses the
 * strike, so a central difference in those inputs would sometimes measure a
 * discretization artifact rather than the Greek.
 *
 * <p>Central differencing, not forward, because the error is {@code O(h^2)}
 * rather than {@code O(h)}.
 */
public final class FiniteDifferenceGreeks {

    /** A pricer of one contract, closed over its type and exercise style. */
    @FunctionalInterface
    public interface Pricer {
        double price(BlackScholesInputs inputs);
    }

    private FiniteDifferenceGreeks() {
    }

    /**
     * @return a {@link Greeks} carrying only {@code delta} and {@code gamma};
     *         vega, theta and rho are {@link Double#NaN} and must not be read
     */
    public static Greeks estimateSpotGreeks(Pricer pricer, BlackScholesInputs in, double spotStep) {
        if (spotStep <= 0.0) {
            throw new IllegalArgumentException("spotStep must be positive, got: " + spotStep);
        }
        double priceUp = pricer.price(in.withSpot(in.spot() + spotStep));
        double priceHere = pricer.price(in);
        double priceDown = pricer.price(in.withSpot(in.spot() - spotStep));

        double delta = (priceUp - priceDown) / (2.0 * spotStep);
        double gamma = (priceUp - 2.0 * priceHere + priceDown) / (spotStep * spotStep);

        return new Greeks(delta, gamma, Double.NaN, Double.NaN, Double.NaN);
    }
}
