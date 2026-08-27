package io.github.williamhuang1261.qrp.nativeengine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Correctness for {@code qrp_bootstrap_medians} (Extension 8): the arena and
 * malloc-backed scratch-buffer strategies must agree bit-identically, the
 * same cross-engine-agreement culture {@link NativeComputeEngineTest} already
 * holds the mean kernel to. There is no portable Java median engine in this
 * platform to compare against, so this suite asserts the one thing that
 * actually distinguishes the two allocation paths: neither changes the
 * numbers.
 */
class BootstrapMedianTest {

    private static final NativeComputeEngine NATIVE_ENGINE = new NativeComputeEngine();

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
    @DisplayName("arena and malloc scratch-buffer paths are bit-identical")
    void arenaAndMallocAgree() {
        double[] sample = syntheticReturns(500);

        for (int blockSize : new int[] {1, 7, 40, 500}) {
            double[] arenaOn = NATIVE_ENGINE.bootstrapMedians(sample, 1_000, blockSize, 20260827L, true);
            double[] arenaOff = NATIVE_ENGINE.bootstrapMedians(sample, 1_000, blockSize, 20260827L, false);

            assertArrayEquals(arenaOn, arenaOff, 0.0,
                    "arena and malloc paths disagree at block size " + blockSize);
        }
    }

    @Test
    @DisplayName("identical across seeds and draw counts, including past the parallel threshold")
    void identicalAcrossSeedsAndSizes() {
        double[] sample = syntheticReturns(300);

        for (long seed : new long[] {0L, 1L, -1L, Long.MIN_VALUE, 987654321L}) {
            for (int draws : new int[] {1, 255, 256, 5_000}) {
                assertArrayEquals(
                        NATIVE_ENGINE.bootstrapMedians(sample, draws, 20, seed, true),
                        NATIVE_ENGINE.bootstrapMedians(sample, draws, 20, seed, false),
                        0.0,
                        "arena and malloc paths disagree at seed " + seed + ", draws " + draws);
            }
        }
    }

    @Test
    @DisplayName("running the arena path twice gives the same answer, so per-thread reuse does not leak")
    void repeatedArenaRunsAgree() {
        double[] sample = syntheticReturns(400);

        assertArrayEquals(
                NATIVE_ENGINE.bootstrapMedians(sample, 10_000, 25, 7L, true),
                NATIVE_ENGINE.bootstrapMedians(sample, 10_000, 25, 7L, true),
                0.0);
    }

    @Test
    @DisplayName("an even-length resample averages its two middle values")
    void evenLengthResampleAveragesMiddleValues() {
        // block_size == n means every draw resamples the whole series as one
        // block starting at index 0, so the resample equals the sample itself
        // (up to the fixed seed choosing start 0, which it must here since
        // starts == 1) and the median is deterministic and hand-checkable.
        double[] sample = {1.0, 2.0, 3.0, 4.0};

        double[] medians = NATIVE_ENGINE.bootstrapMedians(sample, 1, 4, 0L, true);

        assertArrayEquals(new double[] {2.5}, medians, 0.0);
    }

    @Test
    @DisplayName("rejects the same bad arguments the mean kernel rejects")
    void rejectsTheSameBadArguments() {
        double[] sample = syntheticReturns(50);

        assertThrows(IllegalArgumentException.class,
                () -> NATIVE_ENGINE.bootstrapMedians(sample, 10, 51, 1L, true));
        assertThrows(IllegalArgumentException.class,
                () -> NATIVE_ENGINE.bootstrapMedians(new double[0], 10, 1, 1L, true));
        assertThrows(IllegalArgumentException.class,
                () -> NATIVE_ENGINE.bootstrapMedians(sample, 0, 1, 1L, true));
    }
}
