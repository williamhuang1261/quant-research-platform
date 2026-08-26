package io.github.williamhuang1261.qrp.options;

/**
 * Cox-Ross-Rubinstein binomial lattice, European and American.
 *
 * <p>This is the only pricer in the module that handles American exercise:
 * {@link BlackScholesMerton} rejects it outright rather than approximate it,
 * because a closed form has no early-exercise boundary to check against.
 *
 * <p>The up and down factors are the original 1979 choice,
 * {@code u = e^{sigma sqrt(dt)}}, {@code d = 1/u}, which is what makes the tree
 * recombine: an up move followed by a down move lands on the same node as a
 * down then an up, so the tree has {@code steps + 1} nodes at maturity instead
 * of {@code 2^steps}. The risk-neutral probability comes from matching the
 * tree's one-step expected return to the continuous model's forward growth,
 * {@code e^{(r-q)dt}}, so the two models price the same contract consistently
 * as steps grows without bound.
 *
 * <p>Walked backward from the leaves rather than forward from the root, because
 * European and American differ only in one line at each node — American takes
 * the larger of the continuation value and immediate exercise, European never
 * exercises early — and a backward walk is where that line lives.
 */
public final class CoxRossRubinstein {

    private CoxRossRubinstein() {
    }

    /**
     * Fair value of the contract, per unit of underlying.
     *
     * @param steps number of time steps in the lattice; error decays roughly as
     *              {@code 1/steps} against the Black-Scholes limit
     */
    public static double price(OptionType type, ExerciseStyle style, BlackScholesInputs in, int steps) {
        requireArguments(type, style, in, steps);

        if (in.isDeterministic()) {
            return in.discountFactor() * type.payoff(in.forward(), in.strike());
        }

        double dt = in.timeToExpiryYears() / steps;
        double up = Math.exp(in.volatility() * Math.sqrt(dt));
        double down = 1.0 / up;
        double growth = Math.exp(in.carryRate() * dt);
        double riskNeutralUp = (growth - down) / (up - down);
        if (riskNeutralUp <= 0.0 || riskNeutralUp >= 1.0) {
            throw new IllegalStateException(
                    "risk-neutral probability left (0, 1): " + riskNeutralUp
                            + " -- steps=" + steps + " is too coarse for this volatility and time step");
        }
        double riskNeutralDown = 1.0 - riskNeutralUp;
        double discountPerStep = Math.exp(-in.riskFreeRate() * dt);

        // Terminal payoffs across the steps+1 nodes at maturity, cheapest node
        // (all downs) first.
        double[] values = new double[steps + 1];
        for (int node = 0; node <= steps; node++) {
            double terminalPrice = in.spot() * Math.pow(up, node) * Math.pow(down, steps - node);
            values[node] = type.payoff(terminalPrice, in.strike());
        }

        for (int level = steps - 1; level >= 0; level--) {
            for (int node = 0; node <= level; node++) {
                double continuation =
                        discountPerStep * (riskNeutralUp * values[node + 1] + riskNeutralDown * values[node]);
                if (style == ExerciseStyle.AMERICAN) {
                    double spotHere = in.spot() * Math.pow(up, node) * Math.pow(down, level - node);
                    values[node] = Math.max(continuation, type.payoff(spotHere, in.strike()));
                } else {
                    values[node] = continuation;
                }
            }
        }
        return values[0];
    }

    /** Convenience overload pricing a contract's own type and exercise style. */
    public static double priceContract(OptionContract contract, BlackScholesInputs in, int steps) {
        return price(contract.type(), contract.style(), in, steps);
    }

    private static void requireArguments(
            OptionType type, ExerciseStyle style, BlackScholesInputs in, int steps) {
        if (type == null) {
            throw new IllegalArgumentException("option type must not be null");
        }
        if (style == null) {
            throw new IllegalArgumentException("exercise style must not be null");
        }
        if (in == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        if (steps < 1) {
            throw new IllegalArgumentException("steps must be at least 1, got: " + steps);
        }
    }
}
