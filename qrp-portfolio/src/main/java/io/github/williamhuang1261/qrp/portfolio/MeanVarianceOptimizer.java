package io.github.williamhuang1261.qrp.portfolio;

import java.util.Objects;

/**
 * Constrained mean-variance optimizer via projected gradient descent.
 *
 * <p>Minimizes {@code -w^T mu + riskAversion * w^T Sigma w} — expected return
 * traded off against variance, scaled by a caller-supplied risk-aversion
 * coefficient ({@code lambda} in the plan's notation) — subject to:
 *
 * <ul>
 *   <li>a per-instrument box {@code [0, maxWeight]} — <b>long-only</b>. The
 *       constraint set this optimizer is given ({@link PortfolioConstraints})
 *       has only a max weight and a leverage target, no explicit short-selling
 *       allowance, so a lower bound of zero is the stated scope of this
 *       implementation, not a silent assumption. See {@code docs/spec-portfolio.md}
 *       design decision D4.
 *   <li>the leverage target, i.e. {@code sum(w) == constraints.leverage()};
 *   <li>the turnover cap, i.e. {@code sum(|w - previousWeights|) <= constraints.maxTurnover()}.
 * </ul>
 *
 * <p>The box-and-leverage constraints are enforced jointly at every iteration
 * by projecting onto the "capped simplex" {@code {w : 0 <= w_i <= maxWeight,
 * sum(w) == leverage}} via bisection on a single shared threshold — clipping
 * to the box and then independently rescaling to the leverage target (the
 * literal two-step reading of the plan) can push a weight back above
 * {@code maxWeight} after the rescale, which would violate the "respects it
 * exactly" requirement. The turnover cap is then applied as a separate
 * projection onto the L1 ball of radius {@code maxTurnover} around
 * {@code previousWeights}, which stays inside the box-and-leverage set because
 * that set is convex and {@code previousWeights} is assumed to already lie in
 * it. See {@code docs/spec-portfolio.md} design decision D5.
 *
 * <p>Both projections live in {@link PortfolioProjections}, shared with
 * {@link EqualRiskContributionOptimizer} so the SPI's box/leverage/turnover
 * contract is honored identically by every implementation.
 */
public final class MeanVarianceOptimizer implements PortfolioOptimizer {

    private static final int MAX_ITERATIONS = 20_000;
    private static final double CONVERGENCE_TOLERANCE = 1e-14;

    private final double riskAversion;

    /**
     * @param riskAversion the {@code lambda} weight on variance in the
     *                     objective {@code -w^T mu + lambda * w^T Sigma w};
     *                     must be positive. Larger values favor lower
     *                     variance over higher expected return.
     */
    public MeanVarianceOptimizer(double riskAversion) {
        if (!(riskAversion > 0.0)) {
            throw new IllegalArgumentException("riskAversion must be positive, got: " + riskAversion);
        }
        this.riskAversion = riskAversion;
    }

    @Override
    public String id() {
        return "mean-variance";
    }

    @Override
    public String displayName() {
        return "Mean-Variance (projected gradient descent)";
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

        double stepSize = gradientStepSize(covariance);

        double[] w = PortfolioProjections.projectOntoCappedSimplex(previousWeights, maxWeight, leverage);
        w = PortfolioProjections.clipToTurnoverBall(w, previousWeights, maxTurnover);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[] gradient = gradient(w, expectedReturns, covariance);

            double[] candidate = new double[n];
            for (int i = 0; i < n; i++) {
                candidate[i] = w[i] - stepSize * gradient[i];
            }

            candidate = PortfolioProjections.projectOntoCappedSimplex(candidate, maxWeight, leverage);
            candidate = PortfolioProjections.clipToTurnoverBall(candidate, previousWeights, maxTurnover);

            double maxDelta = 0.0;
            for (int i = 0; i < n; i++) {
                maxDelta = Math.max(maxDelta, Math.abs(candidate[i] - w[i]));
            }
            w = candidate;
            if (maxDelta < CONVERGENCE_TOLERANCE) {
                break;
            }
        }
        return w;
    }

    /** {@code grad_i = -mu_i + 2 * lambda * (Sigma w)_i}. */
    private double[] gradient(double[] w, double[] expectedReturns, double[][] covariance) {
        int n = w.length;
        double[] gradient = new double[n];
        for (int i = 0; i < n; i++) {
            double sigmaWi = 0.0;
            for (int j = 0; j < n; j++) {
                sigmaWi += covariance[i][j] * w[j];
            }
            gradient[i] = -expectedReturns[i] + 2.0 * riskAversion * sigmaWi;
        }
        return gradient;
    }

    /**
     * A safe (convergent) step size for gradient descent on a quadratic with
     * gradient Lipschitz constant {@code 2 * lambda * ||Sigma||_op}. The
     * operator norm of a symmetric matrix is bounded by its largest absolute
     * row sum (Gershgorin), which is cheap to compute and avoids depending on
     * an eigenvalue routine.
     */
    private double gradientStepSize(double[][] covariance) {
        double maxRowAbsSum = 0.0;
        for (double[] row : covariance) {
            double rowAbsSum = 0.0;
            for (double value : row) {
                rowAbsSum += Math.abs(value);
            }
            maxRowAbsSum = Math.max(maxRowAbsSum, rowAbsSum);
        }
        double lipschitz = 2.0 * riskAversion * maxRowAbsSum;
        if (lipschitz < 1e-12) {
            lipschitz = 1e-12;
        }
        return 1.0 / lipschitz;
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
