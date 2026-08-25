package io.github.williamhuang1261.qrp.stats;

/**
 * An interval estimate.
 *
 * @param pointEstimate the statistic computed on the observed sample
 * @param lower         lower bound
 * @param upper         upper bound
 * @param level         coverage, e.g. 0.95
 */
public record ConfidenceInterval(double pointEstimate, double lower, double upper, double level) {

    public ConfidenceInterval {
        if (!(level > 0.0 && level < 1.0)) {
            throw new IllegalArgumentException("level must lie strictly in (0, 1), got: " + level);
        }
        if (lower > upper) {
            throw new IllegalArgumentException("lower (" + lower + ") exceeds upper (" + upper + ")");
        }
    }

    public boolean contains(double value) {
        return value >= lower && value <= upper;
    }

    public double width() {
        return upper - lower;
    }

    /** True when the interval excludes zero, the usual "is this real" question. */
    public boolean excludesZero() {
        return !contains(0.0);
    }
}
