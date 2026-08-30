package io.github.williamhuang1261.qrp.realassets;

/**
 * A multi-year discounted cash flow: a growing NOI stream for a stated
 * holding period, plus a terminal value from an exit capitalization applied
 * to the year immediately after the holding period, all discounted back to
 * present value.
 *
 * <p><b>Annual, discretely compounded discounting</b> -- {@code (1 + r)^-t}
 * -- not the continuous compounding {@code e^{-r t}} the platform's
 * {@code qrp-options} module uses for bonds and options. This is a
 * deliberate, module-local choice, not an inconsistency: a bond's cash flows
 * arrive on a fixed coupon schedule that a trading desk prices continuously,
 * while a real estate proforma is conventionally built one discrete fiscal
 * year at a time -- the market convention this module exists to match, the
 * same way {@code BondAnalytics}'s own javadoc explains why it does not
 * follow the bond market's semi-annual bond-equivalent-yield convention
 * either.
 */
public final class DcfValuation {

    private DcfValuation() {
    }

    /**
     * @param year1Noi           the first year's net operating income
     * @param noiGrowthRate      the annual NOI growth rate, as a decimal
     *                           (2% is {@code 0.02})
     * @param holdingPeriodYears the number of years of NOI discounted
     *                           explicitly before the terminal value
     * @param discountRate       the annual discount rate applied to every
     *                           cash flow, as a decimal
     * @param exitCapRate        the capitalization rate applied to the NOI
     *                           of the year immediately after the holding
     *                           period, to derive the terminal (resale)
     *                           value at the end of the holding period
     */
    public record Inputs(
            double year1Noi,
            double noiGrowthRate,
            int holdingPeriodYears,
            double discountRate,
            double exitCapRate) {

        public Inputs {
            if (!Double.isFinite(year1Noi)) {
                throw new IllegalArgumentException("year1Noi must be finite, got: " + year1Noi);
            }
            if (!Double.isFinite(noiGrowthRate) || noiGrowthRate <= -1.0) {
                throw new IllegalArgumentException(
                        "noiGrowthRate must be finite and greater than -1, got: " + noiGrowthRate);
            }
            if (holdingPeriodYears < 1) {
                throw new IllegalArgumentException(
                        "holdingPeriodYears must be at least 1, got: " + holdingPeriodYears);
            }
            if (!(discountRate > 0.0) || !Double.isFinite(discountRate)) {
                throw new IllegalArgumentException(
                        "discountRate must be positive and finite, got: " + discountRate);
            }
            if (!(exitCapRate > 0.0) || !Double.isFinite(exitCapRate)) {
                throw new IllegalArgumentException(
                        "exitCapRate must be positive and finite, got: " + exitCapRate);
            }
        }

        /** NOI in a given holding year, 1-indexed, grown from {@link #year1Noi} at {@link #noiGrowthRate}. */
        public double noiInYear(int year) {
            if (year < 1) {
                throw new IllegalArgumentException("year must be at least 1, got: " + year);
            }
            return year1Noi * Math.pow(1.0 + noiGrowthRate, year - 1);
        }
    }

    /**
     * The present value of the holding-period NOI stream plus the
     * discounted terminal value.
     */
    public static double presentValue(Inputs inputs) {
        double presentValue = 0.0;
        for (int year = 1; year <= inputs.holdingPeriodYears(); year++) {
            double noi = inputs.noiInYear(year);
            presentValue += noi / Math.pow(1.0 + inputs.discountRate(), year);
        }
        presentValue += discountedTerminalValue(inputs);
        return presentValue;
    }

    /**
     * The terminal (resale) value at the end of the holding period --
     * next year's NOI capitalized at the exit cap rate -- discounted back
     * to present value. Exposed separately so a caller can see how much of
     * the total value is carried by the exit assumption versus the
     * operating cash flows.
     */
    public static double discountedTerminalValue(Inputs inputs) {
        double noiAfterHolding = inputs.noiInYear(inputs.holdingPeriodYears() + 1);
        double terminalValue = DirectCapValuation.value(noiAfterHolding, inputs.exitCapRate());
        return terminalValue / Math.pow(1.0 + inputs.discountRate(), inputs.holdingPeriodYears());
    }
}
