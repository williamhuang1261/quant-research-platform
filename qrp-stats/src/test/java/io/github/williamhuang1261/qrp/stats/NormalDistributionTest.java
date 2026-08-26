package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalDistributionTest {

    @Test
    @DisplayName("cdf matches the textbook values")
    void cdfMatchesKnownValues() {
        assertEquals(0.5, NormalDistribution.cdf(0.0), 1e-12);
        assertEquals(0.8413447460685429, NormalDistribution.cdf(1.0), 1e-9);
        assertEquals(0.9750021048517795, NormalDistribution.cdf(1.96), 1e-9);
        assertEquals(0.9986501019683699, NormalDistribution.cdf(3.0), 1e-9);
    }

    @Test
    @DisplayName("cdf is symmetric: N(-x) = 1 - N(x)")
    void cdfIsSymmetric() {
        for (double x : new double[] {0.25, 1.0, 2.5, 4.0, 7.0}) {
            assertEquals(1.0 - NormalDistribution.cdf(x), NormalDistribution.cdf(-x), 1e-12);
        }
    }

    @Test
    @DisplayName("cdf is monotone and stays inside (0, 1)")
    void cdfIsMonotone() {
        double previous = 0.0;
        for (double x = -8.0; x <= 8.0; x += 0.1) {
            double value = NormalDistribution.cdf(x);
            assertTrue(value >= previous, "cdf decreased at x=" + x);
            assertTrue(value >= 0.0 && value <= 1.0, "cdf left [0,1] at x=" + x);
            previous = value;
        }
    }

    @Test
    @DisplayName("the far tail underflows smoothly rather than snapping to zero")
    void farTailStaysPositive() {
        // The reason cdf is built on erfc: a rational approximation returns a flat
        // zero out here, and a deep out-of-the-money option asks for exactly this.
        double tail = NormalDistribution.cdf(-20.0);
        assertTrue(tail > 0.0, "expected a positive tail probability, got " + tail);
        assertTrue(tail < 1e-80, "expected a very small tail probability, got " + tail);
    }

    @Test
    @DisplayName("pdf matches the textbook values and integrates the cdf")
    void pdfMatchesKnownValues() {
        assertEquals(0.3989422804014327, NormalDistribution.pdf(0.0), 1e-15);
        assertEquals(0.24197072451914337, NormalDistribution.pdf(1.0), 1e-12);
        assertEquals(NormalDistribution.pdf(-1.5), NormalDistribution.pdf(1.5), 1e-15);

        // The density is the derivative of the distribution function.
        double h = 1e-5;
        for (double x : new double[] {-1.0, 0.0, 0.7, 2.0}) {
            double numeric = (NormalDistribution.cdf(x + h) - NormalDistribution.cdf(x - h)) / (2 * h);
            assertEquals(NormalDistribution.pdf(x), numeric, 1e-7, "at x=" + x);
        }
    }

    @Test
    @DisplayName("erfc agrees with the cdf it backs")
    void erfcAgreesWithCdf() {
        for (double x : new double[] {-3.0, -0.5, 0.0, 1.25, 4.0}) {
            assertEquals(
                    NormalDistribution.cdf(x),
                    0.5 * NormalDistribution.erfc(-x / Math.sqrt(2.0)),
                    1e-15,
                    "at x=" + x);
        }
    }

    @Test
    @DisplayName("cdf and the quantile function invert each other")
    void roundTripsWithTheQuantile() {
        for (double p : new double[] {0.001, 0.05, 0.25, 0.5, 0.75, 0.95, 0.999}) {
            double x = NormalQuantile.inverseCdf(p);
            assertEquals(p, NormalDistribution.cdf(x), 1e-9, "at p=" + p);
        }
    }

    @Test
    @DisplayName("rejects a non-finite argument")
    void rejectsNonFinite() {
        assertThrows(IllegalArgumentException.class, () -> NormalDistribution.cdf(Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> NormalDistribution.pdf(Double.POSITIVE_INFINITY));
    }
}
