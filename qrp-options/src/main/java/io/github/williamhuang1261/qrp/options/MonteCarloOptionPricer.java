package io.github.williamhuang1261.qrp.options;

import io.github.williamhuang1261.qrp.stats.NormalQuantile;
import io.github.williamhuang1261.qrp.stats.SplitMix64;

/**
 * European option pricing by simulating the terminal price under the
 * risk-neutral measure and discounting the expected payoff.
 *
 * <p>Uses the platform's specified generator, {@link SplitMix64}, and its
 * per-draw seeding convention: draw {@code i} is seeded from
 * {@code (seed, i)} alone, so a result reproduces exactly regardless of how the
 * loop is scheduled. This is the same discipline {@code qrp-stats} already
 * applies to the bootstrap, extended to a new consumer of the same generator.
 *
 * <p><b>Antithetic variates.</b> Each pair of draws uses a uniform {@code u}
 * and its mirror {@code 1 - u}, converted to standard normal draws
 * {@code z} and {@code -z} through {@link NormalQuantile}. Averaging a payoff
 * with its antithetic partner cancels the first-order sampling error from that
 * pair's draw of the underlying, which is why the estimator's standard error
 * empirically improves over independent sampling at the same path count, even
 * though this is not proven in general for a nonlinear payoff.
 *
 * <p>This pricer exists to be checked against, not to replace, the closed form:
 * {@link BlackScholesMerton} is exact and instantaneous, and a correct Monte
 * Carlo estimator has to converge to it. That agreement is the test.
 */
public final class MonteCarloOptionPricer {

    /**
     * @param price          the discounted average payoff
     * @param standardError  standard error of {@code price} across the paths
     * @param confidenceLow  lower bound of the 95% confidence interval
     * @param confidenceHigh upper bound of the 95% confidence interval
     */
    public record Result(double price, double standardError, double confidenceLow, double confidenceHigh) {
    }

    private static final double Z_95 = 1.959963984540054;

    private MonteCarloOptionPricer() {
    }

    /**
     * @param pathPairs number of antithetic pairs simulated; total draws are
     *                  {@code 2 * pathPairs}
     * @param seed      run seed; the same seed reproduces the same result
     */
    public static Result price(OptionType type, BlackScholesInputs in, int pathPairs, long seed) {
        if (type == null) {
            throw new IllegalArgumentException("option type must not be null");
        }
        if (in == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        if (pathPairs < 1) {
            throw new IllegalArgumentException("pathPairs must be at least 1, got: " + pathPairs);
        }

        if (in.isDeterministic()) {
            double value = in.discountFactor() * type.payoff(in.forward(), in.strike());
            return new Result(value, 0.0, value, value);
        }

        double drift = (in.carryRate() - 0.5 * in.volatility() * in.volatility()) * in.timeToExpiryYears();
        double diffusion = in.totalVolatility();
        double discount = in.discountFactor();

        // Averaged payoffs, one per antithetic pair; the mean and standard error
        // of THIS array are what the confidence interval is built from, not the
        // 2*pathPairs raw draws, since the two legs of a pair are correlated by
        // construction and would understate the true sampling error if pooled.
        double[] pairedPayoffs = new double[pathPairs];

        for (int pair = 0; pair < pathPairs; pair++) {
            SplitMix64 rng = SplitMix64.forDraw(seed, pair);
            double u = rng.nextDouble();
            // nextDouble draws from [0, 1); clamp away from the exact endpoints so
            // the quantile function never sees 0 or 1 in the tail of a long run.
            u = Math.min(Math.max(u, 1e-12), 1.0 - 1e-12);
            double z = NormalQuantile.inverseCdf(u);

            double terminalUp = in.spot() * Math.exp(drift + diffusion * z);
            double terminalDown = in.spot() * Math.exp(drift - diffusion * z);

            double payoffUp = type.payoff(terminalUp, in.strike());
            double payoffDown = type.payoff(terminalDown, in.strike());
            pairedPayoffs[pair] = 0.5 * (payoffUp + payoffDown);
        }

        double mean = mean(pairedPayoffs);
        double variance = 0.0;
        for (double value : pairedPayoffs) {
            double deviation = value - mean;
            variance += deviation * deviation;
        }
        variance /= (pathPairs - 1 > 0 ? pathPairs - 1 : 1);

        double discountedMean = discount * mean;
        double standardError = discount * Math.sqrt(variance / pathPairs);

        return new Result(
                discountedMean,
                standardError,
                discountedMean - Z_95 * standardError,
                discountedMean + Z_95 * standardError);
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
