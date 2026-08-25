package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JackknifeCorrelationTest {

    @Test
    @DisplayName("a perfect linear relationship correlates at 1 with no standard error")
    void perfectCorrelation() {
        double[] x = {1, 2, 3, 4, 5, 6, 7, 8};
        double[] y = {2, 4, 6, 8, 10, 12, 14, 16};

        JackknifeCorrelation jackknife = JackknifeCorrelation.of(x, y, 0.95);

        assertEquals(1.0, jackknife.estimate(), 1e-12);
        assertEquals(0.0, jackknife.standardError(), 1e-12);
        assertEquals(0.0, jackknife.bias(), 1e-12);
    }

    @Test
    @DisplayName("an inverse relationship correlates at -1")
    void inverseCorrelation() {
        double[] x = {1, 2, 3, 4, 5, 6};
        double[] y = {12, 10, 8, 6, 4, 2};

        assertEquals(-1.0, JackknifeCorrelation.of(x, y, 0.95).estimate(), 1e-12);
    }

    @Test
    @DisplayName("unrelated series give an interval that straddles zero")
    void unrelatedSeriesStraddleZero() {
        int n = 200;
        double[] x = new double[n];
        double[] y = new double[n];
        SplitMix64 random = new SplitMix64(31L);
        for (int i = 0; i < n; i++) {
            x[i] = random.nextDouble();
            y[i] = random.nextDouble();
        }

        JackknifeCorrelation jackknife = JackknifeCorrelation.of(x, y, 0.95);

        assertTrue(jackknife.interval().contains(0.0),
                "interval [" + jackknife.interval().lower() + ", " + jackknife.interval().upper() + "]");
    }

    @Test
    @DisplayName("a genuine relationship gives an interval that excludes zero")
    void realRelationshipExcludesZero() {
        int n = 200;
        double[] x = new double[n];
        double[] y = new double[n];
        SplitMix64 random = new SplitMix64(17L);
        for (int i = 0; i < n; i++) {
            x[i] = random.nextDouble();
            y[i] = 0.7 * x[i] + 0.3 * random.nextDouble();
        }

        JackknifeCorrelation jackknife = JackknifeCorrelation.of(x, y, 0.95);

        assertTrue(jackknife.interval().excludesZero());
        assertTrue(jackknife.estimate() > 0.5, "estimate was " + jackknife.estimate());
    }

    @Test
    @DisplayName("one outlier shows up as a large standard error")
    void outlierInflatesTheStandardError() {
        double[] x = {1, 2, 3, 4, 5, 6, 7, 8, 9, 50};
        double[] y = {1, 2, 3, 4, 5, 6, 7, 8, 9, -50};
        double[] cleanX = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        double[] cleanY = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        double withOutlier = JackknifeCorrelation.of(x, y, 0.95).standardError();
        double without = JackknifeCorrelation.of(cleanX, cleanY, 0.95).standardError();

        assertTrue(withOutlier > without, withOutlier + " should exceed " + without);
    }

    @Test
    @DisplayName("a wider level widens the interval")
    void higherCoverageIsWider() {
        double[] x = {1, 3, 2, 5, 4, 7, 6, 9, 8, 11};
        double[] y = {2, 3, 4, 4, 6, 7, 7, 9, 10, 10};

        double narrow = JackknifeCorrelation.of(x, y, 0.80).interval().width();
        double wide = JackknifeCorrelation.of(x, y, 0.99).interval().width();

        assertTrue(wide > narrow);
    }

    @Test
    @DisplayName("rejects mismatched or too-short series")
    void rejectsUnusableInput() {
        assertThrows(IllegalArgumentException.class,
                () -> JackknifeCorrelation.of(new double[] {1, 2, 3}, new double[] {1, 2}, 0.95));
        assertThrows(IllegalArgumentException.class,
                () -> JackknifeCorrelation.of(new double[] {1, 2}, new double[] {1, 2}, 0.95));
    }
}
