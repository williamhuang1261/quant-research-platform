package io.github.williamhuang1261.qrp.options;

/**
 * Recovers the volatility that reprices a market quote under Black-Scholes-Merton.
 *
 * <p>Newton-Raphson on vega converges quadratically near the money, in a
 * handful of steps. It stalls in the wings, where vega is tiny and a step can
 * overshoot into a volatility with a smaller vega still, sometimes diverging
 * outright. A bracketed bisection is the fallback for exactly that region: it
 * halves an interval known to contain the root every step, so it cannot
 * diverge, at the cost of linear rather than quadratic convergence. Newton is
 * tried first because it is fast where it works; bisection is what makes the
 * solver <em>always</em> return an answer or fail loudly, rather than silently
 * returning whatever Newton's last iterate happened to be.
 *
 * <p>The bracket is {@code [MIN_VOLATILITY, MAX_VOLATILITY]}. A price outside
 * the interval's no-arbitrage bounds at both ends has no implied volatility at
 * all -- that is a data problem, not a solver problem -- and is reported as
 * such rather than returning the nearest boundary.
 */
public final class ImpliedVolatility {

    /** Below this, Black-Scholes is numerically indistinguishable from zero volatility. */
    private static final double MIN_VOLATILITY = 1e-6;

    /** 500% annualized: far past anything a listed market quotes, chosen as a hard ceiling. */
    private static final double MAX_VOLATILITY = 5.0;

    private static final int MAX_NEWTON_ITERATIONS = 50;
    private static final int MAX_BISECTION_ITERATIONS = 100;
    private static final double PRICE_TOLERANCE = 1e-10;

    private ImpliedVolatility() {
    }

    /**
     * @param marketPrice the observed price to match
     * @throws IllegalArgumentException if the price is outside the model's
     *         no-arbitrage bounds, or the search brackets do not converge
     */
    public static double solve(OptionType type, BlackScholesInputs in, double marketPrice) {
        if (type == null) {
            throw new IllegalArgumentException("option type must not be null");
        }
        if (in == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        if (!(marketPrice >= 0.0) || !Double.isFinite(marketPrice)) {
            throw new IllegalArgumentException(
                    "marketPrice must be non-negative and finite, got: " + marketPrice);
        }

        double priceAtMin = BlackScholesMerton.price(type, in.withVolatility(MIN_VOLATILITY));
        double priceAtMax = BlackScholesMerton.price(type, in.withVolatility(MAX_VOLATILITY));
        if (marketPrice < priceAtMin - PRICE_TOLERANCE || marketPrice > priceAtMax + PRICE_TOLERANCE) {
            throw new IllegalArgumentException(
                    "marketPrice " + marketPrice + " is outside the no-arbitrage range ["
                            + priceAtMin + ", " + priceAtMax + "] for volatility in ["
                            + MIN_VOLATILITY + ", " + MAX_VOLATILITY + "]; this is a data problem,"
                            + " not something a wider bracket would fix");
        }

        Double newtonResult = tryNewton(type, in, marketPrice);
        if (newtonResult != null) {
            return newtonResult;
        }
        return bisect(type, in, marketPrice);
    }

    /** @return the converged volatility, or {@code null} if Newton did not converge within its budget */
    private static Double tryNewton(OptionType type, BlackScholesInputs in, double marketPrice) {
        // Start from a mid-range guess rather than at-the-money-ish 0.2, since a
        // solver used across a whole chain has no reason to expect that level.
        double volatility = 0.3;

        for (int iteration = 0; iteration < MAX_NEWTON_ITERATIONS; iteration++) {
            if (volatility <= MIN_VOLATILITY || volatility >= MAX_VOLATILITY) {
                return null;
            }
            BlackScholesInputs candidate = in.withVolatility(volatility);
            double price = BlackScholesMerton.price(type, candidate);
            double error = price - marketPrice;
            if (Math.abs(error) < PRICE_TOLERANCE) {
                return volatility;
            }
            double vega = BlackScholesMerton.greeks(type, candidate).vega();
            if (vega < 1e-8) {
                // Vega has collapsed: the step below would be huge and unreliable.
                // Hand off to bisection rather than take it.
                return null;
            }
            volatility -= error / vega;
        }
        return null;
    }

    private static double bisect(OptionType type, BlackScholesInputs in, double marketPrice) {
        double low = MIN_VOLATILITY;
        double high = MAX_VOLATILITY;
        // priceAtLow <= marketPrice <= priceAtHigh was already established by the
        // no-arbitrage check in solve(), so the bracket is guaranteed to contain
        // a root: price is monotone increasing in volatility (vega > 0 throughout
        // the open interval), so the sign of (price - marketPrice) flips exactly
        // once across [low, high].

        for (int iteration = 0; iteration < MAX_BISECTION_ITERATIONS; iteration++) {
            double mid = 0.5 * (low + high);
            double priceAtMid = BlackScholesMerton.price(type, in.withVolatility(mid));
            double error = priceAtMid - marketPrice;

            if (Math.abs(error) < PRICE_TOLERANCE || (high - low) < 1e-14) {
                return mid;
            }
            if (error > 0.0) {
                high = mid;
            } else {
                low = mid;
            }
        }
        throw new IllegalStateException(
                "bisection did not converge within " + MAX_BISECTION_ITERATIONS
                        + " iterations for marketPrice=" + marketPrice
                        + "; this should not happen given the interval check in solve()");
    }
}
