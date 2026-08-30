package io.github.williamhuang1261.qrp.realassets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.williamhuang1261.qrp.realassets.NoiCalculator.OperatingExpenseLineItem;
import io.github.williamhuang1261.qrp.realassets.NoiCalculator.RentRollUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoiCalculatorTest {

    /**
     * The SYNPROP sample: 10 units at $1,000/month (unit 105 vacant) and 10
     * units at $1,200/month (unit 210 vacant), matching
     * {@code data/realassets/SYNPROP_rentroll.csv} exactly.
     */
    private static List<RentRollUnit> synpropRentRoll() {
        return List.of(
                new RentRollUnit("101", 1000.0, true),
                new RentRollUnit("102", 1000.0, true),
                new RentRollUnit("103", 1000.0, true),
                new RentRollUnit("104", 1000.0, true),
                new RentRollUnit("105", 1000.0, false),
                new RentRollUnit("106", 1000.0, true),
                new RentRollUnit("107", 1000.0, true),
                new RentRollUnit("108", 1000.0, true),
                new RentRollUnit("109", 1000.0, true),
                new RentRollUnit("110", 1000.0, true),
                new RentRollUnit("201", 1200.0, true),
                new RentRollUnit("202", 1200.0, true),
                new RentRollUnit("203", 1200.0, true),
                new RentRollUnit("204", 1200.0, true),
                new RentRollUnit("205", 1200.0, true),
                new RentRollUnit("206", 1200.0, true),
                new RentRollUnit("207", 1200.0, true),
                new RentRollUnit("208", 1200.0, true),
                new RentRollUnit("209", 1200.0, true),
                new RentRollUnit("210", 1200.0, false));
    }

    private static List<OperatingExpenseLineItem> synpropOperatingExpenses(double egi) {
        return List.of(
                new OperatingExpenseLineItem("property tax", 40_000.0),
                new OperatingExpenseLineItem("insurance", 8_000.0),
                new OperatingExpenseLineItem("repairs and maintenance", 15_000.0),
                new OperatingExpenseLineItem("management fee", 0.05 * egi),
                new OperatingExpenseLineItem("utilities", 10_000.0),
                new OperatingExpenseLineItem("other", 4_000.0));
    }

    @Test
    @DisplayName("SYNPROP's gross potential rent is the golden-run number: $264,000/yr")
    void synpropGrossPotentialRent() {
        double gpr = NoiCalculator.grossPotentialRentAnnual(synpropRentRoll());

        assertEquals(264_000.0, gpr, 1e-9);
    }

    @Test
    @DisplayName("SYNPROP's physical occupancy is 90% by unit count but 89.02% by rent dollars, since the vacant units differ in rent")
    void synpropPhysicalOccupancyByRentDollars() {
        double occupancy = NoiCalculator.physicalOccupancyRate(synpropRentRoll());

        // occupied rent = 264,000 - 1,000*12 - 1,200*12 = 264,000 - 26,400 = 237,600
        assertEquals(237_600.0 / 264_000.0, occupancy, 1e-9);
    }

    @Test
    @DisplayName("SYNPROP's effective gross income at a 5% vacancy/collection loss factor is the golden-run number: $250,800/yr")
    void synpropEffectiveGrossIncome() {
        double gpr = NoiCalculator.grossPotentialRentAnnual(synpropRentRoll());

        double egi = NoiCalculator.effectiveGrossIncome(gpr, 0.05);

        assertEquals(250_800.0, egi, 1e-9);
    }

    @Test
    @DisplayName("SYNPROP's NOI is the golden-run number: $161,260/yr")
    void synpropNetOperatingIncome() {
        double gpr = NoiCalculator.grossPotentialRentAnnual(synpropRentRoll());
        double egi = NoiCalculator.effectiveGrossIncome(gpr, 0.05);
        List<OperatingExpenseLineItem> opex = synpropOperatingExpenses(egi);

        double noi = NoiCalculator.netOperatingIncome(gpr, 0.05, opex);

        // egi 250,800 - opex (40,000 + 8,000 + 15,000 + 12,540 + 10,000 + 4,000 = 89,540) = 161,260
        assertEquals(161_260.0, noi, 1e-9);
    }

    @Test
    @DisplayName("an empty rent roll is rejected rather than silently valued at zero")
    void emptyRentRollRejected() {
        assertThrows(IllegalArgumentException.class, () -> NoiCalculator.grossPotentialRentAnnual(List.of()));
    }

    @Test
    @DisplayName("a vacancy/collection loss rate of 1.0 or above is rejected: it would mean the property earns nothing or less")
    void vacancyRateAtOrAboveOneRejected() {
        assertThrows(IllegalArgumentException.class, () -> NoiCalculator.effectiveGrossIncome(100_000.0, 1.0));
    }

    @Test
    @DisplayName("a negative operating expense line item is rejected")
    void negativeExpenseRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OperatingExpenseLineItem("bad", -1.0));
    }
}
