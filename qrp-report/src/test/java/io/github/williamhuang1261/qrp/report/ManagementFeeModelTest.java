package io.github.williamhuang1261.qrp.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ManagementFeeModelTest {

    @Test
    void zeroFeeReproducesTheGrossCurveExactly() {
        double[] gross = {100.0, 103.0, 98.5, 110.25};

        double[] net = ManagementFeeModel.none().applyTo(gross, 252.0);

        assertArrayEquals(gross, net, 0.0);
    }

    @Test
    void twoPercentAnnualFeeOverOneCompoundingPeriod() {
        // One period per year: the whole annual fee applies in a single step.
        ManagementFeeModel fee = new ManagementFeeModel(0.02);
        double[] gross = {100.0, 110.0};

        double[] net = fee.applyTo(gross, 1.0);

        // 100 grows 10% gross to 110, then retains (1 - 0.02) of that step.
        assertEquals(107.8, net[1], 1e-9);
    }

    @Test
    void twoPercentAnnualFeeCompoundedDailyDrainsExactlyTwoPercentOverOneYear() {
        double periodsPerYear = 252.0;
        int oneYearOfBars = 252;
        double[] flatGross = new double[oneYearOfBars + 1];
        java.util.Arrays.fill(flatGross, 100.0); // no gross return at all

        double[] net = new ManagementFeeModel(0.02).applyTo(flatGross, periodsPerYear);

        // With zero gross return, the fee alone should leave exactly 98% after one year.
        assertEquals(98.0, net[oneYearOfBars], 1e-9);
    }

    @Test
    void rejectsAFeeOutsideZeroToOne() {
        assertThrows(IllegalArgumentException.class, () -> new ManagementFeeModel(-0.01));
        assertThrows(IllegalArgumentException.class, () -> new ManagementFeeModel(1.0));
    }
}
