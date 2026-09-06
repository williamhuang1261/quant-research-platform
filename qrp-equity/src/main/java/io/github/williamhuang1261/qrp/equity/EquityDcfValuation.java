package io.github.williamhuang1261.qrp.equity;

import io.github.williamhuang1261.qrp.options.RatesCurve;

/**
 * A multi-year discounted free-cash-flow valuation: an explicit growth
 * period followed by a Gordon-growth (constant perpetual growth) terminal
 * value, discounted back to present value.
 *
 * <p><b>Annual, discretely compounded discounting</b> -- {@code (1 + r)^-t}
 * -- the same convention {@code qrp-realassets}'s {@link
 * io.github.williamhuang1261.qrp.realassets.DcfValuation} already uses for
 * real estate, and for the same reason stated in that class's javadoc: a
 * company's cash-flow projection is conventionally built one discrete fiscal
 * year at a time, unlike the continuously-compounded bond and option pricing
 * in {@code qrp-options}. Only the terminal-value shape differs from the
 * real-estate module -- a perpetuity growth formula here instead of an exit
 * capitalization rate, since a public company is not conventionally modelled
 * as being sold at the end of a holding period.
 */
public final class EquityDcfValuation {

    private EquityDcfValuation() {
    }

    /**
     * @param year1FreeCashFlow the first year's free cash flow
     * @param fcfGrowthRate     the annual FCF growth rate during the explicit
     *                          period, as a decimal (5% is {@code 0.05})
     * @param explicitYears     the number of years of FCF discounted
     *                          explicitly before the terminal value
     * @param discountRate      the annual discount rate applied to every cash
     *                          flow, as a decimal; see {@link
     *                          #impliedDiscountRate}
     * @param terminalGrowthRate the perpetual growth rate applied to FCF
     *                           after the explicit period, as a decimal;
     *                           must be strictly less than {@code
     *                           discountRate} or the perpetuity diverges
     */
    public record Inputs(
            double year1FreeCashFlow,
            double fcfGrowthRate,
            int explicitYears,
            double discountRate,
            double terminalGrowthRate) {

        public Inputs {
            if (!Double.isFinite(year1FreeCashFlow)) {
                throw new IllegalArgumentException(
                        "year1FreeCashFlow must be finite, got: " + year1FreeCashFlow);
            }
            if (!Double.isFinite(fcfGrowthRate) || fcfGrowthRate <= -1.0) {
                throw new IllegalArgumentException(
                        "fcfGrowthRate must be finite and greater than -1, got: " + fcfGrowthRate);
            }
            if (explicitYears < 1) {
                throw new IllegalArgumentException(
                        "explicitYears must be at least 1, got: " + explicitYears);
            }
            if (!(discountRate > 0.0) || !Double.isFinite(discountRate)) {
                throw new IllegalArgumentException(
                        "discountRate must be positive and finite, got: " + discountRate);
            }
            if (!Double.isFinite(terminalGrowthRate) || terminalGrowthRate >= discountRate) {
                throw new IllegalArgumentException(
                        "terminalGrowthRate must be finite and less than discountRate, got: "
                                + terminalGrowthRate + " >= " + discountRate);
            }
        }

        /** FCF in a given explicit year, 1-indexed, grown from {@link #year1FreeCashFlow}. */
        public double fcfInYear(int year) {
            if (year < 1) {
                throw new IllegalArgumentException("year must be at least 1, got: " + year);
            }
            return year1FreeCashFlow * Math.pow(1.0 + fcfGrowthRate, year - 1);
        }
    }

    /**
     * The real risk-free rate at {@code atYears} off the platform's own
     * Treasury curve, plus a flat, stated equity risk premium.
     *
     * <p>This mixes a continuously-compounded zero rate ({@link
     * RatesCurve#zeroRate}) with a simple additive premium -- an intentional,
     * common desk-level simplification, not a rigorous CAPM cost of equity.
     * A real cost-of-equity estimate would fit a beta against a market index
     * and apply it to the premium; this module uses a single, stated
     * constant so every valuation this platform produces is transparent
     * about exactly one assumption instead of hiding it inside a fitted
     * coefficient. See {@code docs/spec-equity-research.md}.
     */
    public static double impliedDiscountRate(RatesCurve curve, double atYears, double equityRiskPremium) {
        if (!Double.isFinite(equityRiskPremium) || equityRiskPremium < 0.0) {
            throw new IllegalArgumentException(
                    "equityRiskPremium must be non-negative and finite, got: " + equityRiskPremium);
        }
        return curve.zeroRate(atYears) + equityRiskPremium;
    }

    /** The present value of the explicit FCF stream plus the discounted terminal value. */
    public static double presentValue(Inputs inputs) {
        double presentValue = 0.0;
        for (int year = 1; year <= inputs.explicitYears(); year++) {
            double fcf = inputs.fcfInYear(year);
            presentValue += fcf / Math.pow(1.0 + inputs.discountRate(), year);
        }
        presentValue += discountedTerminalValue(inputs);
        return presentValue;
    }

    /**
     * The Gordon-growth terminal value at the end of the explicit period --
     * next year's FCF divided by {@code (discountRate - terminalGrowthRate)}
     * -- discounted back to present value. Exposed separately so a reader
     * can see how much of the total value is carried by the terminal
     * assumption versus the explicit cash flows, the same transparency
     * {@code qrp-realassets}'s {@code discountedTerminalValue} provides.
     */
    public static double discountedTerminalValue(Inputs inputs) {
        double fcfAfterExplicitPeriod = inputs.fcfInYear(inputs.explicitYears() + 1);
        double terminalValue = fcfAfterExplicitPeriod / (inputs.discountRate() - inputs.terminalGrowthRate());
        return terminalValue / Math.pow(1.0 + inputs.discountRate(), inputs.explicitYears());
    }

    /** {@link #presentValue} divided by diluted shares outstanding: a fair-value-per-share estimate. */
    public static double valuePerShare(Inputs inputs, double dilutedSharesOutstanding) {
        if (!(dilutedSharesOutstanding > 0.0) || !Double.isFinite(dilutedSharesOutstanding)) {
            throw new IllegalArgumentException(
                    "dilutedSharesOutstanding must be positive and finite, got: " + dilutedSharesOutstanding);
        }
        return presentValue(inputs) / dilutedSharesOutstanding;
    }
}
