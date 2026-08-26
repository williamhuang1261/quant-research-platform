package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GreeksTest {

    private static BlackScholesInputs atTheMoney() {
        return BlackScholesInputs.equity(100.0, 100.0, 1.0, 0.20, 0.05);
    }

    /**
     * Central difference of the price with respect to one input.
     *
     * <p>Central rather than forward because the error is O(h^2) instead of
     * O(h), which is what makes a 1e-6 agreement achievable without the step
     * size having to be tuned per Greek.
     */
    private static double centralDifference(
            OptionType type,
            BlackScholesInputs base,
            java.util.function.DoubleFunction<BlackScholesInputs> bump,
            double h) {
        double up = BlackScholesMerton.price(type, bump.apply(h));
        double down = BlackScholesMerton.price(type, bump.apply(-h));
        return (up - down) / (2.0 * h);
    }

    @Test
    @DisplayName("delta matches a central difference in spot")
    void deltaMatchesFiniteDifference() {
        for (OptionType type : OptionType.values()) {
            for (double spot : new double[] {70.0, 95.0, 100.0, 110.0, 140.0}) {
                BlackScholesInputs in = atTheMoney().withSpot(spot);
                double numeric = centralDifference(type, in, h -> in.withSpot(spot + h), 1e-4);

                assertEquals(
                        numeric,
                        BlackScholesMerton.greeks(type, in).delta(),
                        1e-7,
                        type + " delta at spot " + spot);
            }
        }
    }

    @Test
    @DisplayName("gamma matches a central difference of delta")
    void gammaMatchesFiniteDifference() {
        for (double spot : new double[] {80.0, 100.0, 125.0}) {
            BlackScholesInputs in = atTheMoney().withSpot(spot);
            double h = 1e-3;
            double up = BlackScholesMerton.greeks(OptionType.CALL, in.withSpot(spot + h)).delta();
            double down = BlackScholesMerton.greeks(OptionType.CALL, in.withSpot(spot - h)).delta();

            assertEquals(
                    (up - down) / (2.0 * h),
                    BlackScholesMerton.greeks(OptionType.CALL, in).gamma(),
                    1e-8,
                    "gamma at spot " + spot);
        }
    }

    @Test
    @DisplayName("vega matches a central difference in volatility")
    void vegaMatchesFiniteDifference() {
        for (double vol : new double[] {0.10, 0.20, 0.55}) {
            BlackScholesInputs in = atTheMoney().withVolatility(vol);
            double numeric = centralDifference(OptionType.CALL, in, h -> in.withVolatility(vol + h), 1e-5);

            assertEquals(numeric, BlackScholesMerton.greeks(OptionType.CALL, in).vega(), 1e-6,
                    "vega at vol " + vol);
        }
    }

    @Test
    @DisplayName("theta matches minus a central difference in time to expiry")
    void thetaMatchesFiniteDifference() {
        // Theta is the derivative with respect to calendar time, and time to
        // expiry runs the other way, hence the sign flip. Getting this backwards
        // is the single most common error in a Greeks implementation.
        for (OptionType type : OptionType.values()) {
            for (double years : new double[] {0.25, 1.0, 3.0}) {
                BlackScholesInputs in = atTheMoney().withTimeToExpiry(years);
                double numeric =
                        centralDifference(type, in, h -> in.withTimeToExpiry(years + h), 1e-5);

                assertEquals(
                        -numeric,
                        BlackScholesMerton.greeks(type, in).theta(),
                        1e-6,
                        type + " theta at T=" + years);
            }
        }
    }

    @Test
    @DisplayName("rho matches a central difference in the risk-free rate")
    void rhoMatchesFiniteDifference() {
        for (OptionType type : OptionType.values()) {
            BlackScholesInputs in = atTheMoney();
            double numeric = centralDifference(type, in, h -> in.withRiskFreeRate(0.05 + h), 1e-6);

            assertEquals(numeric, BlackScholesMerton.greeks(type, in).rho(), 1e-5, type + " rho");
        }
    }

    @Test
    @DisplayName("call and put deltas differ by the carry factor")
    void deltasDifferByCarry() {
        BlackScholesInputs in =
                BlackScholesInputs.equityWithYield(105.0, 100.0, 0.5, 0.25, 0.04, 0.015);

        double callDelta = BlackScholesMerton.greeks(OptionType.CALL, in).delta();
        double putDelta = BlackScholesMerton.greeks(OptionType.PUT, in).delta();

        assertEquals(in.carryFactor(), callDelta - putDelta, 1e-12);
    }

    @Test
    @DisplayName("gamma and vega are shared by the call and the put")
    void gammaAndVegaAreTypeIndependent() {
        BlackScholesInputs in = atTheMoney();
        Greeks call = BlackScholesMerton.greeks(OptionType.CALL, in);
        Greeks put = BlackScholesMerton.greeks(OptionType.PUT, in);

        assertEquals(call.gamma(), put.gamma(), 1e-15);
        assertEquals(call.vega(), put.vega(), 1e-15);
    }

    @Test
    @DisplayName("signs are what a desk expects")
    void signsAreConventional() {
        BlackScholesInputs in = atTheMoney();
        Greeks call = BlackScholesMerton.greeks(OptionType.CALL, in);
        Greeks put = BlackScholesMerton.greeks(OptionType.PUT, in);

        assertTrue(call.delta() > 0.0 && call.delta() < 1.0, "call delta out of range");
        assertTrue(put.delta() < 0.0 && put.delta() > -1.0, "put delta out of range");
        assertTrue(call.gamma() > 0.0, "long gamma should be positive");
        assertTrue(call.vega() > 0.0, "long vega should be positive");
        assertTrue(call.theta() < 0.0, "a long at-the-money call should decay");
        assertTrue(call.rho() > 0.0, "a call gains from higher rates");
        assertTrue(put.rho() < 0.0, "a put loses from higher rates");
    }

    @Test
    @DisplayName("the degenerate case reports a step delta and no convexity")
    void degenerateGreeksAreTheLimit() {
        BlackScholesInputs itm = BlackScholesInputs.equity(120.0, 100.0, 0.0, 0.20, 0.05);
        Greeks greeks = BlackScholesMerton.greeks(OptionType.CALL, itm);

        assertEquals(1.0, greeks.delta(), 1e-12);
        assertEquals(0.0, greeks.gamma(), 1e-12);
        assertEquals(0.0, greeks.vega(), 1e-12);
        assertEquals(0.0, greeks.rho(), 1e-12);

        // Theta does NOT vanish here, and the first draft of this test wrongly
        // asserted that it did. An in-the-money call at the point of expiry still
        // holds one piece of time value: the strike has not been paid yet. That
        // deferral is worth K(1 - e^{-rT}), and it decays at -rK = -5 per year.
        // Only the diffusive part of theta dies with the volatility term.
        assertEquals(-0.05 * 100.0, greeks.theta(), 1e-12);

        Greeks otm = BlackScholesMerton.greeks(
                OptionType.CALL, BlackScholesInputs.equity(80.0, 100.0, 0.0, 0.20, 0.05));
        assertEquals(0.0, otm.delta(), 1e-12);
        // Out of the money there is no strike to defer, so theta really is zero.
        assertEquals(0.0, otm.theta(), 1e-12);
    }

    @Test
    @DisplayName("theta approaches its expiry limit continuously from the diffusive side")
    void thetaLimitIsApproachedContinuously() {
        // The guard against fixing the previous test by pinning whatever the code
        // happened to print: -rK has to be where the diffusive branch is heading,
        // not just what the degenerate branch returns.
        BlackScholesInputs nearExpiry =
                BlackScholesInputs.equity(120.0, 100.0, 1e-8, 0.20, 0.05);

        assertEquals(
                -0.05 * 100.0,
                BlackScholesMerton.greeks(OptionType.CALL, nearExpiry).theta(),
                1e-4);
    }

    @Test
    @DisplayName("the reporting conversions divide by what they say they divide by")
    void reportingUnitsConvert() {
        Greeks greeks = new Greeks(0.5, 0.02, 39.0, -6.4, 45.0);

        assertEquals(0.39, greeks.vegaPerVolPoint(), 1e-12);
        assertEquals(-6.4 / 365.0, greeks.thetaPerCalendarDay(), 1e-12);
        assertEquals(0.0045, greeks.rhoPerBasisPoint(), 1e-12);
        assertEquals(1.0, greeks.scaled(2.0).delta(), 1e-12);
    }
}
