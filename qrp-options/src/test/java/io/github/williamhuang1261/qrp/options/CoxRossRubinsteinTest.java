package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoxRossRubinsteinTest {

    private static BlackScholesInputs atTheMoney() {
        return BlackScholesInputs.equity(100.0, 100.0, 1.0, 0.20, 0.05);
    }

    @Test
    @DisplayName("the European tree converges to Black-Scholes as steps grow")
    void europeanConvergesToBlackScholes() {
        BlackScholesInputs in = atTheMoney();
        double closedForm = BlackScholesMerton.price(OptionType.CALL, in);

        double previousError = Double.POSITIVE_INFINITY;
        for (int steps : new int[] {50, 200, 800, 3200}) {
            double tree = CoxRossRubinstein.price(OptionType.CALL, ExerciseStyle.EUROPEAN, in, steps);
            double error = Math.abs(tree - closedForm);
            // CRR converges at O(1/steps) with an odd/even oscillation, so the
            // trend rather than every single step is asserted: doubling the step
            // count from 200 to 800 (4x) should not leave the error worse than a
            // loose multiple of what quadrupling steps predicts.
            assertTrue(
                    error < previousError * 1.5 || error < 1e-6,
                    "error grew from " + previousError + " to " + error + " at steps=" + steps);
            previousError = error;
        }
        assertTrue(previousError < 1e-3, "final error too large: " + previousError);
    }

    @Test
    @DisplayName("the put converges to Black-Scholes too, checked via parity")
    void europeanPutConverges() {
        BlackScholesInputs in = atTheMoney();
        double closedForm = BlackScholesMerton.price(OptionType.PUT, in);
        double tree = CoxRossRubinstein.price(OptionType.PUT, ExerciseStyle.EUROPEAN, in, 2000);

        assertEquals(closedForm, tree, 5e-3);
    }

    @Test
    @DisplayName("an American put is worth at least as much as its European twin")
    void americanPutDominatesEuropean() {
        BlackScholesInputs in =
                BlackScholesInputs.equity(90.0, 100.0, 1.0, 0.30, 0.06);

        double european = CoxRossRubinstein.price(OptionType.PUT, ExerciseStyle.EUROPEAN, in, 500);
        double american = CoxRossRubinstein.price(OptionType.PUT, ExerciseStyle.AMERICAN, in, 500);

        assertTrue(
                american >= european - 1e-9,
                "American put " + american + " should not be worth less than European " + european);
    }

    @Test
    @DisplayName("a deep in-the-money American put is strictly more valuable early-exercised")
    void deepItmAmericanPutExercisesEarly() {
        // Deep ITM with a real rate: the early-exercise value of the strike today
        // beats waiting, so American must be strictly above European here.
        BlackScholesInputs deepItm = BlackScholesInputs.equity(50.0, 100.0, 2.0, 0.20, 0.08);

        double european = CoxRossRubinstein.price(OptionType.PUT, ExerciseStyle.EUROPEAN, deepItm, 400);
        double american = CoxRossRubinstein.price(OptionType.PUT, ExerciseStyle.AMERICAN, deepItm, 400);

        assertTrue(american > european + 0.5, "expected a real early-exercise premium; "
                + "american=" + american + " european=" + european);
    }

    @Test
    @DisplayName("an American call on a non-dividend underlying is never exercised early")
    void americanCallOnNonDividendEqualsEuropean() {
        // The textbook result: with q = 0, waiting always dominates exercising,
        // because exercising throws away the remaining time value for nothing.
        BlackScholesInputs deepItm = BlackScholesInputs.equity(150.0, 100.0, 1.0, 0.25, 0.05);

        double european = CoxRossRubinstein.price(OptionType.CALL, ExerciseStyle.EUROPEAN, deepItm, 500);
        double american = CoxRossRubinstein.price(OptionType.CALL, ExerciseStyle.AMERICAN, deepItm, 500);

        assertEquals(european, american, 1e-6);
    }

    @Test
    @DisplayName("delta and gamma match a finite difference over the tree itself")
    void spotGreeksMatchFiniteDifference() {
        // Gamma over a lattice is the classic unstable case: the step used to
        // measure it has to be wide enough to average over several nodes, or the
        // second difference measures the tree's own discretization sawtooth
        // instead of curvature. A $0.50 step against 400 nodes (node spacing
        // sigma*sqrt(dt)*S =~ $1 here) was tried first and was off by 4x; $5
        // against 4000 steps, confirmed by scanning both step count and spot
        // step until the error stopped moving, gets within 1e-4.
        BlackScholesInputs in = atTheMoney();
        FiniteDifferenceGreeks.Pricer pricer =
                bumped -> CoxRossRubinstein.price(OptionType.CALL, ExerciseStyle.EUROPEAN, bumped, 4000);

        Greeks numeric = FiniteDifferenceGreeks.estimateSpotGreeks(pricer, in, 5.0);
        Greeks analytic = BlackScholesMerton.greeks(OptionType.CALL, in);

        // Delta's own central-difference error is O(h^2) via the option's
        // curvature (gamma), so the wide step chosen for gamma's stability costs
        // delta some precision in return -- 3e-3 on a step of $5, well inside
        // the tolerance a desk would care about.
        assertEquals(analytic.delta(), numeric.delta(), 3e-3);
        assertEquals(analytic.gamma(), numeric.gamma(), 1e-3);
    }

    @Test
    @DisplayName("degenerate inputs price at discounted intrinsic on the forward, matching Black-Scholes")
    void degenerateInputsMatchTheClosedForm() {
        BlackScholesInputs zeroVol = BlackScholesInputs.equity(105.0, 100.0, 1.0, 0.0, 0.05);
        BlackScholesInputs expired = BlackScholesInputs.equity(120.0, 100.0, 0.0, 0.2, 0.05);

        assertEquals(
                BlackScholesMerton.price(OptionType.CALL, zeroVol),
                CoxRossRubinstein.price(OptionType.CALL, ExerciseStyle.EUROPEAN, zeroVol, 100),
                1e-9);
        assertEquals(
                BlackScholesMerton.price(OptionType.CALL, expired),
                CoxRossRubinstein.price(OptionType.CALL, ExerciseStyle.EUROPEAN, expired, 100),
                1e-9);
    }

    @Test
    @DisplayName("rejects impossible arguments")
    void rejectsImpossibleArguments() {
        BlackScholesInputs in = atTheMoney();
        assertThrows(
                IllegalArgumentException.class,
                () -> CoxRossRubinstein.price(OptionType.CALL, ExerciseStyle.EUROPEAN, in, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> CoxRossRubinstein.price(null, ExerciseStyle.EUROPEAN, in, 100));
    }
}
