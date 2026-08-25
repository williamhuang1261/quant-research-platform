package io.github.williamhuang1261.qrp.nativeengine;

import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import java.util.Locale;

/**
 * Prints what the native kernel is worth on this machine, and says so plainly
 * when it is worth nothing because it is not installed.
 *
 * <p>Not a JMH harness: it warms up, repeats, and reports the best of several
 * runs, which is enough to distinguish "meaningfully faster" from "not" without
 * adding a benchmarking framework to a project this size. Numbers from it belong
 * in a README with the machine named, not in a claim about C++ in general.
 *
 * <p>Run with: {@code mvn -pl qrp-native exec:java} (test classpath, since the
 * portable engine it compares against is a test dependency here).
 */
public final class NativeBenchmark {

    private static final int SAMPLE_SIZE = 2_000;
    private static final int BLOCK_SIZE = 40;
    private static final int WARMUP_RUNS = 3;
    private static final int TIMED_RUNS = 5;

    private NativeBenchmark() {
    }

    public static void main(String[] args) {
        NativeComputeEngine nativeEngine = new NativeComputeEngine();
        if (!nativeEngine.isAvailable()) {
            System.out.println("native kernel unavailable: " + nativeEngine.unavailableReason().orElseThrow());
            System.out.println("the platform runs on the Java engine; build the kernel with: make -C native");
            return;
        }

        ComputeEngine javaParallel = new io.github.williamhuang1261.qrp.stats.JavaComputeEngine();
        ComputeEngine javaSequential = io.github.williamhuang1261.qrp.stats.JavaComputeEngine.sequential();
        System.out.println("library : " + nativeEngine.libraryPath().orElseThrow());
        System.out.println("threads : " + nativeEngine.openmpThreads() + " (OpenMP)");
        System.out.println("sample  : " + SAMPLE_SIZE + " observations, block " + BLOCK_SIZE);
        System.out.println();
        System.out.printf(Locale.ROOT, "%9s  %11s  %11s  %11s  %10s  %10s%n",
                "draws", "java 1t (ms)", "java par(ms)", "openmp (ms)", "vs 1t", "vs par");

        double[] sample = syntheticReturns(SAMPLE_SIZE);
        for (int draws : new int[] {1_000, 10_000, 100_000}) {
            double sequentialMillis = timeBest(javaSequential, sample, draws);
            double parallelMillis = timeBest(javaParallel, sample, draws);
            double nativeMillis = timeBest(nativeEngine, sample, draws);
            System.out.printf(Locale.ROOT, "%9d  %11.1f  %11.1f  %11.1f  %9.2fx  %9.2fx%n",
                    draws, sequentialMillis, parallelMillis, nativeMillis,
                    sequentialMillis / nativeMillis, parallelMillis / nativeMillis);
        }
    }

    private static double timeBest(ComputeEngine engine, double[] sample, int draws) {
        for (int run = 0; run < WARMUP_RUNS; run++) {
            engine.bootstrapMeans(sample, draws, BLOCK_SIZE, 1L);
        }
        long best = Long.MAX_VALUE;
        for (int run = 0; run < TIMED_RUNS; run++) {
            long start = System.nanoTime();
            engine.bootstrapMeans(sample, draws, BLOCK_SIZE, 1L);
            best = Math.min(best, System.nanoTime() - start);
        }
        return best / 1_000_000.0;
    }

    private static double[] syntheticReturns(int n) {
        double[] returns = new double[n];
        for (int i = 0; i < n; i++) {
            returns[i] = 0.0004 + 0.01 * Math.sin(i / 7.0);
        }
        return returns;
    }
}
