package io.github.williamhuang1261.qrp.signals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankTransformTest {

    @Test
    @DisplayName("strictly increasing values rank 1..n in order")
    void strictlyIncreasing() {
        double[] values = {10.0, 20.0, 30.0, 40.0};
        assertArrayEquals(new double[] {1.0, 2.0, 3.0, 4.0}, RankTransform.ranks(values), 1e-12);
    }

    @Test
    @DisplayName("strictly decreasing values rank in reverse")
    void strictlyDecreasing() {
        double[] values = {40.0, 30.0, 20.0, 10.0};
        assertArrayEquals(new double[] {4.0, 3.0, 2.0, 1.0}, RankTransform.ranks(values), 1e-12);
    }

    @Test
    @DisplayName("a tie gets the average of the positions it spans")
    void tieGetsAverageRank() {
        // Sorted: 5, 5, 5, 8 -> positions 1,2,3 tied at (1+2+3)/3 = 2.0; 8 -> 4.
        double[] values = {5.0, 8.0, 5.0, 5.0};
        assertArrayEquals(new double[] {2.0, 4.0, 2.0, 2.0}, RankTransform.ranks(values), 1e-12);
    }

    @Test
    @DisplayName("a tie spanning two positions averages to a half-integer rank")
    void twoWayTie() {
        // Sorted: 1, 2, 2, 3 -> the two 2's tie for positions 2,3 -> rank 2.5 each.
        double[] values = {1.0, 2.0, 3.0, 2.0};
        assertArrayEquals(new double[] {1.0, 2.5, 4.0, 2.5}, RankTransform.ranks(values), 1e-12);
    }

    @Test
    @DisplayName("all values tied share the middle rank")
    void allTied() {
        double[] values = {7.0, 7.0, 7.0, 7.0};
        assertArrayEquals(new double[] {2.5, 2.5, 2.5, 2.5}, RankTransform.ranks(values), 1e-12);
    }

    @Test
    @DisplayName("a single value ranks 1")
    void singleValue() {
        assertArrayEquals(new double[] {1.0}, RankTransform.ranks(new double[] {42.0}), 1e-12);
    }

    @Test
    @DisplayName("an empty array is rejected")
    void emptyRejected() {
        assertThrows(IllegalArgumentException.class, () -> RankTransform.ranks(new double[0]));
    }

    @Test
    @DisplayName("a non-finite value is rejected")
    void nonFiniteRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RankTransform.ranks(new double[] {1.0, Double.NaN, 3.0}));
        assertThrows(IllegalArgumentException.class,
                () -> RankTransform.ranks(new double[] {1.0, Double.POSITIVE_INFINITY, 3.0}));
    }
}
