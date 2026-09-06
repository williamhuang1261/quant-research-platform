package io.github.williamhuang1261.qrp.options;

/**
 * Plain-vanilla fixed-vs-floating interest rate swap pricing off a single
 * {@link RatesCurve}, continuously compounded throughout to stay consistent
 * with {@link BondAnalytics} and {@link BlackScholesInputs}.
 *
 * <p><b>Single-curve simplification, stated plainly.</b> A real desk projects
 * the floating leg off one curve (e.g. SOFR) and discounts off another (the
 * OIS curve), and the two disagree by a basis spread. This module has one
 * curve, so both legs are priced off it. Under that assumption the floating
 * leg of a swap that resets today has a closed form that needs no forecast of
 * future fixings at all: it is worth {@code notional * (1 - DF(T))}, the same
 * identity a floating-rate note at par reduces to. See {@code
 * docs/spec-swaps.md} for the derivation and a worked example.
 *
 * <p>The fixed leg is priced the same way {@link BondAnalytics} prices a
 * bond's coupon stream: a semi-annual schedule of {@code notional * fixedRate
 * * accrual} cash flows, discounted off the curve, reusing {@link
 * BondAnalytics#semiAnnualCashflowTimes}.
 */
public final class SwapValuation {

    private final double notional;
    private final double fixedRate;
    private final double[] fixedLegTimes;
    private final RatesCurve curve;

    private SwapValuation(double notional, double fixedRate, double[] fixedLegTimes, RatesCurve curve) {
        this.notional = notional;
        this.fixedRate = fixedRate;
        this.fixedLegTimes = fixedLegTimes;
        this.curve = curve;
    }

    /**
     * @param notional  swap notional, must be positive
     * @param fixedRate the fixed leg's annual coupon rate as a decimal
     * @param years     swap tenor; the fixed leg pays semi-annually over this span
     * @param curve     the single curve both legs are priced off
     */
    public static SwapValuation of(double notional, double fixedRate, double years, RatesCurve curve) {
        requirePositive("notional", notional);
        requireFinite("fixedRate", fixedRate);
        if (curve == null) {
            throw new IllegalArgumentException("curve must not be null");
        }
        double[] times = BondAnalytics.semiAnnualCashflowTimes(years);
        return new SwapValuation(notional, fixedRate, times, curve);
    }

    /** The swap's tenor: the final fixed-leg cash flow's time, in years. */
    public double years() {
        return fixedLegTimes[fixedLegTimes.length - 1];
    }

    /** PV of the fixed leg: the coupon schedule discounted off the curve. */
    public double fixedLegPv() {
        double accrual = 1.0 / 2.0;
        double couponPerPeriod = fixedRate * accrual * notional;
        double pv = 0.0;
        for (double time : fixedLegTimes) {
            pv += couponPerPeriod * curve.discountFactor(time);
        }
        return pv;
    }

    /**
     * PV of the floating leg, single-curve: {@code notional * (1 - DF(T))}.
     * See the class javadoc for the identity this collapses from.
     */
    public double floatingLegPv() {
        return notional * (1.0 - curve.discountFactor(years()));
    }

    /**
     * PV to the fixed-rate payer: receives floating, pays fixed, so
     * {@code floatingLegPv - fixedLegPv}.
     */
    public double payerPv() {
        return floatingLegPv() - fixedLegPv();
    }

    /** The level/annuity: sum of {@code accrual * DF(t)} over the fixed leg, the value of a 1-rate-unit coupon stream. */
    public double annuity() {
        double accrual = 1.0 / 2.0;
        double level = 0.0;
        for (double time : fixedLegTimes) {
            level += accrual * curve.discountFactor(time);
        }
        return level;
    }

    /**
     * The par swap rate: the fixed rate at which {@code payerPv() == 0}.
     * Solves {@code fixedRate * annuity * notional = notional * (1 - DF(T))}
     * directly, since both legs are linear in the fixed rate.
     */
    public double parRate() {
        return (1.0 - curve.discountFactor(years())) / annuity();
    }

    /**
     * Dollar value of a one basis point (0.0001) parallel move in the curve's
     * zero rates, on the payer's PV, the same finite-difference definition
     * {@link BondAnalytics#dv01} uses for a bond.
     *
     * <p>A parallel 1bp bump to every tenor's zero rate {@code r(t)} scales
     * that tenor's discount factor by exactly {@code e^{-0.0001 t}}, whatever
     * the curve's own shape between quoted points: {@code e^{-(r(t)+0.0001)t}
     * = e^{-r(t)t} * e^{-0.0001 t}}. That identity is what lets this reprice
     * without touching the curve's quoted points at all.
     */
    public double dv01() {
        double bumpedFixedLegPv = 0.0;
        double accrual = 1.0 / 2.0;
        double couponPerPeriod = fixedRate * accrual * notional;
        for (double time : fixedLegTimes) {
            bumpedFixedLegPv += couponPerPeriod * curve.discountFactor(time) * Math.exp(-0.0001 * time);
        }
        double bumpedFloatingLegPv = notional * (1.0 - curve.discountFactor(years()) * Math.exp(-0.0001 * years()));
        double bumpedPayerPv = bumpedFloatingLegPv - bumpedFixedLegPv;
        return bumpedPayerPv - payerPv();
    }

    private static void requirePositive(String name, double value) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be positive and finite, got: " + value);
        }
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got: " + value);
        }
    }
}
