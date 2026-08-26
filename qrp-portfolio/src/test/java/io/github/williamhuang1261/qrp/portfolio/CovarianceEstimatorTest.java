package io.github.williamhuang1261.qrp.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CovarianceEstimatorTest {

    // x and y are a perfect negative correlation (corr = -1); z is x itself
    // (corr = 1 to x). Both have sample variance 5/3, hand-computed:
    // mean 2.5, deviations {-1.5, -0.5, 0.5, 1.5}, sum of squares 5.0, / (n-1) = 5/3.
    private static final double[] X = {1, 2, 3, 4};
    private static final double[] Y = {4, 3, 2, 1};
    private static final double[] Z = {1, 2, 3, 4};
    private static final double VARIANCE = 5.0 / 3.0;

    @Test
    @DisplayName("diagonal is each instrument's sample variance")
    void diagonalIsSampleVariance() {
        double[][] covariance = CovarianceEstimator.estimate(new double[][] {X, Y, Z});

        assertEquals(VARIANCE, covariance[0][0], 1e-12);
        assertEquals(VARIANCE, covariance[1][1], 1e-12);
        assertEquals(VARIANCE, covariance[2][2], 1e-12);
    }

    @Test
    @DisplayName("off-diagonal is correlation scaled by both standard deviations, hand-checked")
    void offDiagonalMatchesHandComputation() {
        double[][] covariance = CovarianceEstimator.estimate(new double[][] {X, Y, Z});

        // corr(X, Y) = -1, so cov = -1 * sqrt(VARIANCE) * sqrt(VARIANCE) = -VARIANCE
        assertEquals(-VARIANCE, covariance[0][1], 1e-9);
        // corr(X, Z) = 1 (Z is X), so cov = VARIANCE
        assertEquals(VARIANCE, covariance[0][2], 1e-9);
        // corr(Y, Z) = corr(Y, X) = -1
        assertEquals(-VARIANCE, covariance[1][2], 1e-9);
    }

    @Test
    @DisplayName("the matrix is symmetric")
    void isSymmetric() {
        double[][] covariance = CovarianceEstimator.estimate(new double[][] {X, Y, Z});

        for (int i = 0; i < covariance.length; i++) {
            for (int j = 0; j < covariance.length; j++) {
                assertEquals(covariance[i][j], covariance[j][i], 1e-12, "asymmetric at (" + i + "," + j + ")");
            }
        }
    }

    @Test
    @DisplayName("a single instrument returns a 1x1 matrix of its own variance")
    void singleInstrument() {
        double[][] covariance = CovarianceEstimator.estimate(new double[][] {X});

        assertEquals(1, covariance.length);
        assertEquals(VARIANCE, covariance[0][0], 1e-12);
    }

    @Test
    @DisplayName("rejects zero instruments")
    void rejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> CovarianceEstimator.estimate(new double[0][]));
    }

    @Test
    @DisplayName("rejects a series with fewer than 2 observations")
    void rejectsTooFewObservations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CovarianceEstimator.estimate(new double[][] {{1.0}}));
    }

    @Test
    @DisplayName("rejects mismatched series lengths")
    void rejectsMismatchedLengths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CovarianceEstimator.estimate(new double[][] {X, {1.0, 2.0}}));
    }
}
