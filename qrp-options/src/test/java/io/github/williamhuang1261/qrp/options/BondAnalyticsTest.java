package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BondAnalyticsTest {

    @Test
    @DisplayName("a zero-coupon bond prices to face value discounted at the flat yield")
    void zeroCouponBondPricesCorrectly() {
        double[] times = {5.0};
        double[] cashflows = {1000.0};

        double price = BondAnalytics.price(times, cashflows, 0.05);

        assertEquals(1000.0 * Math.exp(-0.05 * 5.0), price, 1e-9);
    }

    @Test
    @DisplayName("a zero-coupon bond's Macaulay duration equals its own maturity")
    void zeroCouponDurationEqualsMaturity() {
        double[] times = {7.0};
        double[] cashflows = {1000.0};

        assertEquals(7.0, BondAnalytics.macaulayDuration(times, cashflows, 0.04), 1e-12);
        assertEquals(7.0, BondAnalytics.modifiedDuration(times, cashflows, 0.04), 1e-12);
    }

    @Test
    @DisplayName("a coupon bond's duration is strictly less than its maturity")
    void couponBondDurationIsBelowMaturity() {
        double[] times = BondAnalytics.semiAnnualCashflowTimes(10.0);
        double[] cashflows = BondAnalytics.semiAnnualCashflows(0.05, 1000.0, times.length);

        double duration = BondAnalytics.macaulayDuration(times, cashflows, 0.045);

        assertTrue(duration > 0.0 && duration < 10.0, "duration " + duration + " should be under maturity");
    }

    @Test
    @DisplayName("a 30-year zero has more duration and convexity than a 5-year zero")
    void longerMaturityMeansMoreDurationAndConvexity() {
        double[] shortTimes = {5.0};
        double[] shortCashflows = {1000.0};
        double[] longTimes = {30.0};
        double[] longCashflows = {1000.0};

        double shortDuration = BondAnalytics.macaulayDuration(shortTimes, shortCashflows, 0.045);
        double longDuration = BondAnalytics.macaulayDuration(longTimes, longCashflows, 0.045);
        assertTrue(longDuration > shortDuration);

        double shortConvexity = BondAnalytics.convexity(shortTimes, shortCashflows, 0.045);
        double longConvexity = BondAnalytics.convexity(longTimes, longCashflows, 0.045);
        assertTrue(longConvexity > shortConvexity);
    }

    @Test
    @DisplayName("duration matches a central finite difference of price with respect to yield")
    void durationMatchesFiniteDifference() {
        double[] times = BondAnalytics.semiAnnualCashflowTimes(10.0);
        double[] cashflows = BondAnalytics.semiAnnualCashflows(0.04, 1000.0, times.length);
        double yield = 0.045;
        double h = 1e-5;

        double priceUp = BondAnalytics.price(times, cashflows, yield + h);
        double priceDown = BondAnalytics.price(times, cashflows, yield - h);
        double priceHere = BondAnalytics.price(times, cashflows, yield);
        double numericModifiedDuration = -(priceUp - priceDown) / (2.0 * h) / priceHere;

        assertEquals(numericModifiedDuration, BondAnalytics.modifiedDuration(times, cashflows, yield), 1e-6);
    }

    @Test
    @DisplayName("convexity matches a central finite difference of the price's second derivative")
    void convexityMatchesFiniteDifference() {
        double[] times = BondAnalytics.semiAnnualCashflowTimes(10.0);
        double[] cashflows = BondAnalytics.semiAnnualCashflows(0.04, 1000.0, times.length);
        double yield = 0.045;
        double h = 1e-4;

        double priceUp = BondAnalytics.price(times, cashflows, yield + h);
        double priceDown = BondAnalytics.price(times, cashflows, yield - h);
        double priceHere = BondAnalytics.price(times, cashflows, yield);
        double numericConvexity = (priceUp - 2.0 * priceHere + priceDown) / (h * h) / priceHere;

        assertEquals(numericConvexity, BondAnalytics.convexity(times, cashflows, yield), 1e-4);
    }

    @Test
    @DisplayName("DV01 approximates the price change from a real one basis point yield bump")
    void dv01ApproximatesARealBasisPointMove() {
        double[] times = BondAnalytics.semiAnnualCashflowTimes(10.0);
        double[] cashflows = BondAnalytics.semiAnnualCashflows(0.045, 1000.0, times.length);
        double yield = 0.045;

        double priceHere = BondAnalytics.price(times, cashflows, yield);
        double priceAfterOneBp = BondAnalytics.price(times, cashflows, yield + 0.0001);
        double actualChange = priceHere - priceAfterOneBp;

        assertEquals(actualChange, BondAnalytics.dv01(times, cashflows, yield), 1e-2);
    }

    @Test
    @DisplayName("pricing off a flat curve matches pricing at that curve's flat yield")
    void pricingOffAFlatCurveMatchesFlatYield() {
        double[] times = BondAnalytics.semiAnnualCashflowTimes(10.0);
        double[] cashflows = BondAnalytics.semiAnnualCashflows(0.04, 1000.0, times.length);
        RatesCurve flatCurve = RatesCurve.of(List.of(
                new RatesCurve.Point(1.0, 0.045), new RatesCurve.Point(30.0, 0.045)));

        double priceFromCurve = BondAnalytics.priceFromCurve(times, cashflows, flatCurve);
        double priceFromFlatYield = BondAnalytics.price(times, cashflows, 0.045);

        assertEquals(priceFromFlatYield, priceFromCurve, 1e-9);
    }

    @Test
    @DisplayName("the convenience semi-annual price() overload matches the explicit-cashflow form")
    void convenienceOverloadMatchesExplicitForm() {
        double viaConvenience = BondAnalytics.price(0.05, 1000.0, 10.0, 0.045);

        double[] times = BondAnalytics.semiAnnualCashflowTimes(10.0);
        double[] cashflows = BondAnalytics.semiAnnualCashflows(0.05, 1000.0, times.length);
        double viaExplicit = BondAnalytics.price(times, cashflows, 0.045);

        assertEquals(viaExplicit, viaConvenience, 1e-12);
    }

    @Test
    @DisplayName("rejects mismatched or empty arrays")
    void rejectsMismatchedArrays() {
        assertThrows(IllegalArgumentException.class, () -> BondAnalytics.price(new double[] {1.0}, new double[] {1.0, 2.0}, 0.04));
        assertThrows(IllegalArgumentException.class, () -> BondAnalytics.price(new double[0], new double[0], 0.04));
        assertThrows(IllegalArgumentException.class, () -> BondAnalytics.price(null, new double[] {1.0}, 0.04));
    }
}
