package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiskMetricsTest {

    /** -0.05 .. 0.04 in one-point steps: 100 observations, a known tail. */
    private static double[] rampReturns() {
        double[] returns = new double[100];
        for (int i = 0; i < returns.length; i++) {
            returns[i] = (i - 50) / 1_000.0;
        }
        return returns;
    }

    @Test
    @DisplayName("VaR is reported as a positive loss")
    void varIsAPositiveLoss() {
        double var95 = RiskMetrics.valueAtRisk(rampReturns(), 0.95);

        assertTrue(var95 > 0.0, "expected a positive loss, got " + var95);
        // Type-7 position 0.05 * 99 = 4.95, i.e. 95 % of the way from -0.046 to -0.045.
        assertEquals(0.04505, var95, 1e-9);
    }

    @Test
    @DisplayName("expected shortfall is at least as bad as VaR")
    void shortfallIsWorseThanVar() {
        double[] returns = rampReturns();

        double var95 = RiskMetrics.valueAtRisk(returns, 0.95);
        double es95 = RiskMetrics.expectedShortfall(returns, 0.95);

        assertTrue(es95 >= var95, "ES " + es95 + " should be at least VaR " + var95);
    }

    @Test
    @DisplayName("a higher confidence level looks further into the tail")
    void higherLevelIsMoreConservative() {
        double[] returns = rampReturns();

        assertTrue(RiskMetrics.valueAtRisk(returns, 0.99) > RiskMetrics.valueAtRisk(returns, 0.90));
    }

    @Test
    @DisplayName("a series that never loses has no value at risk")
    void noLossesGivesZero() {
        double[] gains = new double[50];
        java.util.Arrays.fill(gains, 0.01);

        assertEquals(0.0, RiskMetrics.valueAtRisk(gains, 0.95), 1e-12);
        assertEquals(0.0, RiskMetrics.expectedShortfall(gains, 0.95), 1e-12);
    }

    @Test
    @DisplayName("the tail is measured empirically, so a fat tail is not smoothed away")
    void fatTailIsNotSmoothed() {
        double[] returns = new double[100];
        java.util.Arrays.fill(returns, 0.001);
        returns[0] = -0.30;   // one crash

        double es99 = RiskMetrics.expectedShortfall(returns, 0.99);

        assertEquals(0.30, es99, 1e-9);
    }

    @Test
    @DisplayName("rejects an empty sample or a level outside (0, 1)")
    void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskMetrics.valueAtRisk(new double[0], 0.95));
        assertThrows(IllegalArgumentException.class,
                () -> RiskMetrics.expectedShortfall(rampReturns(), 1.0));
    }
}
