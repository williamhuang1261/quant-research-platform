package io.github.williamhuang1261.qrp.realassets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DirectCapValuationTest {

    @Test
    @DisplayName("SYNPROP's direct-cap value at a 6.5% cap rate is the golden-run number: $2,480,923.08")
    void synpropDirectCapValue() {
        double value = DirectCapValuation.value(161_260.0, 0.065);

        assertEquals(2_480_923.0769230768, value, 1e-6);
    }

    @Test
    @DisplayName("halving the cap rate doubles the value, holding NOI fixed")
    void valueIsInverselyProportionalToCapRate() {
        double atFullRate = DirectCapValuation.value(100_000.0, 0.08);
        double atHalfRate = DirectCapValuation.value(100_000.0, 0.04);

        assertEquals(atFullRate * 2.0, atHalfRate, 1e-9);
    }

    @Test
    @DisplayName("a zero or negative cap rate is rejected rather than dividing into an infinite or negative value")
    void nonPositiveCapRateRejected() {
        assertThrows(IllegalArgumentException.class, () -> DirectCapValuation.value(100_000.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> DirectCapValuation.value(100_000.0, -0.01));
    }
}
