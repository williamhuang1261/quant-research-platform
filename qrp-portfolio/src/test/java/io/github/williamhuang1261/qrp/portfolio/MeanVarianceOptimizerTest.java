package io.github.williamhuang1261.qrp.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeanVarianceOptimizerTest {

    @Test
    @DisplayName("matches the closed-form two-asset solution when no constraint binds")
    void matchesClosedFormWhenUnconstrained() {
        // Two uncorrelated assets: mu = (0.10, 0.05), variances 0.04 and 0.09,
        // risk aversion lambda = 5, fully invested (leverage 1). Loose box
        // (maxWeight 1.0) and loose turnover so nothing but the budget
        // constraint binds — this is the textbook Lagrangian case:
        //   w = Sigma^-1 mu / (2 lambda) + k * Sigma^-1 * 1
        //   k = (leverage - 1^T Sigma^-1 mu / (2 lambda)) / (1^T Sigma^-1 1)
        double mu1 = 0.10;
        double mu2 = 0.05;
        double s1 = 0.04;
        double s2 = 0.09;
        double lambda = 5.0;
        double leverage = 1.0;

        double sigmaInvMu1 = mu1 / s1;
        double sigmaInvMu2 = mu2 / s2;
        double sigmaInv1sum = 1.0 / s1 + 1.0 / s2;
        double unconstrainedSum = (sigmaInvMu1 + sigmaInvMu2) / (2.0 * lambda);
        double k = (leverage - unconstrainedSum) / sigmaInv1sum;

        double expectedW1 = sigmaInvMu1 / (2.0 * lambda) + k * (1.0 / s1);
        double expectedW2 = sigmaInvMu2 / (2.0 * lambda) + k * (1.0 / s2);

        // Independent sanity check on the hand-derived closed form itself.
        assertEquals(1.0, expectedW1 + expectedW2, 1e-9);

        MeanVarianceOptimizer optimizer = new MeanVarianceOptimizer(lambda);
        double[] expectedReturns = {mu1, mu2};
        double[][] covariance = {{s1, 0.0}, {0.0, s2}};
        double[] previousWeights = {0.0, 0.0};
        PortfolioConstraints constraints =
                new PortfolioConstraints(1.0, Double.MAX_VALUE, leverage, List.of(), Map.of());

        double[] w = optimizer.optimize(expectedReturns, covariance, previousWeights, constraints);

        assertEquals(expectedW1, w[0], 1e-4, "asset 1 weight vs closed form");
        assertEquals(expectedW2, w[1], 1e-4, "asset 2 weight vs closed form");
        assertEquals(leverage, w[0] + w[1], 1e-9, "fully invested");
    }

    @Test
    @DisplayName("respects a binding max weight exactly")
    void respectsBindingMaxWeight() {
        // Asset 1's expected return dwarfs the others' at low risk aversion,
        // so the unconstrained optimum would push far past any reasonable
        // cap. maxWeight = 0.4 with 3 assets (0.4 * 3 = 1.2 >= leverage 1.0)
        // is feasible but binds hard on asset 1; assets 2 and 3 are
        // identical so symmetry splits the remainder evenly between them.
        MeanVarianceOptimizer optimizer = new MeanVarianceOptimizer(0.1);
        double[] expectedReturns = {5.0, 0.01, 0.01};
        double[][] covariance = {
            {0.04, 0.0, 0.0},
            {0.0, 0.04, 0.0},
            {0.0, 0.0, 0.04}
        };
        double[] previousWeights = {0.0, 0.0, 0.0};
        double maxWeight = 0.4;
        PortfolioConstraints constraints =
                new PortfolioConstraints(maxWeight, Double.MAX_VALUE, 1.0, List.of(), Map.of());

        double[] w = optimizer.optimize(expectedReturns, covariance, previousWeights, constraints);

        for (double weight : w) {
            assertTrue(weight <= maxWeight + 1e-9, "weight " + weight + " exceeds maxWeight " + maxWeight);
        }
        assertEquals(maxWeight, w[0], 1e-6, "asset 1 pinned exactly at the cap");
        assertEquals(0.3, w[1], 1e-4, "remainder split evenly (symmetric assets)");
        assertEquals(0.3, w[2], 1e-4, "remainder split evenly (symmetric assets)");
        assertEquals(1.0, w[0] + w[1] + w[2], 1e-9, "leverage target still met exactly");
    }

    @Test
    @DisplayName("successive rebalances never move further than the turnover cap allows")
    void respectsTightTurnoverCapAcrossRebalances() {
        MeanVarianceOptimizer optimizer = new MeanVarianceOptimizer(5.0);
        double[] expectedReturns = {0.20, 0.05};
        double[][] covariance = {{0.04, 0.0}, {0.0, 0.09}};
        double maxTurnover = 0.05;
        PortfolioConstraints constraints =
                new PortfolioConstraints(1.0, maxTurnover, 1.0, List.of(), Map.of());

        double[] weights = {0.5, 0.5};
        for (int rebalance = 0; rebalance < 6; rebalance++) {
            double[] next = optimizer.optimize(expectedReturns, covariance, weights, constraints);

            double totalAbsDelta = Math.abs(next[0] - weights[0]) + Math.abs(next[1] - weights[1]);
            assertTrue(
                    totalAbsDelta <= maxTurnover + 1e-9,
                    "rebalance " + rebalance + " moved " + totalAbsDelta + " > cap " + maxTurnover);

            weights = next;
        }

        // Over enough rebalances under a binding cap, the walk should have
        // made real progress away from the cold-start 50/50 split toward the
        // higher-expected-return asset, confirming the cap is binding
        // (constraining progress) rather than vacuously satisfied because
        // the optimizer never wanted to move anyway.
        assertTrue(weights[0] > 0.5, "expected asset 1's weight to grow toward its higher expected return");
    }

    @Test
    @DisplayName("rejects a non-positive risk aversion")
    void rejectsNonPositiveRiskAversion() {
        assertThrows(IllegalArgumentException.class, () -> new MeanVarianceOptimizer(0.0));
        assertThrows(IllegalArgumentException.class, () -> new MeanVarianceOptimizer(-1.0));
    }

    @Test
    @DisplayName("rejects mismatched input lengths")
    void rejectsMismatchedLengths() {
        MeanVarianceOptimizer optimizer = new MeanVarianceOptimizer(1.0);
        PortfolioConstraints constraints = PortfolioConstraints.longOnly(1.0, Double.MAX_VALUE);

        assertThrows(
                IllegalArgumentException.class,
                () -> optimizer.optimize(
                        new double[] {0.1, 0.2},
                        new double[][] {{0.04, 0.0}, {0.0, 0.09}},
                        new double[] {0.0},
                        constraints));
        assertThrows(
                IllegalArgumentException.class,
                () -> optimizer.optimize(
                        new double[] {0.1, 0.2},
                        new double[][] {{0.04, 0.0, 0.0}, {0.0, 0.09, 0.0}},
                        new double[] {0.0, 0.0},
                        constraints));
    }

    @Test
    @DisplayName("rejects an infeasible box (n * maxWeight below leverage)")
    void rejectsInfeasibleBox() {
        MeanVarianceOptimizer optimizer = new MeanVarianceOptimizer(1.0);
        PortfolioConstraints constraints =
                new PortfolioConstraints(0.2, Double.MAX_VALUE, 1.0, List.of(), Map.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> optimizer.optimize(
                        new double[] {0.1, 0.2, 0.1},
                        new double[][] {
                            {0.04, 0.0, 0.0},
                            {0.0, 0.04, 0.0},
                            {0.0, 0.0, 0.04}
                        },
                        new double[] {0.0, 0.0, 0.0},
                        constraints));
    }

    @Test
    @DisplayName("id and displayName are stable and distinct")
    void idAndDisplayName() {
        MeanVarianceOptimizer optimizer = new MeanVarianceOptimizer(1.0);

        assertEquals("mean-variance", optimizer.id());
        assertTrue(optimizer.displayName().length() > optimizer.id().length());
    }
}
