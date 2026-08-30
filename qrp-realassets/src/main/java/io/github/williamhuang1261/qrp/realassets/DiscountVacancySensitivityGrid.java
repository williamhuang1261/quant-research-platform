package io.github.williamhuang1261.qrp.realassets;

import io.github.williamhuang1261.qrp.realassets.NoiCalculator.OperatingExpenseLineItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports the DCF value of a property across a grid of discount rates and
 * vacancy/collection loss rates -- the two assumptions a loan officer or
 * asset manager typically stresses first, since neither is directly
 * observable and both move the value in opposite, compounding directions.
 *
 * <p>Reuses {@link NoiCalculator} and {@link DcfValuation} rather than
 * duplicating either's arithmetic: each grid cell recomputes NOI at that
 * cell's vacancy rate (holding gross potential rent and operating expenses
 * fixed) and feeds it into a {@link DcfValuation.Inputs} at that cell's
 * discount rate.
 */
public final class DiscountVacancySensitivityGrid {

    private DiscountVacancySensitivityGrid() {
    }

    /** One grid cell: the assumptions that produced it and the resulting present value. */
    public record Cell(double discountRate, double vacancyCollectionLossRate, double presentValue) {
    }

    /**
     * @param grossPotentialRentAnnual gross potential rent, held fixed across the grid
     * @param operatingExpenses        operating expenses, held fixed across the grid
     * @param noiGrowthRate            NOI growth rate, held fixed across the grid
     * @param holdingPeriodYears       holding period, held fixed across the grid
     * @param exitCapRate              exit cap rate, held fixed across the grid
     * @param discountRates            the discount rates to grid over
     * @param vacancyCollectionLossRates the vacancy/collection loss rates to grid over
     * @return one {@link Cell} per (discount rate, vacancy rate) pair, in the order supplied
     */
    public static List<Cell> build(
            double grossPotentialRentAnnual,
            List<OperatingExpenseLineItem> operatingExpenses,
            double noiGrowthRate,
            int holdingPeriodYears,
            double exitCapRate,
            double[] discountRates,
            double[] vacancyCollectionLossRates) {
        if (discountRates == null || discountRates.length == 0) {
            throw new IllegalArgumentException("discountRates must not be null or empty");
        }
        if (vacancyCollectionLossRates == null || vacancyCollectionLossRates.length == 0) {
            throw new IllegalArgumentException("vacancyCollectionLossRates must not be null or empty");
        }
        List<Cell> cells = new ArrayList<>(discountRates.length * vacancyCollectionLossRates.length);
        for (double discountRate : discountRates) {
            for (double vacancyRate : vacancyCollectionLossRates) {
                double year1Noi = NoiCalculator.netOperatingIncome(
                        grossPotentialRentAnnual, vacancyRate, operatingExpenses);
                DcfValuation.Inputs inputs = new DcfValuation.Inputs(
                        year1Noi, noiGrowthRate, holdingPeriodYears, discountRate, exitCapRate);
                cells.add(new Cell(discountRate, vacancyRate, DcfValuation.presentValue(inputs)));
            }
        }
        return cells;
    }
}
