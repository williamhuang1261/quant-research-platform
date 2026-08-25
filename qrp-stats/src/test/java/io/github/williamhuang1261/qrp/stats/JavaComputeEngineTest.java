package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaComputeEngineTest {

    private final ComputeEngine engine = new JavaComputeEngine();

    @Test
    @DisplayName("is always available, which is what makes it the fallback")
    void isAlwaysAvailable() {
        assertTrue(engine.isAvailable());
        assertEquals("java", engine.id());
    }

    @Test
    @DisplayName("rolling mean leaves the warm-up undefined and matches a direct mean")
    void rollingMeanMatchesDirectMean() {
        double[] values = {1.0, 2.0, 3.0, 4.0, 5.0};

        double[] means = engine.rollingMean(values, 3);

        assertTrue(Double.isNaN(means[0]));
        assertTrue(Double.isNaN(means[1]));
        assertEquals(2.0, means[2], 1e-12);
        assertEquals(3.0, means[3], 1e-12);
        assertEquals(4.0, means[4], 1e-12);
    }

    @Test
    @DisplayName("bootstrap means of a constant series are that constant")
    void constantSeriesResamplesToItself() {
        double[] constant = new double[100];
        java.util.Arrays.fill(constant, 0.005);

        double[] means = engine.bootstrapMeans(constant, 500, 10, 1L);

        for (double mean : means) {
            assertEquals(0.005, mean, 1e-15);
        }
    }

    @Test
    @DisplayName("resampled means scatter around the sample mean")
    void meansScatterAroundTheSampleMean() {
        double[] sample = new double[200];
        for (int i = 0; i < sample.length; i++) {
            sample[i] = Math.sin(i / 5.0) * 0.01 + 0.001;
        }
        double sampleMean = java.util.Arrays.stream(sample).average().orElseThrow();

        double[] means = engine.bootstrapMeans(sample, 2_000, 20, 99L);
        double average = java.util.Arrays.stream(means).average().orElseThrow();

        assertEquals(sampleMean, average, 0.001);
        assertFalse(java.util.Arrays.stream(means).distinct().count() == 1,
                "every draw returned the same mean, so nothing was resampled");
    }

    @Test
    @DisplayName("the whole-series draw and the mean-only draw agree")
    void drawAndDrawMeanAgree() {
        double[] sample = new double[120];
        for (int i = 0; i < sample.length; i++) {
            sample[i] = i * 0.001;
        }

        double[] resampled = Bootstrap.draw(sample, 12, 77L, 3);
        double direct = java.util.Arrays.stream(resampled).average().orElseThrow();
        double viaEngine = engine.bootstrapMeans(sample, 4, 12, 77L)[3];

        assertEquals(direct, viaEngine, 1e-12);
        assertEquals(sample.length, resampled.length);
    }

    @Test
    @DisplayName("the sequential engine returns exactly what the parallel one returns")
    void sequentialMatchesParallel() {
        double[] sample = new double[300];
        for (int i = 0; i < sample.length; i++) {
            sample[i] = Math.cos(i / 8.0) * 0.02;
        }

        double[] parallel = engine.bootstrapMeans(sample, 5_000, 15, 123L);
        double[] sequential = JavaComputeEngine.sequential().bootstrapMeans(sample, 5_000, 15, 123L);

        org.junit.jupiter.api.Assertions.assertArrayEquals(parallel, sequential, 0.0);
        assertEquals("java-sequential", JavaComputeEngine.sequential().id());
    }

    @Test
    @DisplayName("rejects a window or a draw count that cannot work")
    void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> engine.rollingMean(new double[] {1.0}, 0));
        assertThrows(IllegalArgumentException.class,
                () -> engine.bootstrapMeans(new double[] {1.0}, 0, 1, 1L));
    }
}
