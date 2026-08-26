package io.github.williamhuang1261.qrp.options;

/**
 * Plain-vanilla fixed-coupon bond pricing and risk, priced with continuous
 * compounding throughout.
 *
 * <p>A real Treasury desk quotes and prices on a <b>semi-annual
 * bond-equivalent yield</b> -- discrete compounding, not continuous. This class
 * uses continuous compounding instead, deliberately, to stay consistent with
 * every other rate in this module: {@link BlackScholesInputs}'s {@code r} and
 * {@link RatesCurve#discountFactor} are both continuously compounded, and
 * mixing conventions inside one platform is a worse source of a wrong number
 * than the convention itself. The gap between the two conventions is on the
 * order of {@code y^2/2} per year of duration, a few basis points for a
 * 10-year bond at a 4-5% yield.
 *
 * <p>One consequence of continuous compounding is worth stating because it
 * surprises people coming from a bond-market background: <b>Macaulay duration
 * and modified duration are the same number here.</b> Under discrete
 * compounding, modified duration is Macaulay duration divided by
 * {@code 1 + y/frequency}. Under continuous compounding that divisor is
 * {@code 1} (see {@link #macaulayDuration}'s derivation), so this class exposes
 * one method, not two.
 */
public final class BondAnalytics {

    private BondAnalytics() {
    }

    /** Semi-annual coupon dates for a bond maturing in {@code years}, as a convenience for the common case. */
    public static double[] semiAnnualCashflowTimes(double years) {
        if (!(years > 0.0) || !Double.isFinite(years)) {
            throw new IllegalArgumentException("years must be positive and finite, got: " + years);
        }
        int periods = (int) Math.round(years * 2.0);
        if (periods < 1) {
            throw new IllegalArgumentException("years too short for a semi-annual coupon: " + years);
        }
        double[] times = new double[periods];
        for (int i = 0; i < periods; i++) {
            times[i] = years * (i + 1) / periods;
        }
        return times;
    }

    /** The coupon-then-principal cash flow at each of {@code times}: coupon at every date, face added at the last. */
    public static double[] semiAnnualCashflows(double couponRateAnnual, double faceValue, int periods) {
        double[] cashflows = new double[periods];
        double couponPerPeriod = couponRateAnnual / 2.0 * faceValue;
        for (int i = 0; i < periods; i++) {
            cashflows[i] = couponPerPeriod;
        }
        cashflows[periods - 1] += faceValue;
        return cashflows;
    }

    /** Convenience: price a standard semi-annual-coupon bond at a flat yield. */
    public static double price(double couponRateAnnual, double faceValue, double years, double yield) {
        double[] times = semiAnnualCashflowTimes(years);
        double[] cashflows = semiAnnualCashflows(couponRateAnnual, faceValue, times.length);
        return price(times, cashflows, yield);
    }

    /** Present value of arbitrary cash flows at a flat continuously compounded yield. */
    public static double price(double[] times, double[] cashflows, double yield) {
        requireMatchedArrays(times, cashflows);
        double price = 0.0;
        for (int i = 0; i < times.length; i++) {
            price += cashflows[i] * Math.exp(-yield * times[i]);
        }
        return price;
    }

    /** Present value of the same cash flows discounted off a real curve instead of a flat yield. */
    public static double priceFromCurve(double[] times, double[] cashflows, RatesCurve curve) {
        requireMatchedArrays(times, cashflows);
        if (curve == null) {
            throw new IllegalArgumentException("curve must not be null");
        }
        double price = 0.0;
        for (int i = 0; i < times.length; i++) {
            price += cashflows[i] * curve.discountFactor(times[i]);
        }
        return price;
    }

    /**
     * Macaulay duration: the cash-flow-weighted average time to receipt,
     * weighted by each flow's present value.
     *
     * <p>Under continuous compounding this equals {@code -dPrice/dy / Price}
     * directly: {@code Price(y) = sum CF_i e^{-y t_i}}, so
     * {@code dPrice/dy = -sum t_i CF_i e^{-y t_i}}, and dividing by
     * {@code Price} gives exactly the weighted-average-time definition below --
     * modified duration needs no separate {@code 1/(1+y/f)} adjustment.
     */
    public static double macaulayDuration(double[] times, double[] cashflows, double yield) {
        requireMatchedArrays(times, cashflows);
        double price = 0.0;
        double weightedTime = 0.0;
        for (int i = 0; i < times.length; i++) {
            double presentValue = cashflows[i] * Math.exp(-yield * times[i]);
            price += presentValue;
            weightedTime += times[i] * presentValue;
        }
        if (price <= 0.0) {
            throw new IllegalStateException("non-positive price cannot support a duration: " + price);
        }
        return weightedTime / price;
    }

    /** Equal to {@link #macaulayDuration} under continuous compounding; see the class javadoc. */
    public static double modifiedDuration(double[] times, double[] cashflows, double yield) {
        return macaulayDuration(times, cashflows, yield);
    }

    /**
     * Dollar value of a one basis point (0.0001) move in yield: the price
     * change a desk actually risk-manages against, in currency, not percent.
     */
    public static double dv01(double[] times, double[] cashflows, double yield) {
        double price = price(times, cashflows, yield);
        double duration = modifiedDuration(times, cashflows, yield);
        return price * duration * 0.0001;
    }

    /**
     * Convexity: the curvature of price with respect to yield,
     * {@code (1/Price) * d^2Price/dy^2 = sum t_i^2 CF_i e^{-y t_i} / Price}. A
     * positive number, since price is convex in yield for a plain bond with no
     * embedded option -- the reason duration alone understates the price gain
     * from a large rate drop and overstates the loss from a large rate rise.
     */
    public static double convexity(double[] times, double[] cashflows, double yield) {
        requireMatchedArrays(times, cashflows);
        double price = 0.0;
        double weightedTimeSquared = 0.0;
        for (int i = 0; i < times.length; i++) {
            double presentValue = cashflows[i] * Math.exp(-yield * times[i]);
            price += presentValue;
            weightedTimeSquared += times[i] * times[i] * presentValue;
        }
        if (price <= 0.0) {
            throw new IllegalStateException("non-positive price cannot support a convexity: " + price);
        }
        return weightedTimeSquared / price;
    }

    private static void requireMatchedArrays(double[] times, double[] cashflows) {
        if (times == null || cashflows == null) {
            throw new IllegalArgumentException("times and cashflows must not be null");
        }
        if (times.length != cashflows.length || times.length == 0) {
            throw new IllegalArgumentException(
                    "times and cashflows must be the same non-zero length, got "
                            + times.length + " and " + cashflows.length);
        }
    }
}
