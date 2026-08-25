package io.github.williamhuang1261.qrp.core.spi;

/**
 * The numeric kernels the platform runs hot, behind one interface so the
 * implementation can change without touching a caller.
 *
 * <p>Two implementations are planned: a portable parallel Java one, and an
 * OpenMP kernel in C++ reached through the foreign function API. The native one
 * is optional by design. It needs a toolchain and an OpenMP runtime that a
 * reviewer's machine may not have, so {@link #isAvailable()} is what selects it,
 * and identical results from either path are asserted in tests rather than
 * assumed.
 */
public interface ComputeEngine extends AutoCloseable {

    String id();

    /** False when this engine's backing library or toolchain is missing. */
    boolean isAvailable();

    /**
     * Why this engine cannot run, or empty when it can.
     *
     * <p>Part of the contract rather than of one implementation: "the fast path
     * is off" is a question an operator asks, and an answer that requires
     * reading source code is not an answer. See {@code docs/runbook.md}.
     */
    default java.util.Optional<String> unavailableReason() {
        return isAvailable() ? java.util.Optional.empty() : java.util.Optional.of("unavailable");
    }

    /**
     * Trailing arithmetic mean over {@code window} values.
     *
     * @return an array of {@code values.length} elements whose first
     *         {@code window - 1} entries are {@link Double#NaN}
     */
    double[] rollingMean(double[] values, int window);

    /**
     * Means of {@code draws} bootstrap resamples of {@code sample}, each built
     * from contiguous blocks of {@code blockSize} to preserve serial
     * correlation. Given the same seed, every implementation must return the
     * same values, which is what makes the native path testable.
     */
    double[] bootstrapMeans(double[] sample, int draws, int blockSize, long seed);

    @Override
    default void close() {
    }
}
