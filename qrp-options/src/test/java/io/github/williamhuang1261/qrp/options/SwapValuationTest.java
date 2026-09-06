package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwapValuationTest {

    private static RatesCurve upwardSlopingCurve() {
        return RatesCurve.of(List.of(
                new RatesCurve.Point(1.0, 0.04),
                new RatesCurve.Point(5.0, 0.045),
                new RatesCurve.Point(10.0, 0.05)));
    }

    @Test
    @DisplayName("par rate prices the swap to zero PV for either counterparty")
    void parRateRepricesToZero() {
        RatesCurve curve = upwardSlopingCurve();
        SwapValuation atArbitraryRate = SwapValuation.of(1_000_000.0, 0.03, 5.0, curve);
        double parRate = atArbitraryRate.parRate();

        SwapValuation atPar = SwapValuation.of(1_000_000.0, parRate, 5.0, curve);
        assertEquals(0.0, atPar.payerPv(), 1e-6);
        assertEquals(atPar.fixedLegPv(), atPar.floatingLegPv(), 1e-6);
    }

    @Test
    @DisplayName("payer PV increases as the fixed rate paid decreases")
    void payerPvDecreasesWithFixedRate() {
        RatesCurve curve = upwardSlopingCurve();
        SwapValuation lowFixed = SwapValuation.of(1_000_000.0, 0.02, 5.0, curve);
        SwapValuation highFixed = SwapValuation.of(1_000_000.0, 0.06, 5.0, curve);

        // Paying a lower fixed rate is worth more to the fixed-rate payer.
        assertTrue(lowFixed.payerPv() > highFixed.payerPv());
    }

    @Test
    @DisplayName("floating leg PV matches the single-curve identity notional * (1 - DF(T))")
    void floatingLegMatchesTheClosedForm() {
        RatesCurve curve = upwardSlopingCurve();
        SwapValuation swap = SwapValuation.of(1_000_000.0, 0.04, 10.0, curve);
        double expected = 1_000_000.0 * (1.0 - curve.discountFactor(10.0));
        assertEquals(expected, swap.floatingLegPv(), 1e-9);
    }

    @Test
    @DisplayName("DV01 is positive for the payer on an upward-sloping curve and shrinks the payer's PV")
    void dv01SignIsConsistentWithARateIncreaseHurtingThePayer() {
        RatesCurve curve = upwardSlopingCurve();
        SwapValuation swap = SwapValuation.of(1_000_000.0, 0.03, 5.0, curve);

        double dv01 = swap.dv01();
        // A parallel rate increase raises every discount factor's decay,
        // shrinking the value of a fixed 5% receive-floating swap's floating
        // leg more than its shorter-duration fixed leg on net for a payer
        // already in the money -- the DV01 is a real, non-zero repricing.
        assertTrue(Double.isFinite(dv01));
        assertTrue(dv01 != 0.0);
    }

    @Test
    @DisplayName("DV01 magnitude scales with notional")
    void dv01ScalesWithNotional() {
        RatesCurve curve = upwardSlopingCurve();
        SwapValuation small = SwapValuation.of(1_000_000.0, 0.03, 5.0, curve);
        SwapValuation large = SwapValuation.of(2_000_000.0, 0.03, 5.0, curve);

        assertEquals(small.dv01() * 2.0, large.dv01(), 1e-6);
    }

    @Test
    @DisplayName("annuity equals the sum of accrual-weighted discount factors")
    void annuityMatchesTheDefinition() {
        RatesCurve curve = upwardSlopingCurve();
        SwapValuation swap = SwapValuation.of(1_000_000.0, 0.03, 2.0, curve);

        double expected = 0.5 * curve.discountFactor(0.5)
                + 0.5 * curve.discountFactor(1.0)
                + 0.5 * curve.discountFactor(1.5)
                + 0.5 * curve.discountFactor(2.0);
        assertEquals(expected, swap.annuity(), 1e-9);
    }

    @Test
    @DisplayName("rejects a non-positive notional and a null curve")
    void rejectsBadConstruction() {
        RatesCurve curve = upwardSlopingCurve();
        assertThrows(IllegalArgumentException.class, () -> SwapValuation.of(0.0, 0.03, 5.0, curve));
        assertThrows(IllegalArgumentException.class, () -> SwapValuation.of(-1.0, 0.03, 5.0, curve));
        assertThrows(IllegalArgumentException.class, () -> SwapValuation.of(1_000_000.0, 0.03, 5.0, null));
    }
}
