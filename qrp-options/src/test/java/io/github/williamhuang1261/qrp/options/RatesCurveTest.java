package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RatesCurveTest {

    private static RatesCurve threePoint() {
        return RatesCurve.of(List.of(
                new RatesCurve.Point(1.0, 0.04),
                new RatesCurve.Point(5.0, 0.045),
                new RatesCurve.Point(10.0, 0.05)));
    }

    @Test
    @DisplayName("returns the exact quoted rate at a quoted tenor")
    void returnsExactRateAtQuotedTenors() {
        RatesCurve curve = threePoint();
        assertEquals(0.04, curve.zeroRate(1.0), 1e-15);
        assertEquals(0.045, curve.zeroRate(5.0), 1e-15);
        assertEquals(0.05, curve.zeroRate(10.0), 1e-15);
    }

    @Test
    @DisplayName("interpolates linearly between two quoted tenors")
    void interpolatesLinearly() {
        RatesCurve curve = threePoint();
        // Halfway between the 1y (4%) and 5y (4.5%) points.
        assertEquals(0.0425, curve.zeroRate(3.0), 1e-12);
    }

    @Test
    @DisplayName("extrapolates flat past either end")
    void extrapolatesFlat() {
        RatesCurve curve = threePoint();
        assertEquals(0.04, curve.zeroRate(0.1), 1e-15);
        assertEquals(0.05, curve.zeroRate(50.0), 1e-15);
        assertEquals(curve.shortestTenor(), 1.0);
        assertEquals(curve.longestTenor(), 10.0);
    }

    @Test
    @DisplayName("discount factor is 1 at time zero and decreasing thereafter")
    void discountFactorBehavesCorrectly() {
        RatesCurve curve = threePoint();
        assertEquals(1.0, curve.discountFactor(0.0), 1e-15);

        double previous = 1.0;
        for (double years : new double[] {1.0, 5.0, 10.0, 20.0}) {
            double discountFactor = curve.discountFactor(years);
            assertTrue(discountFactor < previous, "discount factor did not decrease at years=" + years);
            assertTrue(discountFactor > 0.0 && discountFactor <= 1.0);
            previous = discountFactor;
        }
    }

    @Test
    @DisplayName("forward rate over a flat curve equals the flat rate")
    void forwardRateOverAFlatSegmentEqualsTheZeroRate() {
        RatesCurve flat = RatesCurve.of(List.of(
                new RatesCurve.Point(1.0, 0.04), new RatesCurve.Point(2.0, 0.04)));
        assertEquals(0.04, flat.forwardRate(1.0, 2.0), 1e-12);
    }

    @Test
    @DisplayName("forward rate matches the algebra: r2*t2 - r1*t1 over t2-t1")
    void forwardRateMatchesTheDefinition() {
        RatesCurve curve = threePoint();
        double r1 = curve.zeroRate(1.0);
        double r5 = curve.zeroRate(5.0);
        double expected = (r5 * 5.0 - r1 * 1.0) / (5.0 - 1.0);

        assertEquals(expected, curve.forwardRate(1.0, 5.0), 1e-12);
    }

    @Test
    @DisplayName("rejects fewer than two points, and malformed points")
    void rejectsBadConstruction() {
        assertThrows(IllegalArgumentException.class, () -> RatesCurve.of(List.of(new RatesCurve.Point(1.0, 0.04))));
        assertThrows(IllegalArgumentException.class, () -> RatesCurve.of(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> RatesCurve.of(List.of(new RatesCurve.Point(-1.0, 0.04), new RatesCurve.Point(1.0, 0.04))));
    }

    @Test
    @DisplayName("rejects an invalid forward window")
    void rejectsInvalidForwardWindow() {
        RatesCurve curve = threePoint();
        assertThrows(IllegalArgumentException.class, () -> curve.forwardRate(5.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> curve.forwardRate(5.0, 5.0));
    }
}
