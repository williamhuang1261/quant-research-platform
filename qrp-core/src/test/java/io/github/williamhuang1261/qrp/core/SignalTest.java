package io.github.williamhuang1261.qrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignalTest {

    @Test
    @DisplayName("exposure is bounded to [-1, 1]")
    void exposureIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new Signal(1.5));
        assertThrows(IllegalArgumentException.class, () -> new Signal(-1.5));
        assertThrows(IllegalArgumentException.class, () -> new Signal(Double.NaN));
    }

    @Test
    @DisplayName("the factories cover the three states a strategy usually wants")
    void factoriesCoverCommonStates() {
        assertTrue(Signal.flat().isFlat());
        assertTrue(Signal.fullyLong().isLong());
        assertTrue(Signal.fullyShort().isShort());
        assertFalse(Signal.fullyLong().isShort());
        assertEquals(-1.0, Signal.fullyShort().targetExposure(), 1e-12);
    }

    @Test
    @DisplayName("a partial exposure is legal")
    void partialExposureIsLegal() {
        Signal half = new Signal(0.5);

        assertTrue(half.isLong());
        assertEquals(0.5, half.targetExposure(), 1e-12);
    }
}
