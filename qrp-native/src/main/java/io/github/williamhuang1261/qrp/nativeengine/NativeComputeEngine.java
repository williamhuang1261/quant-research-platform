package io.github.williamhuang1261.qrp.nativeengine;

import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The OpenMP kernel, behind the same interface as the Java one.
 *
 * <p>It exists to make one operation faster — bootstrap resampling, which is
 * embarrassingly parallel — and it is required to produce numbers identical to
 * the portable engine while doing so. That requirement is what shapes the C++:
 * the generator is transcribed rather than reimplemented, and only the loop over
 * draws is parallel, because reordering the additions inside a draw would change
 * the last bits of every mean.
 *
 * <p>Unavailability is normal. No toolchain, no OpenMP runtime, a stale library
 * or a different architecture all leave {@link #isAvailable()} false with a
 * readable {@link #unavailableReason()}, and the platform selects the Java
 * engine instead.
 */
public final class NativeComputeEngine implements ComputeEngine {

    private final KernelLibrary library = KernelLibrary.instance();

    @Override
    public String id() {
        return "openmp";
    }

    @Override
    public boolean isAvailable() {
        return library.isAvailable();
    }

    /** Why the kernel is not in use, or empty when it is. */
    @Override
    public Optional<String> unavailableReason() {
        return Optional.ofNullable(library.unavailableReason());
    }

    public Optional<Path> libraryPath() {
        return library.path();
    }

    /** Threads OpenMP will use, or 1 when the library was built without it. */
    public int openmpThreads() {
        return library.openmpThreads();
    }

    @Override
    public double[] rollingMean(double[] values, int window) {
        if (window < 1) {
            throw new IllegalArgumentException("window must be at least 1, got: " + window);
        }
        return library.rollingMean(values, window);
    }

    @Override
    public double[] bootstrapMeans(double[] sample, int draws, int blockSize, long seed) {
        // Validated on this side so both engines reject the same inputs the same
        // way; the kernel returns silently on bad arguments rather than trapping.
        if (sample.length == 0) {
            throw new IllegalArgumentException("sample must not be empty");
        }
        if (draws < 1) {
            throw new IllegalArgumentException("draws must be at least 1, got: " + draws);
        }
        if (blockSize < 1) {
            throw new IllegalArgumentException("blockSize must be at least 1, got: " + blockSize);
        }
        if (blockSize > sample.length) {
            throw new IllegalArgumentException(
                    "blockSize (" + blockSize + ") exceeds the sample length (" + sample.length + ")");
        }
        return library.bootstrapMeans(sample, draws, blockSize, seed);
    }

    /**
     * Medians of {@code draws} block-bootstrap resamples of {@code sample}.
     * Not part of {@link ComputeEngine}: unlike the mean, this has no portable
     * Java counterpart in this platform yet, so it is exposed here only, the
     * same way {@link #openmpThreads()} is native-only.
     *
     * @param useArena selects the native scratch-buffer strategy per draw: the
     *                 mmap-backed arena allocator when true, a malloc-backed
     *                 fallback when false. Both produce identical output.
     */
    public double[] bootstrapMedians(double[] sample, int draws, int blockSize, long seed, boolean useArena) {
        if (sample.length == 0) {
            throw new IllegalArgumentException("sample must not be empty");
        }
        if (draws < 1) {
            throw new IllegalArgumentException("draws must be at least 1, got: " + draws);
        }
        if (blockSize < 1) {
            throw new IllegalArgumentException("blockSize must be at least 1, got: " + blockSize);
        }
        if (blockSize > sample.length) {
            throw new IllegalArgumentException(
                    "blockSize (" + blockSize + ") exceeds the sample length (" + sample.length + ")");
        }
        return library.bootstrapMedians(sample, draws, blockSize, seed, useArena);
    }
}
