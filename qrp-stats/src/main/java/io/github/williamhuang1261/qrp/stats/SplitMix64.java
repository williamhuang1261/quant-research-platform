package io.github.williamhuang1261.qrp.stats;

/**
 * The splitmix64 generator, specified here rather than taken from the platform.
 *
 * <p>The reason is cross-language reproducibility. A resampling result has to be
 * identical whether it was produced by the Java compute engine or by the C++
 * kernel, and no two standard libraries agree on the internals of their random
 * generators. splitmix64 is five lines of arithmetic, so both implementations can
 * be the same algorithm rather than the same intent.
 *
 * <p>Each draw of a bootstrap gets <em>its own</em> generator, seeded from the
 * run seed and the draw index ({@link #forDraw}). Consuming one shared stream
 * would make the result depend on the order draws are executed in, which is
 * exactly what a parallel loop does not guarantee. Per-draw seeding makes the
 * output independent of thread count and scheduling.
 *
 * <p>Reference: Steele, Lea and Flood, "Fast splittable pseudorandom number
 * generators" (2014); the constants are the published ones.
 */
public final class SplitMix64 {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    public SplitMix64(long seed) {
        this.state = seed;
    }

    /** A generator for one draw, independent of every other draw in the run. */
    public static SplitMix64 forDraw(long seed, int drawIndex) {
        return new SplitMix64(seed + (long) drawIndex * GOLDEN_GAMMA);
    }

    public long nextLong() {
        long z = (state += GOLDEN_GAMMA);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Uniform in {@code [0, bound)} by unsigned remainder.
     *
     * <p>Modulo leaves a bias of order {@code bound / 2^64}. For a bound of a
     * million that is below 1e-13, far under the sampling error of any bootstrap
     * this drives, and it keeps the C++ kernel a transcription rather than a
     * reimplementation of rejection sampling.
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, got: " + bound);
        }
        return (int) Long.remainderUnsigned(nextLong(), bound);
    }

    /** Uniform in {@code [0, 1)}, using the top 53 bits. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }
}
