package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImpliedVolatilityTest {

    private static BlackScholesInputs atTheMoney(double volatility) {
        return BlackScholesInputs.equity(100.0, 100.0, 1.0, volatility, 0.05);
    }

    @Test
    @DisplayName("price -> implied vol -> price round-trips to 1e-10")
    void roundTripsThroughPricing() {
        for (double trueVol : new double[] {0.05, 0.15, 0.20, 0.45, 0.90, 1.50}) {
            for (OptionType type : OptionType.values()) {
                BlackScholesInputs in = atTheMoney(trueVol);
                double price = BlackScholesMerton.price(type, in);

                double solved = ImpliedVolatility.solve(type, in.withVolatility(0.2), price);
                double repriced = BlackScholesMerton.price(type, in.withVolatility(solved));

                assertEquals(price, repriced, 1e-10, type + " at trueVol=" + trueVol);
            }
        }
    }

    @Test
    @DisplayName("recovers the exact volatility it was priced at")
    void recoversTheGeneratingVolatility() {
        for (double trueVol : new double[] {0.08, 0.25, 0.60, 1.20}) {
            BlackScholesInputs in = atTheMoney(trueVol);
            double price = BlackScholesMerton.price(OptionType.CALL, in);

            double solved = ImpliedVolatility.solve(OptionType.CALL, in, price);

            assertEquals(trueVol, solved, 1e-8, "at trueVol=" + trueVol);
        }
    }

    @Test
    @DisplayName("far out-of-the-money wings force the bisection fallback and still converge")
    void deepWingsStillConverge() {
        // Far OTM, vega is tiny and Newton is expected to hand off to bisection;
        // the test only cares that SOME correct answer comes back.
        BlackScholesInputs deepOtmPut = BlackScholesInputs.equity(100.0, 20.0, 0.1, 0.60, 0.05);
        double price = BlackScholesMerton.price(OptionType.PUT, deepOtmPut);

        double solved = ImpliedVolatility.solve(OptionType.PUT, deepOtmPut, price);
        double repriced = BlackScholesMerton.price(OptionType.PUT, deepOtmPut.withVolatility(solved));

        assertEquals(price, repriced, 1e-8);
    }

    @Test
    @DisplayName("a price above the no-arbitrage ceiling is refused rather than guessed")
    void refusesAnImpossiblePrice() {
        BlackScholesInputs in = atTheMoney(0.2);
        // The ceiling is the price at 500% volatility; ask for more than that.
        double ceiling = BlackScholesMerton.price(OptionType.CALL, in.withVolatility(5.0));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ImpliedVolatility.solve(OptionType.CALL, in, ceiling + 1.0));
        assertTrue(exception.getMessage().contains("no-arbitrage"));
    }

    @Test
    @DisplayName("a negative price is refused")
    void refusesNegativePrice() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ImpliedVolatility.solve(OptionType.CALL, atTheMoney(0.2), -1.0));
    }

    @Test
    @DisplayName("zero price implies the floor volatility, not an error")
    void zeroPriceImpliesTheFloor() {
        // A deep OTM option can legitimately be quoted at zero; that has an
        // answer (the floor), it is not the same failure as a price above the
        // ceiling.
        BlackScholesInputs deepOtm = BlackScholesInputs.equity(100.0, 500.0, 0.05, 0.2, 0.05);
        double solved = ImpliedVolatility.solve(OptionType.CALL, deepOtm, 0.0);

        assertTrue(solved >= 1e-6 && solved <= 5.0);
    }
}
