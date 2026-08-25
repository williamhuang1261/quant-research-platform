package io.github.williamhuang1261.qrp.stats;

import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import java.util.stream.IntStream;

/**
 * The portable implementation of the compute kernels: plain Java, parallel
 * across draws, always available.
 *
 * <p>Parallelism is safe here precisely because each bootstrap draw carries its
 * own {@link SplitMix64} seeded from the draw index. Results do not depend on
 * how many threads ran or in what order they finished, which is what lets the
 * same test assert the same numbers from the native kernel in a later step.
 */
public final class JavaComputeEngine implements ComputeEngine {

    /** Below this many draws, the fork/join overhead costs more than it saves. */
    private static final int PARALLEL_THRESHOLD = 256;

    private final boolean allowParallel;

    /** Parallel across draws once there are enough of them to pay for it. */
    public JavaComputeEngine() {
        this(true);
    }

    private JavaComputeEngine(boolean allowParallel) {
        this.allowParallel = allowParallel;
    }

    /**
     * A single-threaded engine. Exists so the parallel path can be compared
     * against a real baseline: measuring OpenMP against parallel Java answers a
     * different question than measuring it against one thread, and quoting the
     * first as though it were the second is how a speedup number becomes a lie.
     */
    public static JavaComputeEngine sequential() {
        return new JavaComputeEngine(false);
    }

    @Override
    public String id() {
        return allowParallel ? "java" : "java-sequential";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public double[] rollingMean(double[] values, int window) {
        if (window < 1) {
            throw new IllegalArgumentException("window must be at least 1, got: " + window);
        }
        double[] out = new double[values.length];
        java.util.Arrays.fill(out, Double.NaN);
        double sum = 0.0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= window) {
                sum -= values[i - window];
            }
            if (i >= window - 1) {
                out[i] = sum / window;
            }
        }
        return out;
    }

    @Override
    public double[] bootstrapMeans(double[] sample, int draws, int blockSize, long seed) {
        Bootstrap.validate(sample, draws, blockSize);
        double[] means = new double[draws];
        IntStream stream = IntStream.range(0, draws);
        if (allowParallel && draws >= PARALLEL_THRESHOLD) {
            stream = stream.parallel();
        }
        stream.forEach(draw -> means[draw] = Bootstrap.drawMean(sample, blockSize, seed, draw));
        return means;
    }
}
