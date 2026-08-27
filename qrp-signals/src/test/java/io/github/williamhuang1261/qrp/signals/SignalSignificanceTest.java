package io.github.williamhuang1261.qrp.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignalSignificanceTest {

    @Test
    @DisplayName("a constant nonzero IC across every period is maximally significant")
    void constantNonzeroIcIsMaximallySignificant() {
        // Summing 40 copies of 0.30 and dividing back rarely recovers 0.30
        // bit-for-bit, so the deviations, and therefore the standard error,
        // land at the order of one ULP rather than being an exact 0.0 — the
        // z-statistic this test can actually pin is "very large", not
        // literally Double.POSITIVE_INFINITY. The zero-standard-error branch
        // itself (D4) is exercised exactly, deterministically, by
        // constantZeroIcIsNotSignificant below, where 0.0 - 0.0 is exact.
        double[] icSeries = new double[40];
        java.util.Arrays.fill(icSeries, 0.30);

        SignalSignificance significance = SignalSignificance.of(icSeries);

        assertEquals(0.30, significance.meanIc(), 1e-12);
        assertTrue(significance.standardError() < 1e-10,
                "expected a near-zero standard error, got: " + significance.standardError());
        assertTrue(significance.zStatistic() > 1e6,
                "expected a very large z-statistic, got: " + significance.zStatistic());
        assertEquals(0.0, significance.pValue(), 1e-12);
        assertTrue(significance.isSignificant(0.05));
    }

    @Test
    @DisplayName("a constant IC of exactly zero is reported as not significant")
    void constantZeroIcIsNotSignificant() {
        double[] icSeries = new double[20];

        SignalSignificance significance = SignalSignificance.of(icSeries);

        assertEquals(0.0, significance.zStatistic(), 1e-12);
        assertEquals(1.0, significance.pValue(), 1e-12);
        assertFalse(significance.isSignificant(0.05));
    }

    @Test
    @DisplayName("an IC series with a mirrored, exactly zero-mean spread is not significant")
    void zeroMeanSpreadIsNotSignificant() {
        // Every value's exact negation is also in the series, so the mean is
        // exactly 0.0 by construction — deterministic, not a random draw that
        // could land significant 5% of the time by design of the test itself.
        double[] icSeries = {
                0.05, -0.05, 0.03, -0.03, 0.07, -0.07, 0.02, -0.02,
                0.09, -0.09, 0.01, -0.01, 0.06, -0.06, 0.04, -0.04,
        };

        SignalSignificance significance = SignalSignificance.of(icSeries);

        assertEquals(0.0, significance.meanIc(), 1e-12);
        assertFalse(significance.isSignificant(0.05),
                "z=" + significance.zStatistic() + " p=" + significance.pValue());
    }

    @Test
    @DisplayName("a hand-computed mean and standard error reproduce the expected z-statistic")
    void handComputedZStatistic() {
        // Mean 0.2, sample standard deviation 0.1 (n=5): {0.1, 0.15, 0.2, 0.25, 0.3}.
        double[] icSeries = {0.1, 0.15, 0.2, 0.25, 0.3};

        SignalSignificance significance = SignalSignificance.of(icSeries);

        double expectedStdDev = 0.07905694150420949; // sample std dev, n-1 denominator
        double expectedStandardError = expectedStdDev / Math.sqrt(5);
        double expectedZ = 0.2 / expectedStandardError;

        assertEquals(0.2, significance.meanIc(), 1e-12);
        assertEquals(expectedStandardError, significance.standardError(), 1e-9);
        assertEquals(expectedZ, significance.zStatistic(), 1e-6);
    }

    @Test
    @DisplayName("fewer than 2 periods is rejected")
    void tooFewPeriodsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SignalSignificance.of(new double[] {0.1}));
    }

    @Test
    @DisplayName("a non-finite IC in the series is rejected rather than silently propagated")
    void nonFiniteIcRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SignalSignificance.of(new double[] {0.1, Double.NaN, 0.2}));
    }

    @Test
    @DisplayName("isSignificant rejects an alpha outside (0, 1)")
    void isSignificantValidatesAlpha() {
        SignalSignificance significance = SignalSignificance.of(new double[] {0.1, 0.2, 0.3});
        assertThrows(IllegalArgumentException.class, () -> significance.isSignificant(0.0));
        assertThrows(IllegalArgumentException.class, () -> significance.isSignificant(1.0));
    }
}
