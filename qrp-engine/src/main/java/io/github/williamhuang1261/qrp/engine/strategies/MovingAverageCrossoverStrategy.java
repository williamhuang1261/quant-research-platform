package io.github.williamhuang1261.qrp.engine.strategies;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.PluginRegistry;
import io.github.williamhuang1261.qrp.core.Signal;
import io.github.williamhuang1261.qrp.core.spi.Indicator;
import io.github.williamhuang1261.qrp.core.spi.Strategy;

/**
 * Long while the fast moving average sits above the slow one, flat otherwise.
 *
 * <p>The textbook example, published here precisely because it has no edge worth
 * hiding: it exists to exercise the engine end to end, and to give the README a
 * result a reader can reproduce.
 *
 * <p>It resolves {@code sma} through the {@link PluginRegistry} rather than
 * importing an implementation, so the engine module never compiles against a
 * concrete indicator. Whatever provides {@code sma} on the classpath is what
 * runs, which is the same seam a private indicator jar arrives through.
 *
 * <p>Both averages are computed once in {@link #onStart}, over the full series.
 * That is safe only because a moving average is causal: its value at bar
 * {@code i} depends on bars up to {@code i} and nothing after. An indicator that
 * looked ahead — a centred average, a series-wide normalisation — could not be
 * precomputed this way without leaking the future into every decision.
 *
 * <p>Parameters: {@code fast}, {@code slow} (fast &lt; slow).
 */
public final class MovingAverageCrossoverStrategy implements Strategy {

    public static final String FAST = "fast";
    public static final String SLOW = "slow";
    private static final String SMA_ID = "sma";

    private DoubleSeries fastAverage;
    private DoubleSeries slowAverage;

    @Override
    public String id() {
        return "sma-crossover";
    }

    @Override
    public String displayName() {
        return "Moving Average Crossover";
    }

    @Override
    public int warmup(Params params) {
        return periods(params)[1] - 1;
    }

    @Override
    public void onStart(BarSeries fullSeries, Params params) {
        int[] periods = periods(params);
        Indicator sma = PluginRegistry.load(Indicator.class, Indicator::id).require(SMA_ID);
        fastAverage = sma.compute(fullSeries, Params.of("period", periods[0]));
        slowAverage = sma.compute(fullSeries, Params.of("period", periods[1]));
    }

    @Override
    public Signal onBar(BarSeries visible, Params params) {
        if (fastAverage == null) {
            throw new IllegalStateException(id() + ".onStart must run before onBar");
        }
        int index = visible.size() - 1;
        if (!fastAverage.isDefined(index) || !slowAverage.isDefined(index)) {
            return Signal.flat();
        }
        return fastAverage.get(index) > slowAverage.get(index) ? Signal.fullyLong() : Signal.flat();
    }

    private static int[] periods(Params params) {
        int fast = params.requireInt(FAST);
        int slow = params.requireInt(SLOW);
        if (fast < 1) {
            throw new IllegalArgumentException("fast must be at least 1, got: " + fast);
        }
        if (fast >= slow) {
            throw new IllegalArgumentException(
                    "fast (" + fast + ") must be shorter than slow (" + slow + ")");
        }
        return new int[] {fast, slow};
    }
}
