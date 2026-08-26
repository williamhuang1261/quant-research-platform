package io.github.williamhuang1261.qrp.options;

import io.github.williamhuang1261.qrp.stats.NormalDistribution;

/**
 * Closed-form European option pricing under the generalized Black-Scholes-Merton
 * model, with all five Greeks analytic.
 *
 * <p>Generalized in the cost-of-carry sense: the carry {@code b = r - q} comes
 * from {@link BlackScholesInputs}, so this one class prices a non-dividend
 * equity, an index with a yield, an option on a future and an FX option without
 * a branch per case. See that record for the table.
 *
 * <p><b>The degenerate cases are priced, not rejected.</b> At {@code T = 0} or
 * {@code sigma = 0} there is no diffusion left and the contract is worth its
 * discounted intrinsic value <em>on the forward</em>, not on spot: a call struck
 * below a forward that carries above it is worth something even with no
 * volatility. Handling this in the pricer rather than at every call site is what
 * lets the implied-volatility solver bracket from zero, and what lets the
 * binomial tree be compared against a closed form at its own boundary.
 *
 * <p>The two branches are not separate formulas. In the degenerate case
 * {@code N(d1)} and {@code N(d2)} are replaced by the indicator that the forward
 * finishes in the money and the density by zero; every expression below is then
 * the same expression. That is deliberate — two independently written formulas
 * would eventually disagree at the boundary, and the boundary is exactly where
 * a solver spends its time.
 *
 * <p>American exercise is <b>not</b> handled here and is rejected rather than
 * silently priced as European. {@code CoxRossRubinstein} is the lattice that
 * covers it.
 */
public final class BlackScholesMerton {

    private BlackScholesMerton() {
    }

    /** Fair value of a European option, per unit of underlying. */
    public static double price(OptionType type, BlackScholesInputs in) {
        requireArguments(type, in);

        double discount = in.discountFactor();
        double carry = in.carryFactor();

        if (in.isDeterministic()) {
            return discount * type.payoff(in.forward(), in.strike());
        }

        double d1 = d1(in);
        double d2 = d1 - in.totalVolatility();

        if (type == OptionType.CALL) {
            return in.spot() * carry * NormalDistribution.cdf(d1)
                    - in.strike() * discount * NormalDistribution.cdf(d2);
        }
        return in.strike() * discount * NormalDistribution.cdf(-d2)
                - in.spot() * carry * NormalDistribution.cdf(-d1);
    }

    /** All five sensitivities, analytic, in the units documented on {@link Greeks}. */
    public static Greeks greeks(OptionType type, BlackScholesInputs in) {
        requireArguments(type, in);

        double spot = in.spot();
        double strike = in.strike();
        double years = in.timeToExpiryYears();
        double vol = in.volatility();
        double discount = in.discountFactor();
        double carry = in.carryFactor();

        // In the degenerate case the normal terms collapse to an indicator on the
        // forward and the density to zero, so the expressions below stay shared.
        double nd1;
        double nd2;
        double densityD1;
        if (in.isDeterministic()) {
            double inTheMoneyForACall = in.forward() > strike ? 1.0 : 0.0;
            nd1 = inTheMoneyForACall;
            nd2 = inTheMoneyForACall;
            densityD1 = 0.0;
        } else {
            double d1 = d1(in);
            double d2 = d1 - in.totalVolatility();
            nd1 = NormalDistribution.cdf(d1);
            nd2 = NormalDistribution.cdf(d2);
            densityD1 = NormalDistribution.pdf(d1);
        }

        double sqrtYears = Math.sqrt(years);
        double spotLeg = spot * carry;
        double strikeLeg = strike * discount;

        double delta = type == OptionType.CALL ? carry * nd1 : carry * (nd1 - 1.0);

        // gamma and vega share the density, so both vanish with it.
        double gamma = in.isDeterministic() ? 0.0 : carry * densityD1 / (spot * vol * sqrtYears);
        double vega = in.isDeterministic() ? 0.0 : spotLeg * densityD1 * sqrtYears;

        // The diffusion part of theta is the only term that needs 1/sqrt(T).
        double diffusionDecay =
                in.isDeterministic() ? 0.0 : -spotLeg * densityD1 * vol / (2.0 * sqrtYears);

        double theta;
        double rho;
        if (type == OptionType.CALL) {
            theta = diffusionDecay
                    + in.dividendYield() * spotLeg * nd1
                    - in.riskFreeRate() * strikeLeg * nd2;
            rho = years * strikeLeg * nd2;
        } else {
            theta = diffusionDecay
                    - in.dividendYield() * spotLeg * (1.0 - nd1)
                    + in.riskFreeRate() * strikeLeg * (1.0 - nd2);
            rho = -years * strikeLeg * (1.0 - nd2);
        }

        return new Greeks(delta, gamma, vega, theta, rho);
    }

    /**
     * Left-hand side of put-call parity, {@code C - P}.
     *
     * <p>Exposed because it is the cheapest self-check the module has: the
     * identity {@code C - P = S e^{-qT} - K e^{-rT}} holds by algebra, with no
     * appeal to the model, so a failure means an implementation bug rather than
     * a modelling disagreement.
     */
    public static double parityResidual(BlackScholesInputs in) {
        requireInputs(in);
        double callMinusPut = price(OptionType.CALL, in) - price(OptionType.PUT, in);
        double forwardValue = in.spot() * in.carryFactor() - in.strike() * in.discountFactor();
        return callMinusPut - forwardValue;
    }

    /**
     * {@code d1}, the standardized log-moneyness of the forward.
     *
     * @throws IllegalArgumentException if there is no diffusion, where it diverges
     */
    public static double d1(BlackScholesInputs in) {
        requireInputs(in);
        if (in.isDeterministic()) {
            throw new IllegalArgumentException(
                    "d1 is undefined with zero volatility or zero time to expiry; "
                            + "price() handles that case directly");
        }
        double totalVol = in.totalVolatility();
        return (Math.log(in.spot() / in.strike())
                + (in.carryRate() + 0.5 * in.volatility() * in.volatility()) * in.timeToExpiryYears())
                / totalVol;
    }

    /** {@code d2 = d1 - sigma sqrt(T)}: the risk-neutral probability of exercise sits at {@code N(d2)}. */
    public static double d2(BlackScholesInputs in) {
        return d1(in) - in.totalVolatility();
    }

    private static void requireArguments(OptionType type, BlackScholesInputs in) {
        if (type == null) {
            throw new IllegalArgumentException("option type must not be null");
        }
        requireInputs(in);
    }

    private static void requireInputs(BlackScholesInputs in) {
        if (in == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
    }

    /**
     * Guard for callers holding a contract rather than loose numbers.
     *
     * @throws IllegalArgumentException on an American contract, which this model
     *         does not cover; pricing it as European would understate a put
     */
    public static double priceContract(
            OptionContract contract, BlackScholesInputs in) {
        if (contract.style() == ExerciseStyle.AMERICAN) {
            throw new IllegalArgumentException(
                    "Black-Scholes-Merton prices European exercise only; "
                            + "use a lattice for " + contract);
        }
        return price(contract.type(), in);
    }
}
