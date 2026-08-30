package io.github.williamhuang1261.qrp.realassets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.realassets.NoiCalculator.OperatingExpenseLineItem;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiscountVacancySensitivityGridTest {

    private static final double SYNPROP_GPR = 264_000.0;

    /** A fixed opex budget, independent of vacancy rate, so the grid isolates the two swept assumptions. */
    private static List<OperatingExpenseLineItem> fixedOperatingExpenses() {
        return List.of(
                new OperatingExpenseLineItem("property tax", 40_000.0),
                new OperatingExpenseLineItem("insurance", 8_000.0),
                new OperatingExpenseLineItem("repairs and maintenance", 15_000.0),
                new OperatingExpenseLineItem("utilities", 10_000.0),
                new OperatingExpenseLineItem("other", 4_000.0));
    }

    @Test
    @DisplayName("the grid has one cell per (discount rate, vacancy rate) pair")
    void gridHasOneCellPerPair() {
        double[] discountRates = {0.07, 0.08, 0.09};
        double[] vacancyRates = {0.03, 0.05, 0.07, 0.10};

        List<DiscountVacancySensitivityGrid.Cell> cells = DiscountVacancySensitivityGrid.build(
                SYNPROP_GPR, fixedOperatingExpenses(), 0.02, 5, 0.07, discountRates, vacancyRates);

        assertEquals(discountRates.length * vacancyRates.length, cells.size());
    }

    @Test
    @DisplayName("value decreases monotonically as the discount rate rises, holding vacancy fixed")
    void valueDecreasesAsDiscountRateRises() {
        double[] discountRates = {0.06, 0.07, 0.08, 0.09, 0.10};
        double[] vacancyRates = {0.05};

        List<DiscountVacancySensitivityGrid.Cell> cells = DiscountVacancySensitivityGrid.build(
                SYNPROP_GPR, fixedOperatingExpenses(), 0.02, 5, 0.07, discountRates, vacancyRates);

        for (int i = 1; i < cells.size(); i++) {
            assertTrue(cells.get(i).presentValue() < cells.get(i - 1).presentValue(),
                    "value at discount rate " + cells.get(i).discountRate()
                            + " should be lower than at " + cells.get(i - 1).discountRate());
        }
    }

    @Test
    @DisplayName("value decreases monotonically as the vacancy/collection loss rate rises, holding discount rate fixed")
    void valueDecreasesAsVacancyRateRises() {
        double[] discountRates = {0.08};
        double[] vacancyRates = {0.03, 0.05, 0.07, 0.10, 0.15};

        List<DiscountVacancySensitivityGrid.Cell> cells = DiscountVacancySensitivityGrid.build(
                SYNPROP_GPR, fixedOperatingExpenses(), 0.02, 5, 0.07, discountRates, vacancyRates);

        for (int i = 1; i < cells.size(); i++) {
            assertTrue(cells.get(i).presentValue() < cells.get(i - 1).presentValue(),
                    "value at vacancy rate " + cells.get(i).vacancyCollectionLossRate()
                            + " should be lower than at " + cells.get(i - 1).vacancyCollectionLossRate());
        }
    }

    @Test
    @DisplayName("the grid's own (8%, 5%) cell reproduces DcfValuation's SYNPROP golden-run number exactly")
    void gridCellMatchesDirectDcfCall() {
        List<DiscountVacancySensitivityGrid.Cell> cells = DiscountVacancySensitivityGrid.build(
                SYNPROP_GPR, fixedOperatingExpenses(), 0.02, 5, 0.07, new double[] {0.08}, new double[] {0.05});

        double directNoi = NoiCalculator.netOperatingIncome(SYNPROP_GPR, 0.05, fixedOperatingExpenses());
        double directValue = DcfValuation.presentValue(new DcfValuation.Inputs(directNoi, 0.02, 5, 0.08, 0.07));

        assertEquals(directValue, cells.get(0).presentValue(), 1e-9);
    }

    @Test
    @DisplayName("an empty discount-rate or vacancy-rate array is rejected")
    void emptyRateArraysRejected() {
        assertThrows(IllegalArgumentException.class, () -> DiscountVacancySensitivityGrid.build(
                SYNPROP_GPR, fixedOperatingExpenses(), 0.02, 5, 0.07, new double[0], new double[] {0.05}));
        assertThrows(IllegalArgumentException.class, () -> DiscountVacancySensitivityGrid.build(
                SYNPROP_GPR, fixedOperatingExpenses(), 0.02, 5, 0.07, new double[] {0.08}, new double[0]));
    }
}
