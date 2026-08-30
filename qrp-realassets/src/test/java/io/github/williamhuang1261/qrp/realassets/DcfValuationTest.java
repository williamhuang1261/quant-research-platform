package io.github.williamhuang1261.qrp.realassets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DcfValuationTest {

    /** SYNPROP's year-1 NOI ($161,260) at a 2% NOI growth rate, 5-year hold, 8% discount, 7% exit cap. */
    private static DcfValuation.Inputs synpropInputs() {
        return new DcfValuation.Inputs(161_260.0, 0.02, 5, 0.08, 0.07);
    }

    @Test
    @DisplayName("noiInYear grows year 1's NOI at the stated rate, compounding annually")
    void noiGrowsCompoundAnnually() {
        DcfValuation.Inputs inputs = synpropInputs();

        assertEquals(161_260.0, inputs.noiInYear(1), 1e-9);
        assertEquals(161_260.0 * 1.02, inputs.noiInYear(2), 1e-6);
        assertEquals(161_260.0 * Math.pow(1.02, 4), inputs.noiInYear(5), 1e-6);
    }

    @Test
    @DisplayName("SYNPROP's DCF present value is the golden-run number")
    void synpropPresentValue() {
        double presentValue = DcfValuation.presentValue(synpropInputs());

        assertEquals(2_399_157.6128713517, presentValue, 1e-3);
    }

    @Test
    @DisplayName("SYNPROP's discounted terminal value is the golden-run number and is less than the total present value")
    void synpropDiscountedTerminalValue() {
        double terminalValue = DcfValuation.discountedTerminalValue(synpropInputs());
        double totalValue = DcfValuation.presentValue(synpropInputs());

        assertEquals(1_731_054.3227718864, terminalValue, 1e-3);
        assertTrue(terminalValue < totalValue);
    }

    @Test
    @DisplayName("a single-year holding period reduces to next year's NOI stream plus an immediately-following terminal value")
    void singleYearHoldingPeriod() {
        DcfValuation.Inputs oneYear = new DcfValuation.Inputs(100_000.0, 0.0, 1, 0.10, 0.08);

        double expectedYear1 = 100_000.0 / 1.10;
        double expectedTerminal = (100_000.0 / 0.08) / 1.10;

        assertEquals(expectedYear1 + expectedTerminal, DcfValuation.presentValue(oneYear), 1e-6);
    }

    @Test
    @DisplayName("a higher discount rate produces a lower present value, holding every other input fixed")
    void higherDiscountRateLowersValue() {
        DcfValuation.Inputs lowRate = new DcfValuation.Inputs(161_260.0, 0.02, 5, 0.07, 0.07);
        DcfValuation.Inputs highRate = new DcfValuation.Inputs(161_260.0, 0.02, 5, 0.09, 0.07);

        assertTrue(DcfValuation.presentValue(highRate) < DcfValuation.presentValue(lowRate));
    }

    @Test
    @DisplayName("a growth rate at or below -100% is rejected: NOI cannot go non-positive under geometric growth")
    void growthRateAtOrBelowNegativeOneRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DcfValuation.Inputs(100_000.0, -1.0, 5, 0.08, 0.07));
    }

    @Test
    @DisplayName("a zero or negative holding period is rejected")
    void nonPositiveHoldingPeriodRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DcfValuation.Inputs(100_000.0, 0.02, 0, 0.08, 0.07));
    }
}
