package io.github.williamhuang1261.qrp.portfolio;

import java.util.Arrays;
import java.util.Objects;

/**
 * Equal risk contribution ("risk parity") optimizer via cyclical coordinate
 * descent.
 *
 * <p>Rather than trading expected return off against variance like {@link
 * MeanVarianceOptimizer}, this optimizer ignores {@code expectedReturns}
 * entirely and instead equalizes each instrument's contribution to total
 * portfolio variance, {@code RC_i = w_i * (Sigma w)_i}. Summed across
 * instruments, {@code RC_i} adds up to the portfolio variance {@code w^T
 * Sigma w}, so "equal risk contribution" means each instrument accounts for
 * {@code (w^T Sigma w) / n} of it.
 *
 * <p>Solved by cyclical coordinate descent (Griveau-Billiotte/Richard/Roncalli-
 * style Newton update per coordinate): holding every weight but {@code w_i}
 * fixed, {@code RC_i = target} is a quadratic in {@code w_i},
 *
 * <pre>{@code
 *   Sigma_ii * w_i^2 + b_i * w_i - target = 0,   b_i = sum_{j != i} Sigma_ij * w_j
 * }</pre>
 *
 * <p>solved by the positive root {@code w_i = (-b_i + sqrt(b_i^2 + 4 *
 * Sigma_ii * target)) / (2 * Sigma_ii)} (non-negative whenever {@code
 * Sigma_ii > 0} and {@code target >= 0}, since {@code sqrt(b_i^2 + 4 *
 * Sigma_ii * target) >= |b_i|}). Cycling through every instrument and
 * recomputing {@code target} from the current weights each cycle converges
 * to the (unique up to scale) risk-parity solution.
 *
 * <p>This raw solve ignores the box and leverage constraints — a diagonal
 * covariance matrix has a well-known closed-form solution (weights inversely
 * proportional to each instrument's volatility) that has nothing to do with
 * any {@code maxWeight} or {@code leverage} the caller happens to pass, so
 * folding those into the coordinate updates would blur the closed form this
 * optimizer is validated against. Instead, exactly like {@link
 * MeanVarianceOptimizer}, the box-and-leverage projection and the turnover
 * clip are applied to the converged raw solution via the shared helpers in
 * {@link PortfolioProjections} — same capped-simplex bisection, same L1-ball
 * turnover clip, so the SPI's constraint contract is honored identically by
 * both optimizers.
 */
public final class EqualRiskContributionOptimizer implements PortfolioOptimizer {

    private static final int MAX_ITERATIONS = 10_000;
    private static final double CONVERGENCE_TOLERANCE = 1e-14;
    private static final double MIN_VARIANCE = 1e-15;

    @Override
    public String id() {
        return "equal-risk-contribution";
    }

    @Override
    public String displayName() {
        return "Equal Risk Contribution (cyclical coordinate descent)";
    }

    @Override
    public double[] optimize(
            double[] expectedReturns,
            double[][] covariance,
            double[] previousWeights,
            PortfolioConstraints constraints) {
        validate(expectedReturns, covariance, previousWeights, constraints);
        int n = expectedReturns.length;
        double maxWeight = constraints.maxWeight();
        double leverage = constraints.leverage();
        double maxTurnover = constraints.maxTurnover();

        double[] w = solveRiskParity(covariance, n, leverage);
        w = rescaleToLeverage(w, leverage);

        w = PortfolioProjections.projectOntoCappedSimplex(w, maxWeight, leverage);
        w = PortfolioProjections.clipToTurnoverBall(w, previousWeights, maxTurnover);
        return w;
    }

