package io.github.williamhuang1261.qrp.nativeengine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import io.github.williamhuang1261.qrp.stats.JavaComputeEngine;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The equivalence suite. Its whole purpose is the claim that selecting an engine
 * cannot change a result, so every numeric assertion here is exact: no
 * tolerance, no "close enough".
 *
 * <p>On a machine with no C++ toolchain the equivalence tests are skipped rather
 * than failed — that is the supported configuration, and
 * {@link NativeFallbackTest} covers it — but the skip prints the reason, so an
 * unbuilt kernel never passes silently as if it had been checked.
 */
class NativeComputeEngineTest {

    private static final NativeComputeEngine NATIVE_ENGINE = new NativeComputeEngine();
    private static final ComputeEngine JAVA_ENGINE = new JavaComputeEngine();

    @BeforeAll
    static void requireKernel() {
        Assumptions.assumeTrue(NATIVE_ENGINE.isAvailable(),
                () -> "native kernel not built: " + NATIVE_ENGINE.unavailableReason().orElse("unknown")
                        + " (build it with: make -C native)");
    }

    private static double[] syntheticReturns(int n) {
        double[] returns = new double[n];
        for (int i = 0; i < n; i++) {
            returns[i] = 0.0004 + 0.012 * Math.sin(i / 6.0) - 0.003 * Math.cos(i / 17.0);
        }
        return returns;
    }

    @Test
    @DisplayName("bootstrap means are bit-identical to the Java engine")
    void bootstrapMeansAreBitIdentical() {
        double[] sample = syntheticReturns(500);

        for (int blockSize : new int[] {1, 7, 40, 500}) {
            double[] fromJava = JAVA_ENGINE.bootstrapMeans(sample, 1_000, blockSize, 20260825L);
            double[] fromNative = NATIVE_ENGINE.bootstrapMeans(sample, 1_000, blockSize, 20260825L);

            assertArrayEquals(fromJava, fromNative, 0.0,
                    "engines disagree at block size " + blockSize);
        }
    }

    @Test
    @DisplayName("identical across seeds and draw counts, including past the parallel threshold")
    void identicalAcrossSeedsAndSizes() {
        double[] sample = syntheticReturns(300);

        for (long seed : new long[] {0L, 1L, -1L, Long.MIN_VALUE, 987654321L}) {
            for (int draws : new int[] {1, 255, 256, 5_000}) {
                assertArrayEquals(
                        JAVA_ENGINE.bootstrapMeans(sample, draws, 20, seed),
                        NATIVE_ENGINE.bootstrapMeans(sample, draws, 20, seed),
                        0.0,
                        "engines disagree at seed " + seed + ", draws " + draws);
            }
        }
    }

    @Test
    @DisplayName("rolling means are bit-identical, warm-up NaNs included")
    void rollingMeanIsBitIdentical() {
        double[] values = syntheticReturns(1_000);

        for (int window : new int[] {1, 2, 20, 250}) {
            double[] fromJava = JAVA_ENGINE.rollingMean(values, window);
            double[] fromNative = NATIVE_ENGINE.rollingMean(values, window);

            assertEquals(fromJava.length, fromNative.length);
            for (int i = 0; i < fromJava.length; i++) {
                if (Double.isNaN(fromJava[i])) {
                    assertTrue(Double.isNaN(fromNative[i]),
                            "native defined a value at " + i + " where Java has NaN");
                } else {
                    assertEquals(fromJava[i], fromNative[i], 0.0, "window " + window + ", index " + i);
                }
            }
        }
    }

    @Test
    @DisplayName("running twice gives the same answer, so threads do not leak into the result")
    void repeatedRunsAgree() {
        double[] sample = syntheticReturns(400);

        assertArrayEquals(
                NATIVE_ENGINE.bootstrapMeans(sample, 10_000, 25, 7L),
                NATIVE_ENGINE.bootstrapMeans(sample, 10_000, 25, 7L),
                0.0);
    }

    @Test
    @DisplayName("reports the OpenMP thread count it will actually use")
    void reportsThreadCount() {
        assertTrue(NATIVE_ENGINE.openmpThreads() >= 1);
        assertEquals("openmp", NATIVE_ENGINE.id());
        assertTrue(NATIVE_ENGINE.libraryPath().isPresent());
    }

    @Test
    @DisplayName("rejects the same bad arguments the Java engine rejects")
    void rejectsTheSameBadArguments() {
        double[] sample = syntheticReturns(50);

        assertThrows(IllegalArgumentException.class,
                () -> NATIVE_ENGINE.bootstrapMeans(sample, 10, 51, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> NATIVE_ENGINE.bootstrapMeans(new double[0], 10, 1, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> NATIVE_ENGINE.bootstrapMeans(sample, 0, 1, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> NATIVE_ENGINE.rollingMean(sample, 0));
    }
}
