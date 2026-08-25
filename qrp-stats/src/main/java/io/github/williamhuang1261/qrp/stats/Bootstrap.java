package io.github.williamhuang1261.qrp.stats;

/**
 * The moving-block bootstrap scheme, written once so the Java engine, the native
 * kernel and the Monte Carlo simulator all resample identically.
 *
 * <p>Blocks rather than individual observations: financial returns are not
 * independent, and resampling them one at a time destroys the serial structure
 * that produces drawdowns. A drawdown distribution built from an i.i.d. bootstrap
 * is optimistic for the same reason a shuffled deck has no runs.
 *
 * <p>The scheme, in full, so the C++ transcription has something to be checked
 * against: draw {@code d} uses {@code SplitMix64.forDraw(seed, d)}; it draws
 * {@code ceil(n / blockSize)} block starts uniformly from
 * {@code [0, n - blockSize]}, concatenates the blocks in draw order, and
 * truncates the result to {@code n} observations.
 */
public final class Bootstrap {

    private Bootstrap() {
    }

    static void validate(double[] sample, int draws, int blockSize) {
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
    }

    /** One resampled series of the same length as {@code sample}. */
    public static double[] draw(double[] sample, int blockSize, long seed, int drawIndex) {
        validate(sample, drawIndex + 1, blockSize);
        SplitMix64 random = SplitMix64.forDraw(seed, drawIndex);
        int n = sample.length;
        int starts = n - blockSize + 1;
        double[] resampled = new double[n];

        int filled = 0;
        while (filled < n) {
            int start = random.nextInt(starts);
            int length = Math.min(blockSize, n - filled);
            System.arraycopy(sample, start, resampled, filled, length);
            filled += length;
        }
        return resampled;
    }

    /** Mean of one resampled series, without materialising it. */
    static double drawMean(double[] sample, int blockSize, long seed, int drawIndex) {
        SplitMix64 random = SplitMix64.forDraw(seed, drawIndex);
        int n = sample.length;
        int starts = n - blockSize + 1;

        double sum = 0.0;
        int filled = 0;
        while (filled < n) {
            int start = random.nextInt(starts);
            int length = Math.min(blockSize, n - filled);
            for (int i = 0; i < length; i++) {
                sum += sample[start + i];
            }
            filled += length;
        }
        return sum / n;
    }
}
