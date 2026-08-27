package io.github.williamhuang1261.qrp.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForwardReturnsTest {

    @Test
    @DisplayName("a 1-bar forward return matches the simple close-to-close ratio")
    void oneBarForward() {
        double[] closes = {100.0, 110.0, 99.0};
        assertEquals(0.10, ForwardReturns.forwardReturn(closes, 0, 1), 1e-12);
        assertEquals(-0.10, ForwardReturns.forwardReturn(closes, 1, 1), 1e-9);
    }

    @Test
    @DisplayName("a multi-bar horizon compounds correctly")
    void multiBarForward() {
        double[] closes = {100.0, 110.0, 121.0};
        assertEquals(0.21, ForwardReturns.forwardReturn(closes, 0, 2), 1e-12);
    }

    @Test
    @DisplayName("a horizon running past the end of the series is NaN, not an exception")
    void horizonPastEndIsNaN() {
        double[] closes = {100.0, 110.0, 121.0};
        assertTrue(Double.isNaN(ForwardReturns.forwardReturn(closes, 2, 1)));
        assertTrue(Double.isNaN(ForwardReturns.forwardReturn(closes, 1, 5)));
    }

    @Test
    @DisplayName("a non-positive horizon is rejected")
    void nonPositiveHorizonRejected() {
        double[] closes = {100.0, 110.0};
        assertThrows(IllegalArgumentException.class, () -> ForwardReturns.forwardReturn(closes, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ForwardReturns.forwardReturn(closes, 0, -1));
    }

    @Test
    @DisplayName("an out-of-range index is rejected")
    void outOfRangeIndexRejected() {
        double[] closes = {100.0, 110.0};
        assertThrows(IllegalArgumentException.class, () -> ForwardReturns.forwardReturn(closes, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> ForwardReturns.forwardReturn(closes, 2, 1));
    }
}
