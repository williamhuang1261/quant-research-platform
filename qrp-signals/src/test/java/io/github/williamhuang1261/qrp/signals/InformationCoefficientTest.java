package io.github.williamhuang1261.qrp.signals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.stats.SplitMix64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InformationCoefficientTest {

    @Test
    @DisplayName("a signal that ranks instruments identically to forward returns has IC 1.0")
    void perfectRankAgreementIsOne() {
        double[] signal = {0.5, 3.2, 1.1, 9.0};
        double[] forwardReturns = {0.01, 0.05, 0.02, 0.10};

        assertEquals(1.0, InformationCoefficient.spearman(signal, forwardReturns), 1e-12);
    }

    @Test
    @DisplayName("a signal that ranks instruments in reverse of forward returns has IC -1.0")
    void perfectRankDisagreementIsNegativeOne() {
        // forwardReturns ranks are [1, 3, 2, 4] (instrument order); signal is
        // built so its ranks are the exact mirror, [4, 2, 3, 1].
        double[] signal = {9.0, 1.1, 3.2, 0.5};
        double[] forwardReturns = {0.01, 0.05, 0.02, 0.10};

        assertEquals(-1.0, InformationCoefficient.spearman(signal, forwardReturns), 1e-12);
    }

    @Test
    @DisplayName("the ranking is what matters, not the raw magnitude")
    void invariantToMonotoneRescaling() {
        double[] signal = {0.5, 3.2, 1.1, 9.0};
        double[] forwardReturns = {0.01, 0.05, 0.02, 0.10};
        double[] rescaledSignal = {5.0, 32.0, 11.0, 90.0};

        assertEquals(
                InformationCoefficient.spearman(signal, forwardReturns),
                InformationCoefficient.spearman(rescaledSignal, forwardReturns),
                1e-12);
    }

    @Test
    @DisplayName("independent noise gives an IC near zero across many instruments")
    void independentNoiseIsNearZero() {
        int instruments = 500;
        double[] signal = new double[instruments];
        double[] forwardReturns = new double[instruments];
        SplitMix64 random = new SplitMix64(7L);
        for (int i = 0; i < instruments; i++) {
            signal[i] = random.nextDouble();
            forwardReturns[i] = random.nextDouble();
        }

        double ic = InformationCoefficient.spearman(signal, forwardReturns);
        assertTrue(Math.abs(ic) < 0.15, "expected an IC near zero, got: " + ic);
    }

    @Test
    @DisplayName("perPeriod computes one IC per row, matching spearman called directly")
    void perPeriodMatchesSpearmanPerRow() {
        double[][] signals = {
                {0.5, 3.2, 1.1, 9.0},
                {9.0, 3.2, 1.1, 0.5},
        };
        double[][] forwardReturns = {
                {0.01, 0.05, 0.02, 0.10},
                {0.01, 0.05, 0.02, 0.10},
        };

        double[] ic = InformationCoefficient.perPeriod(signals, forwardReturns);

        assertArrayEquals(
                new double[] {
                        InformationCoefficient.spearman(signals[0], forwardReturns[0]),
                        InformationCoefficient.spearman(signals[1], forwardReturns[1]),
                },
                ic,
                1e-12);
    }

    @Test
    @DisplayName("mismatched lengths are rejected")
    void mismatchedLengthsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> InformationCoefficient.spearman(new double[] {1, 2, 3}, new double[] {1, 2}));
        assertThrows(IllegalArgumentException.class,
                () -> InformationCoefficient.perPeriod(new double[][] {{1, 2, 3}}, new double[][] {{1, 2, 3}, {1, 2, 3}}));
    }

    @Test
    @DisplayName("fewer than 3 instruments is rejected")
    void tooFewInstrumentsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> InformationCoefficient.spearman(new double[] {1, 2}, new double[] {1, 2}));
    }
}
