package io.github.williamhuang1261.qrp.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EqualRiskContributionOptimizerTest {

    @Test
    @DisplayName("matches the diagonal-covariance closed form: weights inversely proportional to volatility")
    void matchesClosedFormOnDiagonalCovariance() {
        // For uncorrelated assets (diagonal Sigma), RC_i = w_i * (Sigma w)_i
        // reduces to w_i^2 * Sigma_ii. Equalizing RC_i across assets gives
        // w_i / w_j = sqrt(Sigma_jj / Sigma_ii) -- i.e. weight inversely
        // proportional to volatility (equivalently: w_i * sqrt(Sigma_ii) is
        // the same constant for every asset). This is the textbook
        // "inverse-volatility" portfolio, the known closed form for equal
        // risk contribution when there is no cross-asset risk to share.
        double s1 = 0.01;
        double s2 = 0.04;
        double s3 = 0.09;
        double leverage = 1.0;

        double invVol1 = 1.0 / Math.sqrt(s1);
        double invVol2 = 1.0 / Math.sqrt(s2);
        double invVol3 = 1.0 / Math.sqrt(s3);
        double sumInvVol = invVol1 + invVol2 + invVol3;
        double expectedW1 = leverage * invVol1 / sumInvVol;
        double expectedW2 = leverage * invVol2 / sumInvVol;
        double expectedW3 = leverage * invVol3 / sumInvVol;

        EqualRiskContributionOptimizer optimizer = new EqualRiskContributionOptimizer();
        double[] expectedReturns = {0.08, 0.05, 0.10}; // unused by this optimizer, still required by the SPI
        double[][] covariance = {
            {s1, 0.0, 0.0},
            {0.0, s2, 0.0},
            {0.0, 0.0, s3}
        };
        double[] previousWeights = {0.0, 0.0, 0.0};
        PortfolioConstraints constraints =
                new PortfolioConstraints(1.0, Double.MAX_VALUE, leverage, List.of(), Map.of());

        double[] w = optimizer.optimize(expectedReturns, covariance, previousWeights, constraints);

        assertEquals(expectedW1, w[0], 1e-6, "asset 1 (lowest variance) weight vs inverse-vol closed form");
        assertEquals(expectedW2, w[1], 1e-6, "asset 2 weight vs inverse-vol closed form");
        assertEquals(expectedW3, w[2], 1e-6, "asset 3 (highest variance) weight vs inverse-vol closed form");
        assertEquals(leverage, w[0] + w[1] + w[2], 1e-9, "fully invested");
        assertTrue(w[0] > w[1] && w[1] > w[2], "lower-variance assets get more weight");
    }

    @Test
    @DisplayName("equalizes realized risk contribution across correlated assets")
    void equalizesRiskContributionOnCorrelatedCovariance() {
        // Diagonally dominant (so positive definite) covariance with genuine
        // cross terms, unlike the diagonal case above.
        double[][] covariance = {
            {0.04, 0.01, 0.015},
            {0.01, 0.09, 0.02},
            {0.015, 0.02, 0.16}
        };
        double[] expectedReturns = {0.08, 0.05, 0.10};
        double[] previousWeights = {0.0, 0.0, 0.0};
        PortfolioConstraints constraints = PortfolioConstraints.longOnly(1.0, Double.MAX_VALUE);

        EqualRiskContributionOptimizer optimizer = new EqualRiskContributionOptimizer();
        double[] w = optimizer.optimize(expectedReturns, covariance, previousWeights, constraints);

        double[] riskContribution = new double[3];
        for (int i = 0; i < 3; i++) {
            double sigmaWi = 0.0;
            for (int j = 0; j < 3; j++) {
                sigmaWi += covariance[i][j] * w[j];
            }
            riskContribution[i] = w[i] * sigmaWi;
        }

        assertEquals(riskContribution[0], riskContribution[1], 1e-6, "asset 1 vs asset 2 risk contribution");
        assertEquals(riskContribution[1], riskContribution[2], 1e-6, "asset 2 vs asset 3 risk contribution");
        assertEquals(riskContribution[0], riskContribution[2], 1e-6, "asset 1 vs asset 3 risk contribution");
        assertEquals(1.0, w[0] + w[1] + w[2], 1e-9, "fully invested");
    }

    @Test
    @DisplayName("successive rebalances never move further than the turnover cap allows")
    void respectsTightTurnoverCapAcrossRebalances() {
        // Same shape of test as MeanVarianceOptimizerTest's turnover-cap
        // test, run against this optimizer too, to confirm the SPI's
        // constraint contract is honored identically by both
        // implementations. Asset 1's much lower variance means ERC's target
        // allocation favors it (inverse-vol closed form above), pulling
        // weight away from the cold-start 50/50 split.
        EqualRiskContributionOptimizer optimizer = new EqualRiskContributionOptimizer();
        double[] expectedReturns = {0.0, 0.0}; // unused by this optimizer
        double[][] covariance = {{0.01, 0.0}, {0.0, 0.09}};
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
        // lower-variance asset, confirming the cap is binding (constraining
        // progress) rather than vacuously satisfied because the optimizer
        // never wanted to move anyway.
        assertTrue(weights[0] > 0.5, "expected asset 1's weight to grow toward its lower-variance ERC target");
    }

    @Test
    @DisplayName("rejects mismatched input lengths")
    void rejectsMismatchedLengths() {
        EqualRiskContributionOptimizer optimizer = new EqualRiskContributionOptimizer();
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
        EqualRiskContributionOptimizer optimizer = new EqualRiskContributionOptimizer();
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
    @DisplayName("rejects a non-positive variance on the covariance diagonal")
    void rejectsNonPositiveVariance() {
        EqualRiskContributionOptimizer optimizer = new EqualRiskContributionOptimizer();
        PortfolioConstraints constraints = PortfolioConstraints.longOnly(1.0, Double.MAX_VALUE);

        assertThrows(
                IllegalArgumentException.class,
                () -> optimizer.optimize(
                        new double[] {0.1, 0.2},
                        new double[][] {{0.0, 0.0}, {0.0, 0.09}},
                        new double[] {0.0, 0.0},
                        constraints));
    }

    @Test
    @DisplayName("id and displayName are stable and distinct")
    void idAndDisplayName() {
        EqualRiskContributionOptimizer optimizer = new EqualRiskContributionOptimizer();

        assertEquals("equal-risk-contribution", optimizer.id());
        assertTrue(optimizer.displayName().length() > optimizer.id().length());
    }
}
