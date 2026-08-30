package io.github.williamhuang1261.qrp.realassets;

import java.util.List;

/**
 * Turns a rent roll and an operating expense budget into net operating
 * income (NOI), the single number every valuation method in this module
 * starts from.
 *
 * <p><b>Vacancy is a stated proforma assumption, not derived from
 * {@link RentRollUnit#occupied}.</b> A real rent roll's occupied count is a
 * single day's snapshot, which can run temporarily better or worse than a
 * property's stabilized performance. This class reports the snapshot's own
 * physical occupancy separately ({@link #physicalOccupancyRate}) but values
 * the property against a caller-supplied vacancy and collection loss rate
 * instead, the same way a loan officer underwrites to a market-standard
 * factor rather than whatever one rent roll happens to show. See
 * {@code data/realassets/README.md}.
 */
public final class NoiCalculator {

    private NoiCalculator() {
    }

    /** One rent roll line: a unit's market rent and whether it is currently occupied. */
    public record RentRollUnit(String unitId, double monthlyMarketRent, boolean occupied) {
        public RentRollUnit {
            if (unitId == null || unitId.isBlank()) {
                throw new IllegalArgumentException("unitId must not be blank");
            }
            if (!(monthlyMarketRent >= 0.0) || !Double.isFinite(monthlyMarketRent)) {
                throw new IllegalArgumentException(
                        "monthlyMarketRent must be non-negative and finite, got: " + monthlyMarketRent);
            }
        }
    }

    /** One annual operating expense line item, e.g. property tax or insurance. */
    public record OperatingExpenseLineItem(String name, double annualAmount) {
        public OperatingExpenseLineItem {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (!(annualAmount >= 0.0) || !Double.isFinite(annualAmount)) {
                throw new IllegalArgumentException(
                        "annualAmount must be non-negative and finite, got: " + annualAmount);
            }
        }
    }

    /** Every unit's market rent at full occupancy, annualized (monthly rent times 12, summed). */
    public static double grossPotentialRentAnnual(List<RentRollUnit> rentRoll) {
        requireNonEmpty(rentRoll, "rentRoll");
        double total = 0.0;
        for (RentRollUnit unit : rentRoll) {
            total += unit.monthlyMarketRent() * 12.0;
        }
        return total;
    }

    /**
     * The fraction of gross potential rent currently occupied, by rent
     * dollars rather than unit count -- a $1,200 vacant unit costs more than
     * a $1,000 one. Informational only; see the class javadoc.
     */
    public static double physicalOccupancyRate(List<RentRollUnit> rentRoll) {
        requireNonEmpty(rentRoll, "rentRoll");
        double totalRent = 0.0;
        double occupiedRent = 0.0;
        for (RentRollUnit unit : rentRoll) {
            totalRent += unit.monthlyMarketRent();
            if (unit.occupied()) {
                occupiedRent += unit.monthlyMarketRent();
            }
        }
        if (totalRent == 0.0) {
            throw new IllegalStateException("rent roll has zero total rent, cannot compute an occupancy rate");
        }
        return occupiedRent / totalRent;
    }

    /** Gross potential rent minus a stated vacancy and collection loss rate. */
    public static double effectiveGrossIncome(double grossPotentialRentAnnual, double vacancyCollectionLossRate) {
        requireRate(vacancyCollectionLossRate, "vacancyCollectionLossRate");
        if (!(grossPotentialRentAnnual >= 0.0) || !Double.isFinite(grossPotentialRentAnnual)) {
            throw new IllegalArgumentException(
                    "grossPotentialRentAnnual must be non-negative and finite, got: " + grossPotentialRentAnnual);
        }
        return grossPotentialRentAnnual * (1.0 - vacancyCollectionLossRate);
    }

    /** The sum of every operating expense line item's annual amount. */
    public static double totalOperatingExpenses(List<OperatingExpenseLineItem> operatingExpenses) {
        requireNonEmpty(operatingExpenses, "operatingExpenses");
        double total = 0.0;
        for (OperatingExpenseLineItem item : operatingExpenses) {
            total += item.annualAmount();
        }
        return total;
    }

    /**
     * Net operating income: effective gross income (gross potential rent
     * less vacancy and collection loss) minus total operating expenses.
     * Does not deduct debt service or capital expenditures -- NOI is,
     * deliberately, a pre-financing, pre-capex number. See
     * {@code docs/spec-realassets.md}.
     */
    public static double netOperatingIncome(
            double grossPotentialRentAnnual,
            double vacancyCollectionLossRate,
            List<OperatingExpenseLineItem> operatingExpenses) {
        double egi = effectiveGrossIncome(grossPotentialRentAnnual, vacancyCollectionLossRate);
        double opex = totalOperatingExpenses(operatingExpenses);
        return egi - opex;
    }

    private static void requireRate(double rate, String name) {
        if (!(rate >= 0.0) || !(rate < 1.0) || !Double.isFinite(rate)) {
            throw new IllegalArgumentException(name + " must be in [0, 1), got: " + rate);
        }
    }

    private static void requireNonEmpty(List<?> list, String name) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be null or empty");
        }
    }
}
