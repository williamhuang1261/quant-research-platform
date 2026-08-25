package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonteCarloSimulationTest {

    private final MonteCarloSimulation simulation = new MonteCarloSimulation();

    private static double[] alternatingReturns(int n, double up, double down) {
        double[] returns = new double[n];
        for (int i = 0; i < n; i++) {
            returns[i] = i % 2 == 0 ? up : down;
        }
        return returns;
    }

    @Test
    @DisplayName("the same seed reproduces the whole report")
    void isReproducible() {
        double[] returns = alternatingReturns(200, 0.01, -0.008);

        MonteCarloSimulation.Report first = simulation.run(returns, 100_000.0, 500, 10, 0.95, 4L);
        MonteCarloSimulation.Report second = simulation.run(returns, 100_000.0, 500, 10, 0.95, 4L);

        assertEquals(first, second);
    }

    @Test
    @DisplayName("a series that only rises never loses, whatever the ordering")
    void alwaysPositiveReturnsNeverLose() {
        double[] returns = new double[100];
        java.util.Arrays.fill(returns, 0.002);

        MonteCarloSimulation.Report report = simulation.run(returns, 10_000.0, 200, 5, 0.95, 1L);

        assertEquals(0.0, report.probabilityOfLoss(), 1e-12);
        assertEquals(0.0, report.maxDrawdown().upper(), 1e-12);
        assertTrue(report.medianFinalEquity() > 10_000.0);
    }

    @Test
    @DisplayName("a losing series loses on nearly every path")
    void losingSeriesLosesOnMostPaths() {
        double[] returns = new double[150];
        java.util.Arrays.fill(returns, -0.001);

        MonteCarloSimulation.Report report = simulation.run(returns, 10_000.0, 300, 10, 0.95, 2L);

        assertEquals(1.0, report.probabilityOfLoss(), 1e-12);
        assertTrue(report.finalEquity().upper() < 10_000.0);
    }

    @Test
    @DisplayName("reordering the same returns produces a spread of outcomes and drawdowns")
    void reorderingProducesASpread() {
        double[] returns = alternatingReturns(250, 0.015, -0.012);

        MonteCarloSimulation.Report report = simulation.run(returns, 100_000.0, 1_000, 20, 0.95, 8L);

        assertTrue(report.finalEquity().width() > 0.0, "every path ended identically");
        assertTrue(report.maxDrawdown().lower() >= 0.0);
        assertTrue(report.maxDrawdown().upper() <= 1.0);
        assertTrue(report.maxDrawdown().upper() > report.maxDrawdown().lower());
        assertEquals(1_000, report.paths());
    }

    @Test
    @DisplayName("the median final equity is the middle of the simulated distribution")
    void medianSitsInsideTheInterval() {
        double[] returns = alternatingReturns(200, 0.01, -0.005);

        MonteCarloSimulation.Report report = simulation.run(returns, 50_000.0, 500, 10, 0.90, 6L);

        assertTrue(report.finalEquity().contains(report.medianFinalEquity()));
        assertEquals(report.finalEquity().pointEstimate(), report.medianFinalEquity(), 1e-12);
    }

    @Test
    @DisplayName("rejects a non-positive starting balance or an impossible block")
    void rejectsBadArguments() {
        double[] returns = alternatingReturns(50, 0.01, -0.01);

        assertThrows(IllegalArgumentException.class,
                () -> simulation.run(returns, 0.0, 100, 5, 0.95, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> simulation.run(returns, 10_000.0, 100, 51, 0.95, 1L));
    }
}
