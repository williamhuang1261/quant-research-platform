package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import io.github.williamhuang1261.qrp.core.AssetClass;
import io.github.williamhuang1261.qrp.core.Instrument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlackScholesMertonTest {

    /** S=100, K=100, T=1, sigma=20 %, r=5 %, no dividend: the standard worked example. */
    private static BlackScholesInputs atTheMoney() {
        return BlackScholesInputs.equity(100.0, 100.0, 1.0, 0.20, 0.05);
    }

    @Test
    @DisplayName("prices the textbook at-the-money example")
    void matchesTheTextbookExample() {
        BlackScholesInputs in = atTheMoney();

        // Hull, Options, Futures and Other Derivatives: c = 10.4506, p = 5.5735.
        assertEquals(10.450583572185565, BlackScholesMerton.price(OptionType.CALL, in), 1e-9);
        assertEquals(5.573526022256971, BlackScholesMerton.price(OptionType.PUT, in), 1e-9);
    }

    @Test
    @DisplayName("put-call parity holds by algebra, not by luck")
    void parityHolds() {
        BlackScholesInputs[] cases = {
            atTheMoney(),
            BlackScholesInputs.equityWithYield(87.5, 100.0, 0.25, 0.45, 0.03, 0.02),
            BlackScholesInputs.future(4200.0, 4300.0, 0.75, 0.18, 0.045),
            BlackScholesInputs.fx(1.09, 1.15, 2.0, 0.09, 0.04, 0.025),
            BlackScholesInputs.equity(100.0, 100.0, 1.0, 0.20, -0.005),
        };

        for (BlackScholesInputs in : cases) {
            assertEquals(0.0, BlackScholesMerton.parityResidual(in), 1e-12, "parity broke for " + in);
        }
    }

    @Test
    @DisplayName("at zero volatility the option is worth discounted intrinsic on the forward")
    void zeroVolatilityPricesTheForward() {
        // Carry pushes a 100 spot to 105.13 in a year, so a 100-strike call is
        // worth something even with no diffusion at all.
        BlackScholesInputs in = BlackScholesInputs.equity(100.0, 100.0, 1.0, 0.0, 0.05);

        double expected = Math.exp(-0.05) * (100.0 * Math.exp(0.05) - 100.0);
        assertEquals(expected, BlackScholesMerton.price(OptionType.CALL, in), 1e-12);
        assertEquals(0.0, BlackScholesMerton.price(OptionType.PUT, in), 1e-12);
        assertEquals(0.0, BlackScholesMerton.parityResidual(in), 1e-12);
    }

    @Test
    @DisplayName("at expiry the option is worth intrinsic on spot")
    void zeroTimePricesIntrinsic() {
        BlackScholesInputs itm = BlackScholesInputs.equity(120.0, 100.0, 0.0, 0.30, 0.05);
        BlackScholesInputs otm = BlackScholesInputs.equity(80.0, 100.0, 0.0, 0.30, 0.05);

        assertEquals(20.0, BlackScholesMerton.price(OptionType.CALL, itm), 1e-12);
        assertEquals(0.0, BlackScholesMerton.price(OptionType.PUT, itm), 1e-12);
        assertEquals(0.0, BlackScholesMerton.price(OptionType.CALL, otm), 1e-12);
        assertEquals(20.0, BlackScholesMerton.price(OptionType.PUT, otm), 1e-12);
    }

    @Test
    @DisplayName("the zero-volatility limit is approached continuously")
    void approachesTheZeroVolatilityLimitSmoothly() {
        // The degenerate branch has to agree with the diffusive one at the seam,
        // because the implied-vol solver brackets from zero and will sit here.
        BlackScholesInputs limit = BlackScholesInputs.equity(105.0, 100.0, 1.0, 0.0, 0.05);
        double atZero = BlackScholesMerton.price(OptionType.CALL, limit);
        double nearZero = BlackScholesMerton.price(OptionType.CALL, limit.withVolatility(1e-8));

        assertEquals(atZero, nearZero, 1e-9);
    }

    @Test
    @DisplayName("an option on a future is Black 1976: zero carry")
    void futureOptionHasZeroCarry() {
        BlackScholesInputs in = BlackScholesInputs.future(100.0, 100.0, 1.0, 0.20, 0.05);

        assertEquals(0.0, in.carryRate(), 1e-15);
        assertEquals(100.0, in.forward(), 1e-12);
        // With zero carry the forward is at the money, so call and put are equal.
        assertEquals(
                BlackScholesMerton.price(OptionType.CALL, in),
                BlackScholesMerton.price(OptionType.PUT, in),
                1e-12);
    }

    @Test
    @DisplayName("price rises with volatility and with spot for a call")
    void isMonotoneWhereItShouldBe() {
        BlackScholesInputs in = atTheMoney();

        double previousVol = -1.0;
        for (double vol = 0.0; vol < 1.5; vol += 0.05) {
            double price = BlackScholesMerton.price(OptionType.CALL, in.withVolatility(vol));
            assertTrue(price > previousVol, "call price fell as volatility rose to " + vol);
            previousVol = price;
        }

        double previousSpot = -1.0;
        for (double spot = 50.0; spot <= 150.0; spot += 5.0) {
            double price = BlackScholesMerton.price(OptionType.CALL, in.withSpot(spot));
            assertTrue(price > previousSpot, "call price fell as spot rose to " + spot);
            previousSpot = price;
        }
    }

    @Test
    @DisplayName("price stays inside its no-arbitrage bounds")
    void respectsNoArbitrageBounds() {
        for (double spot = 60.0; spot <= 140.0; spot += 10.0) {
            BlackScholesInputs in = atTheMoney().withSpot(spot);
            double call = BlackScholesMerton.price(OptionType.CALL, in);

            double lower = Math.max(in.spot() * in.carryFactor() - in.strike() * in.discountFactor(), 0.0);
            double upper = in.spot() * in.carryFactor();

            assertTrue(call >= lower - 1e-12, "call " + call + " below its floor " + lower);
            assertTrue(call <= upper + 1e-12, "call " + call + " above spot " + upper);
        }
    }

    @Test
    @DisplayName("deep in and out of the money converge on their limits")
    void convergesAtTheExtremes() {
        BlackScholesInputs deepItm = BlackScholesInputs.equity(1000.0, 100.0, 1.0, 0.20, 0.05);
        BlackScholesInputs deepOtm = BlackScholesInputs.equity(1.0, 100.0, 1.0, 0.20, 0.05);

        double forwardValue = 1000.0 - 100.0 * Math.exp(-0.05);
        assertEquals(forwardValue, BlackScholesMerton.price(OptionType.CALL, deepItm), 1e-6);
        assertEquals(0.0, BlackScholesMerton.price(OptionType.CALL, deepOtm), 1e-9);
    }

    @Test
    @DisplayName("d1 refuses to answer where it diverges")
    void d1RejectsTheDegenerateCase() {
        BlackScholesInputs zeroVol = BlackScholesInputs.equity(100.0, 100.0, 1.0, 0.0, 0.05);
        BlackScholesInputs expired = BlackScholesInputs.equity(100.0, 100.0, 0.0, 0.20, 0.05);

        assertThrows(IllegalArgumentException.class, () -> BlackScholesMerton.d1(zeroVol));
        assertThrows(IllegalArgumentException.class, () -> BlackScholesMerton.d1(expired));
    }

    @Test
    @DisplayName("N(d2) is the risk-neutral probability of finishing in the money")
    void d2IsTheExerciseProbability() {
        // A far out-of-the-money call is unlikely to be exercised; a deep
        // in-the-money one nearly certain. This is the sanity check that d1 and
        // d2 are not transposed, which is the classic sign error here.
        BlackScholesInputs otm = BlackScholesInputs.equity(100.0, 200.0, 1.0, 0.20, 0.05);
        BlackScholesInputs itm = BlackScholesInputs.equity(200.0, 100.0, 1.0, 0.20, 0.05);

        assertTrue(BlackScholesMerton.d2(otm) < -2.0);
        assertTrue(BlackScholesMerton.d2(itm) > 2.0);
        assertTrue(BlackScholesMerton.d1(otm) > BlackScholesMerton.d2(otm));
    }

    @Test
    @DisplayName("refuses to price American exercise as European")
    void refusesAmericanExercise() {
        Instrument underlying = new Instrument("SYNA", "USD", AssetClass.EQUITY);
        OptionContract american = OptionContract.american(
                underlying, OptionType.PUT, 100.0, LocalDate.of(2027, 1, 15));

        assertThrows(
                IllegalArgumentException.class,
                () -> BlackScholesMerton.priceContract(american, atTheMoney()));
    }

    @Test
    @DisplayName("rejects impossible inputs at the boundary")
    void rejectsImpossibleInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BlackScholesInputs.equity(-1.0, 100.0, 1.0, 0.2, 0.05));
        assertThrows(
                IllegalArgumentException.class,
                () -> BlackScholesInputs.equity(100.0, 0.0, 1.0, 0.2, 0.05));
        assertThrows(
                IllegalArgumentException.class,
                () -> BlackScholesInputs.equity(100.0, 100.0, -0.5, 0.2, 0.05));
        assertThrows(
                IllegalArgumentException.class,
                () -> BlackScholesInputs.equity(100.0, 100.0, 1.0, -0.2, 0.05));
        assertThrows(
                IllegalArgumentException.class,
                () -> BlackScholesInputs.equity(100.0, 100.0, 1.0, 0.2, Double.NaN));
    }
}
