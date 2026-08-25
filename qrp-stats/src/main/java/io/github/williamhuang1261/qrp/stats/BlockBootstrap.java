package io.github.williamhuang1261.qrp.stats;

import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import java.util.Arrays;
import java.util.Objects;

/**
 * Percentile confidence intervals for the mean of a serially correlated series.
 *
 * <p>The question this answers is the one a backtest cannot: the strategy
 * returned what it returned, but how much of that is the sample it happened to
 * be run on? An interval that straddles zero says the result is indistinguishable
 * from noise at this sample size, which is worth knowing before anyone trades it.
 */
public final class BlockBootstrap {

    private final ComputeEngine engine;

    /** Uses the fastest available engine. */
    public BlockBootstrap() {
        this(ComputeEngines.best());
    }

    public BlockBootstrap(ComputeEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public String engineId() {
        return engine.id();
    }

    /**
     * @param level coverage, e.g. 0.95 for a 2.5 %/97.5 % percentile interval
     * @param seed  the same seed reproduces the interval exactly
     */
    public ConfidenceInterval meanInterval(
            double[] sample, int draws, int blockSize, double level, long seed) {
        if (!(level > 0.0 && level < 1.0)) {
            throw new IllegalArgumentException("level must lie strictly in (0, 1), got: " + level);
        }
        double[] means = engine.bootstrapMeans(sample, draws, blockSize, seed);
        Arrays.sort(means);

        double tail = (1.0 - level) / 2.0;
        return new ConfidenceInterval(
                mean(sample),
                Percentiles.ofSorted(means, tail),
                Percentiles.ofSorted(means, 1.0 - tail),
                level);
    }

    /** The raw bootstrap distribution, for callers that want to plot it. */
    public double[] meanDistribution(double[] sample, int draws, int blockSize, long seed) {
        return engine.bootstrapMeans(sample, draws, blockSize, seed);
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
