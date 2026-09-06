package io.github.williamhuang1261.qrp.equity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.options.RatesCurve;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EquityDcfValuationTest {

    /** $10 year-1 FCF/share, 6% explicit growth, 5-year explicit period, 9% discount, 2.5% terminal growth. */
    private static EquityDcfValuation.Inputs sampleInputs() {
        return new EquityDcfValuation.Inputs(10.0, 0.06, 5, 0.09, 0.025);
    }

    @Test
    @DisplayName("fcfInYear grows year 1's FCF at the stated rate, compounding annually")
    void fcfGrowsCompoundAnnually() {
        EquityDcfValuation.Inputs inputs = sampleInputs();

        assertEquals(10.0, inputs.fcfInYear(1), 1e-9);
        assertEquals(10.0 * 1.06, inputs.fcfInYear(2), 1e-9);
        assertEquals(10.0 * Math.pow(1.06, 4), inputs.fcfInYear(5), 1e-9);
    }

    @Test
    @DisplayName("a single-year explicit period reduces to year 1's FCF plus an immediately-following terminal value")
    void singleYearExplicitPeriod() {
        EquityDcfValuation.Inputs oneYear = new EquityDcfValuation.Inputs(10.0, 0.0, 1, 0.10, 0.03);

        double expectedYear1 = 10.0 / 1.10;
        double expectedTerminal = (10.0 / (0.10 - 0.03)) / 1.10;

        assertEquals(expectedYear1 + expectedTerminal, EquityDcfValuation.presentValue(oneYear), 1e-9);
    }

    @Test
    @DisplayName("a higher discount rate produces a lower present value, holding every other input fixed")
    void higherDiscountRateLowersValue() {
        EquityDcfValuation.Inputs lowRate = new EquityDcfValuation.Inputs(10.0, 0.06, 5, 0.08, 0.025);
        EquityDcfValuation.Inputs highRate = new EquityDcfValuation.Inputs(10.0, 0.06, 5, 0.11, 0.025);

        assertTrue(EquityDcfValuation.presentValue(highRate) < EquityDcfValuation.presentValue(lowRate));
    }

    @Test
    @DisplayName("the discounted terminal value is smaller than the total present value")
    void terminalValueSmallerThanTotal() {
        EquityDcfValuation.Inputs inputs = sampleInputs();

        double terminal = EquityDcfValuation.discountedTerminalValue(inputs);
        double total = EquityDcfValuation.presentValue(inputs);

        assertTrue(terminal < total);
        assertTrue(terminal > 0.0);
    }

    @Test
    @DisplayName("valuePerShare divides the present value by diluted shares outstanding")
    void valuePerShareDivides() {
        EquityDcfValuation.Inputs inputs = sampleInputs();
        double presentValue = EquityDcfValuation.presentValue(inputs);

        assertEquals(presentValue / 4.0, EquityDcfValuation.valuePerShare(inputs, 4.0), 1e-9);
    }

    @Test
    @DisplayName("a terminal growth rate at or above the discount rate is rejected: the perpetuity would diverge")
    void terminalGrowthAtOrAboveDiscountRateRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EquityDcfValuation.Inputs(10.0, 0.06, 5, 0.08, 0.08));
        assertThrows(IllegalArgumentException.class,
                () -> new EquityDcfValuation.Inputs(10.0, 0.06, 5, 0.08, 0.09));
    }

    @Test
    @DisplayName("an FCF growth rate at or below -100% is rejected: FCF cannot go non-positive under geometric growth")
    void fcfGrowthRateAtOrBelowNegativeOneRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EquityDcfValuation.Inputs(10.0, -1.0, 5, 0.08, 0.02));
    }

    @Test
    @DisplayName("a zero or negative explicit period is rejected")
    void nonPositiveExplicitYearsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EquityDcfValuation.Inputs(10.0, 0.06, 0, 0.08, 0.02));
    }

    @Test
    @DisplayName("impliedDiscountRate adds a flat equity risk premium to the curve's zero rate")
    void impliedDiscountRateAddsPremium() {
        RatesCurve curve = RatesCurve.of(List.of(
                new RatesCurve.Point(1.0, 0.04),
                new RatesCurve.Point(10.0, 0.045)));

        double rate = EquityDcfValuation.impliedDiscountRate(curve, 10.0, 0.05);

        assertEquals(0.045 + 0.05, rate, 1e-9);
    }

    @Test
    @DisplayName("a negative equity risk premium is rejected")
    void negativeEquityRiskPremiumRejected() {
        RatesCurve curve = RatesCurve.of(List.of(
                new RatesCurve.Point(1.0, 0.04),
                new RatesCurve.Point(10.0, 0.045)));

        assertThrows(IllegalArgumentException.class,
                () -> EquityDcfValuation.impliedDiscountRate(curve, 10.0, -0.01));
    }
}
