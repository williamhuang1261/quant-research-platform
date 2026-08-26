package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class MonteCarloOptionPricerTest {

    private static BlackScholesInputs atTheMoney() {
        return BlackScholesInputs.equity(100.0, 100.0, 1.0, 0.20, 0.05);
    }

    @Test
    @DisplayName("the 95% confidence interval covers the analytic price")
    void confidenceIntervalCoversTheClosedForm() {
        BlackScholesInputs in = atTheMoney();
        double closedForm = BlackScholesMerton.price(OptionType.CALL, in);

        MonteCarloOptionPricer.Result result =
                MonteCarloOptionPricer.price(OptionType.CALL, in, 200_000, 42L);

        assertTrue(
                closedForm >= result.confidenceLow() && closedForm <= result.confidenceHigh(),
                "closed form " + closedForm + " outside CI [" + result.confidenceLow() + ", "
                        + result.confidenceHigh() + "]");
        // A loose sanity bound on the point estimate itself, independent of the
        // CI machinery being tested above.
        assertEquals(closedForm, result.price(), 0.15);
    }

    @Test
    @DisplayName("the 95% interval covers the closed form across ten independent seeds")
    void confidenceIntervalCoversAcrossManySeeds() {
        // A single seed passing the CI check could be luck; a coverage property
        // should hold across many. With a true 95% interval, "misses on 0 of 10"
        // is unremarkable, so no seed here should miss by more than a hair.
        BlackScholesInputs in = atTheMoney();
        double closedForm = BlackScholesMerton.price(OptionType.CALL, in);

        for (long seed = 0; seed < 10; seed++) {
            MonteCarloOptionPricer.Result result =
                    MonteCarloOptionPricer.price(OptionType.CALL, in, 50_000, seed * 1_000 + 7);
            assertTrue(
                    closedForm >= result.confidenceLow() - 0.05
                            && closedForm <= result.confidenceHigh() + 0.05,
                    "seed " + seed + ": closed form " + closedForm + " outside CI");
        }
    }

    @Test
    @DisplayName("standard error shrinks as path count grows")
    void standardErrorShrinksWithPaths() {
        BlackScholesInputs in = atTheMoney();
        double previousError = Double.POSITIVE_INFINITY;

        for (int pairs : new int[] {1_000, 10_000, 100_000}) {
            MonteCarloOptionPricer.Result result =
                    MonteCarloOptionPricer.price(OptionType.CALL, in, pairs, 7L);
            assertTrue(
                    result.standardError() < previousError,
                    "standard error did not shrink at pairs=" + pairs);
            previousError = result.standardError();
        }
    }

    @RepeatedTest(5)
    @DisplayName("the same seed reproduces the same result exactly")
    void sameSeedReproducesExactly() {
        BlackScholesInputs in = atTheMoney();
        MonteCarloOptionPricer.Result first =
                MonteCarloOptionPricer.price(OptionType.PUT, in, 5_000, 123L);
        MonteCarloOptionPricer.Result second =
                MonteCarloOptionPricer.price(OptionType.PUT, in, 5_000, 123L);

        assertEquals(first.price(), second.price(), 0.0);
        assertEquals(first.standardError(), second.standardError(), 0.0);
    }

    @Test
    @DisplayName("different seeds give different paths")
    void differentSeedsGiveDifferentResults() {
        BlackScholesInputs in = atTheMoney();
        MonteCarloOptionPricer.Result a = MonteCarloOptionPricer.price(OptionType.CALL, in, 2_000, 1L);
        MonteCarloOptionPricer.Result b = MonteCarloOptionPricer.price(OptionType.CALL, in, 2_000, 2L);

        assertTrue(a.price() != b.price(), "two different seeds produced an identical price");
    }

    @Test
    @DisplayName("degenerate inputs price deterministically with zero standard error")
    void degenerateInputsAreDeterministic() {
        BlackScholesInputs zeroVol = BlackScholesInputs.equity(105.0, 100.0, 1.0, 0.0, 0.05);

        MonteCarloOptionPricer.Result result =
                MonteCarloOptionPricer.price(OptionType.CALL, zeroVol, 1_000, 99L);

        assertEquals(BlackScholesMerton.price(OptionType.CALL, zeroVol), result.price(), 1e-9);
        assertEquals(0.0, result.standardError(), 1e-12);
    }

    @Test
    @DisplayName("rejects impossible arguments")
    void rejectsImpossibleArguments() {
        BlackScholesInputs in = atTheMoney();
        assertThrows(
                IllegalArgumentException.class,
                () -> MonteCarloOptionPricer.price(OptionType.CALL, in, 0, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> MonteCarloOptionPricer.price(null, in, 1_000, 1L));
    }
}
