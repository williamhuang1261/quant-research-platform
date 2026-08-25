package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EquityCurveTest {

    @Test
    @DisplayName("max drawdown finds the deepest fall, not the last")
    void maxDrawdownFindsTheDeepest() {
        assertEquals(0.25, EquityCurve.maxDrawdown(new double[] {100, 120, 90, 130, 117}), 1e-12);
    }

    @Test
    @DisplayName("returns and equity are inverses of each other")
    void returnsRoundTrip() {
        double[] equity = {100.0, 110.0, 99.0, 118.8};

        double[] returns = EquityCurve.toReturns(equity);
        double[] rebuilt = EquityCurve.fromReturns(100.0, returns);

        assertEquals(3, returns.length);
        assertArrayEquals(equity, rebuilt, 1e-9);
    }

    @Test
    @DisplayName("a single point has no returns and no drawdown")
    void degenerateCurves() {
        assertEquals(0, EquityCurve.toReturns(new double[] {100.0}).length);
        assertEquals(0.0, EquityCurve.maxDrawdown(new double[] {100.0}), 1e-12);
    }
}
