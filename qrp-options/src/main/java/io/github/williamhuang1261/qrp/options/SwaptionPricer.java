package io.github.williamhuang1261.qrp.options;

import io.github.williamhuang1261.qrp.stats.NormalDistribution;

/**
 * European swaption pricing under Black-76 on the forward par swap rate.
 *
 * <p>A payer swaption is the option to enter a swap paying fixed at the
 * strike; a receiver swaption, to enter one receiving fixed. Both are priced
 * the same way an option on a future is: lognormal in the forward, zero
 * carry, {@link OptionType#CALL} standing in for payer and {@link
 * OptionType#PUT} for receiver by direct analogy (a payer gains when rates
 * rise, exactly like a call on the rate).
 *
 * <p><b>Reuses {@link BlackScholesMerton}'s {@code d1}/{@code d2} rather than
 * re-deriving them</b>: constructing a {@link BlackScholesInputs#future} with
 * the forward par swap rate standing in for the futures price produces
 * exactly Black-76's {@code d1}/{@code d2}, since that factory already sets
 * zero carry ({@code q = r}). The one genuine difference from pricing an
 * option on a future is what {@code N(d1)}/{@code N(d2)} get multiplied by:
 * a future discounts by a single discount factor to expiry, but a swaption's
 * payoff is an annuity of {@code |F - K|} received across the swap's whole
 * remaining life, not a single date. So this class does not call {@link
 * BlackScholesMerton#price}; it takes {@code d1}/{@code d2} from there and
 * scales by {@link SwapValuation#annuity()} itself, multiplied by {@link
 * SwapValuation#notional()} since {@code annuity()} is deliberately a
 * per-unit-notional quantity (see its javadoc).
 *
 * <p><b>Flat volatility, not a fitted surface.</b> {@link VolatilitySurface}
 * is built from a chain of market option quotes; this module has no
 * swaption-quote data to fit one from, and fabricating quotes to populate a
 * surface would be worse than the honest alternative: take a flat volatility
 * as an input. Fitting a real swaption vol surface (or cube, across
 * expiry/tenor/strike) is a stated, scoped-out extension -- see {@code
 * docs/spec-swaps.md}.
 */
public final class SwaptionPricer {

    private SwaptionPricer() {
    }

    /**
     * Fair value of a European swaption, in the same currency units as the
     * swap's notional.
     *
     * @param type            {@link OptionType#CALL} for a payer swaption,
     *                        {@link OptionType#PUT} for a receiver
     * @param swap            the underlying swap; its {@link
     *                        SwapValuation#annuity()} and {@link
     *                        SwapValuation#parRate()} (the forward par rate)
     *                        drive the pricing
     * @param strike          the swaption's strike, as a decimal rate
     * @param expiryYears     year fraction until the swaption's exercise date
     * @param flatVolatility  annualized lognormal volatility of the forward
     *                        swap rate, as a decimal
     * @param riskFreeRate    continuously compounded rate used only to
     *                        satisfy {@link BlackScholesInputs}'s shape; it
     *                        cancels out of {@code d1}/{@code d2} under zero
     *                        carry and plays no role in the annuity scaling
     */
    public static double price(
            OptionType type,
            SwapValuation swap,
            double strike,
            double expiryYears,
            double flatVolatility,
            double riskFreeRate) {
        requireArguments(type, swap, strike, expiryYears, flatVolatility);

        double forward = swap.parRate();
        double annuity = swap.notional() * swap.annuity();

        BlackScholesInputs inputs = BlackScholesInputs.future(
                forward, strike, expiryYears, flatVolatility, riskFreeRate);

        if (inputs.isDeterministic()) {
            return annuity * type.payoff(forward, strike);
        }

        double d1 = BlackScholesMerton.d1(inputs);
        double d2 = BlackScholesMerton.d2(inputs);

        if (type == OptionType.CALL) {
            return annuity * (forward * NormalDistribution.cdf(d1) - strike * NormalDistribution.cdf(d2));
        }
        return annuity * (strike * NormalDistribution.cdf(-d2) - forward * NormalDistribution.cdf(-d1));
    }

    /**
     * Left-hand side of payer-receiver parity, {@code Payer - Receiver}.
     *
     * <p>Exposed for the same reason {@link
     * BlackScholesMerton#parityResidual} is: the identity {@code Payer -
     * Receiver = Annuity * (F - K)} holds by algebra alone, with no appeal to
     * the model, so a failure means an implementation bug rather than a
     * modelling disagreement.
     */
    public static double parityResidual(
            SwapValuation swap, double strike, double expiryYears, double flatVolatility, double riskFreeRate) {
        double payer = price(OptionType.CALL, swap, strike, expiryYears, flatVolatility, riskFreeRate);
        double receiver = price(OptionType.PUT, swap, strike, expiryYears, flatVolatility, riskFreeRate);
        double expected = swap.notional() * swap.annuity() * (swap.parRate() - strike);
        return (payer - receiver) - expected;
    }

    private static void requireArguments(
            OptionType type, SwapValuation swap, double strike, double expiryYears, double flatVolatility) {
        if (type == null) {
            throw new IllegalArgumentException("option type must not be null");
        }
        if (swap == null) {
            throw new IllegalArgumentException("swap must not be null");
        }
        if (!(strike > 0.0) || !Double.isFinite(strike)) {
            throw new IllegalArgumentException("strike must be positive and finite, got: " + strike);
        }
        if (!(expiryYears >= 0.0) || !Double.isFinite(expiryYears)) {
            throw new IllegalArgumentException("expiryYears must be non-negative and finite, got: " + expiryYears);
        }
        if (!(flatVolatility >= 0.0) || !Double.isFinite(flatVolatility)) {
            throw new IllegalArgumentException("flatVolatility must be non-negative and finite, got: " + flatVolatility);
        }
    }
}
