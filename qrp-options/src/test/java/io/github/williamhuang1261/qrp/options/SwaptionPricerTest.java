package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwaptionPricerTest {

    private static RatesCurve flatCurve(double rate) {
        return RatesCurve.of(List.of(new RatesCurve.Point(1.0, rate), new RatesCurve.Point(10.0, rate)));
    }

    private static SwapValuation fiveYearSwap() {
        // Fixed rate doesn't matter here: SwaptionPricer only reads the swap's
        // forward par rate and annuity, both independent of the coupon quoted
        // when the SwapValuation was constructed.
        return SwapValuation.of(1_000_000.0, 0.04, 5.0, flatCurve(0.04));
    }

    @Test
    @DisplayName("payer-receiver parity holds: Payer - Receiver = Annuity * (F - K)")
    void payerReceiverParityHolds() {
        SwapValuation swap = fiveYearSwap();
        double residual = SwaptionPricer.parityResidual(swap, 0.045, 2.0, 0.25, 0.04);
        assertEquals(0.0, residual, 1e-9);
    }

    @Test
    @DisplayName("parity holds away from at-the-money and across volatilities")
    void parityHoldsAcrossStrikesAndVols() {
        SwapValuation swap = fiveYearSwap();
        for (double strike : new double[] {0.02, 0.04, 0.06}) {
            for (double vol : new double[] {0.10, 0.30, 0.60}) {
                double residual = SwaptionPricer.parityResidual(swap, strike, 3.0, vol, 0.04);
                assertEquals(0.0, residual, 1e-8, "strike=" + strike + " vol=" + vol);
            }
        }
    }

    @Test
    @DisplayName("an at-the-money payer and receiver are worth the same when forward equals strike")
    void atTheMoneyPayerEqualsReceiver() {
        SwapValuation swap = fiveYearSwap();
        double forward = swap.parRate();

        double payer = SwaptionPricer.price(OptionType.CALL, swap, forward, 2.0, 0.25, 0.04);
        double receiver = SwaptionPricer.price(OptionType.PUT, swap, forward, 2.0, 0.25, 0.04);
        assertEquals(payer, receiver, 1e-9);
    }

    @Test
    @DisplayName("zero volatility prices the deterministic intrinsic value, scaled by the annuity")
    void zeroVolatilityPricesIntrinsicValue() {
        SwapValuation swap = fiveYearSwap();
        double forward = swap.parRate();
        double strike = forward - 0.01;

        double payer = SwaptionPricer.price(OptionType.CALL, swap, strike, 2.0, 0.0, 0.04);
        double expected = swap.annuity() * Math.max(forward - strike, 0.0);
        assertEquals(expected, payer, 1e-9);
    }

    @Test
    @DisplayName("a payer swaption gains value as volatility rises")
    void payerValueIncreasesWithVolatility() {
        SwapValuation swap = fiveYearSwap();
        double forward = swap.parRate();

        double lowVol = SwaptionPricer.price(OptionType.CALL, swap, forward, 2.0, 0.10, 0.04);
        double highVol = SwaptionPricer.price(OptionType.CALL, swap, forward, 2.0, 0.50, 0.04);
        assertTrue(highVol > lowVol);
    }

    @Test
    @DisplayName("rejects invalid arguments")
    void rejectsBadArguments() {
        SwapValuation swap = fiveYearSwap();
        assertThrows(IllegalArgumentException.class,
                () -> SwaptionPricer.price(null, swap, 0.04, 2.0, 0.2, 0.04));
        assertThrows(IllegalArgumentException.class,
                () -> SwaptionPricer.price(OptionType.CALL, null, 0.04, 2.0, 0.2, 0.04));
        assertThrows(IllegalArgumentException.class,
                () -> SwaptionPricer.price(OptionType.CALL, swap, -0.01, 2.0, 0.2, 0.04));
        assertThrows(IllegalArgumentException.class,
                () -> SwaptionPricer.price(OptionType.CALL, swap, 0.04, -1.0, 0.2, 0.04));
        assertThrows(IllegalArgumentException.class,
                () -> SwaptionPricer.price(OptionType.CALL, swap, 0.04, 2.0, -0.1, 0.04));
    }
}
