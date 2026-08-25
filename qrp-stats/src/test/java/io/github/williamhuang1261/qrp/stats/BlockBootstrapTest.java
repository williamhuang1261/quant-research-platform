package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockBootstrapTest {

    private final BlockBootstrap bootstrap = new BlockBootstrap(ComputeEngines.portable());

    private static double[] returnsAround(double mean, double amplitude, int n) {
        double[] returns = new double[n];
        for (int i = 0; i < n; i++) {
            returns[i] = mean + amplitude * Math.sin(i / 3.0);
        }
        return returns;
    }

    @Test
    @DisplayName("the same seed reproduces the interval exactly")
    void isReproducible() {
        double[] sample = returnsAround(0.001, 0.01, 250);

        ConfidenceInterval first = bootstrap.meanInterval(sample, 1_000, 20, 0.95, 42L);
        ConfidenceInterval second = bootstrap.meanInterval(sample, 1_000, 20, 0.95, 42L);

        assertEquals(first, second);
        assertEquals(first.lower(), second.lower(), 0.0);
        assertEquals(first.upper(), second.upper(), 0.0);
    }

    @Test
    @DisplayName("a different seed gives a different interval")
    void seedChangesTheInterval() {
        double[] sample = returnsAround(0.001, 0.01, 250);

        ConfidenceInterval first = bootstrap.meanInterval(sample, 1_000, 20, 0.95, 42L);
        ConfidenceInterval other = bootstrap.meanInterval(sample, 1_000, 20, 0.95, 43L);

        assertNotEquals(first.lower(), other.lower());
    }

    @Test
    @DisplayName("the parallel path agrees with the sequential one, bit for bit")
    void parallelAgreesWithSequential() {
        double[] sample = returnsAround(0.0005, 0.02, 300);

        // 100 draws stays sequential, 5,000 crosses the parallel threshold.
        double[] small = bootstrap.meanDistribution(sample, 100, 15, 7L);
        double[] large = bootstrap.meanDistribution(sample, 5_000, 15, 7L);

        for (int draw = 0; draw < small.length; draw++) {
            assertEquals(small[draw], large[draw], 0.0,
                    "draw " + draw + " changed when the loop went parallel");
        }
    }

    @Test
    @DisplayName("the interval brackets the sample mean and narrows as draws grow")
    void intervalBracketsTheEstimate() {
        double[] sample = returnsAround(0.002, 0.005, 400);

        ConfidenceInterval interval = bootstrap.meanInterval(sample, 2_000, 20, 0.95, 11L);

        assertTrue(interval.contains(interval.pointEstimate()),
                interval.pointEstimate() + " outside [" + interval.lower() + ", " + interval.upper() + "]");
        assertTrue(interval.width() > 0.0);
    }

    @Test
    @DisplayName("a wider level gives a wider interval")
    void higherCoverageIsWider() {
        double[] sample = returnsAround(0.001, 0.01, 250);

        double narrow = bootstrap.meanInterval(sample, 2_000, 20, 0.80, 5L).width();
        double wide = bootstrap.meanInterval(sample, 2_000, 20, 0.99, 5L).width();

        assertTrue(wide > narrow, wide + " should exceed " + narrow);
    }

    @Test
    @DisplayName("blocks preserve runs: a block of one destroys the serial structure")
    void blockSizeMattersForSerialData() {
        // A trending series: consecutive observations are strongly related.
        double[] trending = new double[300];
        for (int i = 0; i < trending.length; i++) {
            trending[i] = Math.sin(i / 20.0) * 0.02;
        }

        double blocky = bootstrap.meanInterval(trending, 2_000, 40, 0.95, 3L).width();
        double iid = bootstrap.meanInterval(trending, 2_000, 1, 0.95, 3L).width();

        assertNotEquals(blocky, iid,
                "block and i.i.d. resampling should not give the same interval on serial data");
    }

    @Test
    @DisplayName("rejects a block longer than the sample, or a nonsense level")
    void rejectsBadArguments() {
        double[] sample = returnsAround(0.001, 0.01, 50);

        assertThrows(IllegalArgumentException.class,
                () -> bootstrap.meanInterval(sample, 100, 51, 0.95, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> bootstrap.meanInterval(sample, 100, 10, 1.0, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> bootstrap.meanInterval(new double[0], 100, 1, 0.95, 1L));
    }
}
