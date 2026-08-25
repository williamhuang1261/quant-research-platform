package io.github.williamhuang1261.qrp.stats;

import java.util.Arrays;

/**
 * Resamples a return series into many alternative equity paths.
 *
 * <p>A backtest reports the one ordering of returns that history happened to
 * produce. Reordering them in blocks answers a different and more useful
 * question: across the paths this strategy could plausibly have taken, how deep
 * do the drawdowns get, and how often does the account end below where it
 * started? A single 24 % drawdown is an anecdote; the distribution it came from
 * is a risk statement.
 *
 * <p>Every path is reproducible from {@code seed} and its index, so a report can
 * be regenerated exactly from the numbers printed beside it.
 */
public final class MonteCarloSimulation {

    /**
     * @param paths            number of simulated paths
     * @param finalEquity      interval over the terminal account value
     * @param maxDrawdown      interval over the deepest peak-to-trough fall of each path
     * @param medianFinalEquity the 50th percentile terminal value
     * @param probabilityOfLoss fraction of paths ending below the initial equity
     */
    public record Report(
            int paths,
            ConfidenceInterval finalEquity,
            ConfidenceInterval maxDrawdown,
            double medianFinalEquity,
            double probabilityOfLoss) {
    }

    public Report run(double[] periodReturns, double initialEquity, int paths, int blockSize,
            double level, long seed) {
        Bootstrap.validate(periodReturns, paths, blockSize);
        if (!Double.isFinite(initialEquity) || initialEquity <= 0.0) {
            throw new IllegalArgumentException(
                    "initialEquity must be finite and positive, got: " + initialEquity);
        }
        if (!(level > 0.0 && level < 1.0)) {
            throw new IllegalArgumentException("level must lie strictly in (0, 1), got: " + level);
        }

        double[] finals = new double[paths];
        double[] drawdowns = new double[paths];
        int losses = 0;

        for (int path = 0; path < paths; path++) {
            double[] resampled = Bootstrap.draw(periodReturns, blockSize, seed, path);
            double[] equity = EquityCurve.fromReturns(initialEquity, resampled);
            finals[path] = equity[equity.length - 1];
            drawdowns[path] = EquityCurve.maxDrawdown(equity);
            if (finals[path] < initialEquity) {
                losses++;
            }
        }

        Arrays.sort(finals);
        Arrays.sort(drawdowns);
        double tail = (1.0 - level) / 2.0;

        return new Report(
                paths,
                new ConfidenceInterval(
                        Percentiles.ofSorted(finals, 0.5),
                        Percentiles.ofSorted(finals, tail),
                        Percentiles.ofSorted(finals, 1.0 - tail),
                        level),
                new ConfidenceInterval(
                        Percentiles.ofSorted(drawdowns, 0.5),
                        Percentiles.ofSorted(drawdowns, tail),
                        Percentiles.ofSorted(drawdowns, 1.0 - tail),
                        level),
                Percentiles.ofSorted(finals, 0.5),
                (double) losses / paths);
    }
}
