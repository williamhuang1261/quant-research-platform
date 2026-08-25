package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PercentilesTest {

    private static final double[] ONE_TO_FIVE = {1.0, 2.0, 3.0, 4.0, 5.0};

    @Test
    @DisplayName("the extremes are the extremes")
    void boundsAreExact() {
        assertEquals(1.0, Percentiles.of(ONE_TO_FIVE, 0.0), 1e-12);
        assertEquals(5.0, Percentiles.of(ONE_TO_FIVE, 1.0), 1e-12);
        assertEquals(3.0, Percentiles.of(ONE_TO_FIVE, 0.5), 1e-12);
    }

    @Test
    @DisplayName("interpolates linearly between order statistics (type 7)")
    void interpolatesBetweenPoints() {
        // position = 0.25 * (5 - 1) = 1.0 exactly, so the second value.
        assertEquals(2.0, Percentiles.of(ONE_TO_FIVE, 0.25), 1e-12);
        // position = 0.4 * 4 = 1.6, between 2 and 3.
        assertEquals(2.6, Percentiles.of(ONE_TO_FIVE, 0.4), 1e-12);
    }

    @Test
    @DisplayName("does not modify the caller's array")
    void doesNotSortInPlace() {
        double[] unsorted = {5.0, 1.0, 3.0};

        Percentiles.of(unsorted, 0.5);

        assertEquals(5.0, unsorted[0], 1e-12);
    }

    @Test
    @DisplayName("rejects an empty sample or a quantile outside [0, 1]")
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> Percentiles.of(new double[0], 0.5));
        assertThrows(IllegalArgumentException.class, () -> Percentiles.of(ONE_TO_FIVE, 1.5));
    }
}
