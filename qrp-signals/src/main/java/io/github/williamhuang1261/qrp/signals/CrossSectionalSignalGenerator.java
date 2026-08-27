package io.github.williamhuang1261.qrp.signals;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.spi.Indicator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns one {@link Indicator}'s output across several instruments into a
 * per-instrument expected-return forecast, in the exact shape
 * {@code qrp-portfolio}'s {@code PortfolioBacktestEngine.run()} already
 * accepts as {@code expectedReturnViews}.
 *
 * <p>The forecast is built from the indicator's <em>cross-sectional rank</em>
 * at each bar, not its raw magnitude: at every bar, every instrument's
 * indicator value is rank-transformed across the instrument universe
 * ({@link RankTransform}), centered so the middle rank forecasts zero, and
 * scaled linearly to {@code targetSpread} — the gap between the most- and
 * least-favoured instrument's forecast. Ranking rather than reading the raw
 * value directly matters because not every indicator's output is
 * comparable across instruments at face value (a simple moving average is in
 * price units, and a $500 instrument's SMA is not "more bullish" than a $50
 * one's); rank order is comparable by construction, and it is the same
 * quantity {@link InformationCoefficient} scores the forecast against.
 *
 * <p>This class never looks at a forward bar: it only reads
 * {@link Indicator#compute(BarSeries, Params)}'s own output, which by the
 * SPI's contract is itself computed causally (a trailing window, {@code NaN}
 * before warmup). Nothing here needs {@code BarSeries.visibleAt} on top of
 * that guarantee.
 */
public final class CrossSectionalSignalGenerator {

    private CrossSectionalSignalGenerator() {
    }

    /**
     * @param series        one aligned bar series per instrument — same length,
     *                      same bar-for-bar dates, in the order the result follows
     * @param indicator     computed once per instrument; its raw output is never
     *                      returned, only its cross-sectional rank each bar
     * @param params        parameters passed to {@code indicator.compute}
     * @param targetSpread  the forecast gap between the top- and bottom-ranked
     *                      instrument at any bar where every instrument's
     *                      indicator is defined; must be positive
     * @return one {@link DoubleSeries} per instrument, same order and length as
     *         {@code series}; a bar where any instrument's indicator has not
     *         warmed up yet forecasts {@code NaN} for every instrument that bar
     */
    public static List<DoubleSeries> generate(
            List<BarSeries> series, Indicator indicator, Params params, double targetSpread) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(indicator, "indicator");
        Objects.requireNonNull(params, "params");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("need at least one instrument, got 0");
        }
        if (series.size() < 3) {
            throw new IllegalArgumentException(
                    "need at least 3 instruments for a cross-sectional rank, got: " + series.size());
        }
        if (!(targetSpread > 0.0)) {
            throw new IllegalArgumentException("targetSpread must be positive, got: " + targetSpread);
        }

        int n = series.size();
        int barCount = series.get(0).size();
        for (int i = 0; i < n; i++) {
            int size = series.get(i).size();
            if (size != barCount) {
                throw new IllegalArgumentException(
                        "every instrument must have the same number of bars; instrument 0 has "
                                + barCount + ", instrument " + i + " has " + size);
            }
        }

        DoubleSeries[] indicatorValues = new DoubleSeries[n];
        for (int i = 0; i < n; i++) {
            indicatorValues[i] = indicator.compute(series.get(i), params);
        }

        double[][] forecast = new double[n][barCount];
        double midRank = (n + 1) / 2.0;
        double[] cross = new double[n];
        for (int t = 0; t < barCount; t++) {
            boolean allDefined = true;
            for (int i = 0; i < n; i++) {
                double value = indicatorValues[i].get(t);
                if (Double.isNaN(value)) {
                    allDefined = false;
                    break;
                }
                cross[i] = value;
            }
            if (!allDefined) {
                for (int i = 0; i < n; i++) {
                    forecast[i][t] = Double.NaN;
                }
                continue;
            }
            double[] ranks = RankTransform.ranks(cross);
            for (int i = 0; i < n; i++) {
                double normalized = (ranks[i] - midRank) / (n - 1);
                forecast[i][t] = normalized * targetSpread;
            }
        }

        List<DoubleSeries> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(DoubleSeries.of(forecast[i]));
        }
        return result;
    }
}