    /**
     * The risk-parity solution is unique only up to an overall multiplicative
     * scale (scaling every weight by {@code c} scales every {@code RC_i} by
     * {@code c^2}, so equality among them is preserved). Unlike {@link
     * MeanVarianceOptimizer}'s box-constrained problem, restoring the
     * leverage target here must be a multiplicative rescale, not the
     * capped-simplex's additive shift-and-clamp — an additive shift would
     * change the ratios between weights and break the equal-risk-contribution
     * property before the box even has a chance to bind. This rescale runs
     * first; {@link PortfolioProjections#projectOntoCappedSimplex} then only
     * has to do real work when {@code maxWeight} actually binds, at which
     * point honoring the box exactly necessarily takes priority over the
     * unconstrained risk-parity ratios.
     */
    private static double[] rescaleToLeverage(double[] w, double leverage) {
        double sum = 0.0;
        for (double value : w) {
            sum += value;
        }
        if (sum < 1e-15) {
            return w;
        }
        double scale = leverage / sum;
        double[] scaled = new double[w.length];
        for (int i = 0; i < w.length; i++) {
            scaled[i] = w[i] * scale;
        }
        return scaled;
    }

    /**
     * Cyclical coordinate descent to the unconstrained equal-risk-contribution
     * solution, started from an equal-weight vector summing to {@code
     * leverage} (a scale-neutral starting point; the risk-parity solution is
     * unique only up to an overall scale, and that scale is restored by the
     * capped-simplex projection afterward, not by this solve).
     */
    private static double[] solveRiskParity(double[][] covariance, int n, double leverage) {
        double[] w = new double[n];
        Arrays.fill(w, leverage / n);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[] previous = w.clone();
            double target = (weightedQuadraticForm(w, covariance)) / n;

            for (int i = 0; i < n; i++) {
                double a = covariance[i][i];
                double b = 0.0;
                for (int j = 0; j < n; j++) {
                    if (j != i) {
                        b += covariance[i][j] * w[j];
                    }
                }
                double discriminant = Math.max(0.0, b * b + 4.0 * a * target);
                w[i] = Math.max(0.0, (-b + Math.sqrt(discriminant)) / (2.0 * a));
            }

            double maxDelta = 0.0;
            for (int i = 0; i < n; i++) {
                maxDelta = Math.max(maxDelta, Math.abs(w[i] - previous[i]));
            }
            if (maxDelta < CONVERGENCE_TOLERANCE) {
                break;
            }
        }
        return w;
    }

    /** {@code w^T Sigma w}, the total portfolio variance at the current weights. */
    private static double weightedQuadraticForm(double[] w, double[][] covariance) {
        int n = w.length;
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            double sigmaWi = 0.0;
            for (int j = 0; j < n; j++) {
                sigmaWi += covariance[i][j] * w[j];
            }
            total += w[i] * sigmaWi;
        }
        return total;
    }

    private static void validate(
            double[] expectedReturns,
            double[][] covariance,
            double[] previousWeights,
            PortfolioConstraints constraints) {
        Objects.requireNonNull(expectedReturns, "expectedReturns");
        Objects.requireNonNull(covariance, "covariance");
        Objects.requireNonNull(previousWeights, "previousWeights");
        Objects.requireNonNull(constraints, "constraints");

        int n = expectedReturns.length;
        if (n == 0) {
            throw new IllegalArgumentException("need at least one instrument, got 0");
        }
        if (covariance.length != n) {
            throw new IllegalArgumentException(
                    "covariance must have " + n + " rows to match expectedReturns, got: " + covariance.length);
        }
        for (int i = 0; i < n; i++) {
            Objects.requireNonNull(covariance[i], "covariance[" + i + "]");
            if (covariance[i].length != n) {
                throw new IllegalArgumentException(
                        "covariance must be " + n + "x" + n + " (square); row " + i
                                + " has length " + covariance[i].length);
            }
            if (covariance[i][i] < MIN_VARIANCE) {
                throw new IllegalArgumentException(
                        "covariance diagonal (variance) must be positive; instrument " + i
                                + " has variance " + covariance[i][i]);
            }
        }
        if (previousWeights.length != n) {
            throw new IllegalArgumentException(
                    "previousWeights must have " + n + " entries to match expectedReturns, got: "
                            + previousWeights.length);
        }
        if ((double) n * constraints.maxWeight() < constraints.leverage() - 1e-9) {
            throw new IllegalArgumentException(
                    "infeasible constraints: " + n + " instruments at maxWeight "
                            + constraints.maxWeight() + " cannot reach leverage " + constraints.leverage());
        }
    }
}
