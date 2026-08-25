package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalQuantileTest {

    @Test
    @DisplayName("matches the textbook critical values")
    void matchesKnownCriticalValues() {
        assertEquals(1.6448536269514722, NormalQuantile.inverseCdf(0.95), 1e-9);
        assertEquals(1.959963984540054, NormalQuantile.inverseCdf(0.975), 1e-9);
        assertEquals(2.5758293035489004, NormalQuantile.inverseCdf(0.995), 1e-9);
        assertEquals(0.0, NormalQuantile.inverseCdf(0.5), 1e-12);
    }

    @Test
    @DisplayName("is symmetric about the median")
    void isSymmetric() {
        for (double p : new double[] {0.01, 0.1, 0.3, 0.45}) {
            assertEquals(-NormalQuantile.inverseCdf(1.0 - p), NormalQuantile.inverseCdf(p), 1e-9);
        }
    }

    @Test
    @DisplayName("stays accurate in the far tail, where the approximation switches branch")
    void isAccurateInTheTail() {
        assertEquals(-3.090232306167813, NormalQuantile.inverseCdf(0.001), 1e-8);
        assertEquals(-4.264890793922602, NormalQuantile.inverseCdf(0.00001), 1e-7);
    }

    @Test
    @DisplayName("rejects probabilities outside (0, 1)")
    void rejectsBadProbability() {
        assertThrows(IllegalArgumentException.class, () -> NormalQuantile.inverseCdf(0.0));
        assertThrows(IllegalArgumentException.class, () -> NormalQuantile.inverseCdf(1.0));
    }
}
