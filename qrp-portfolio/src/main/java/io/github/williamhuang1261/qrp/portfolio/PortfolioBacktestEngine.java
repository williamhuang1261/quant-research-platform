package io.github.williamhuang1261.qrp.portfolio;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.Signal;
import io.github.williamhuang1261.qrp.core.spi.Strategy;
import io.github.williamhuang1261.qrp.engine.BacktestEngine;
import io.github.williamhuang1261.qrp.engine.BacktestRequest;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.ExecutionModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Several instruments, one capital base, rebalanced on a schedule through a
 * {@link PortfolioOptimizer}.
 *
 * <p>This does not duplicate {@link BacktestEngine}'s fill logic: at every
 * rebalance date the target weights split the portfolio's current capital
 * across instruments, and each instrument's slice is sized, filled and marked
 * to market by handing a fresh {@link BacktestRequest} for the bars until the
 * next rebalance to the existing, unmodified {@link BacktestEngine}. The
 * sleeve strategy inside that request is trivial by design — "hold fully
 * invested" — because the allocation decision already happened; the engine's
 * job is only to turn that decision into fills against real cost and slippage,
 * exactly as it does for a single instrument.
 *
 * <p><b>What this composition does not preserve</b>: {@link BacktestEngine}
 * always starts a run flat, with no notion of a position carried in from
 * outside. Composing it per rebalance period therefore means every sleeve
 * transacts at every rebalance boundary — buying back in with its new
 * allocation — even when an instrument's target weight did not change between
 * two consecutive rebalances. {@link PortfolioBacktestResult#totalTurnover()}
 * is therefore computed from the target-weight deltas the optimizer actually
 * produced (the same quantity {@link PortfolioConstraints#maxTurnover()}
 * bounds), not from the literal fills this composition happens to make, so it
 * stays a faithful measure of the requested rebalancing intensity.
 */
public final class PortfolioBacktestEngine {

    /** Minimum weight treated as "allocated"; anything smaller sits out the period entirely. */
    private static final double MIN_ALLOCATED_WEIGHT = 1e-12;

    /** A sleeve that is always fully invested: the allocation decision already happened upstream. */
    private static final Strategy FULLY_INVESTED_SLEEVE = new FullyInvestedSleeve();

    private PortfolioBacktestEngine() {
    }

    /** How often the portfolio rebalances, expressed in trading bars. */
    public enum RebalanceFrequency {
        WEEKLY(5),
        MONTHLY(21);

        private final int bars;

        RebalanceFrequency(int bars) {
            this.bars = bars;
        }

        public int bars() {
            return bars;
        }
    }

    /**
     * @param series                 one aligned bar series per instrument — same length, same
     *                               bar-for-bar dates, in the order every other argument follows
     * @param expectedReturnViews    one per-bar expected-return view per instrument, same order
     *                               and length as {@code series}; a {@code NaN} at a rebalance
     *                               date (still warming up) is treated as a flat 0.0 view
     * @param rebalanceFrequency     how often the portfolio is rebalanced
     * @param covarianceLookbackBars trailing close-to-close return observations the covariance
     *                               estimate at each rebalance is built from; at least 2
     * @param optimizer              turns views, covariance and the previous weights into new weights
     * @param constraints            bounds the optimizer must respect exactly
     * @param execution              the fill model each instrument's sleeve trades through
     * @param initialCash            starting capital for the whole portfolio, positive
     */
    public static PortfolioBacktestResult run(
            List<BarSeries> series,
            List<DoubleSeries> expectedReturnViews,
            RebalanceFrequency rebalanceFrequency,
            int covarianceLookbackBars,
            PortfolioOptimizer optimizer,
            PortfolioConstraints constraints,
            ExecutionModel execution,
            double initialCash) {
        Objects.requireNonNull(rebalanceFrequency, "rebalanceFrequency");
        return run(series, expectedReturnViews, rebalanceFrequency.bars(), covarianceLookbackBars,
                optimizer, constraints, execution, initialCash);
    }

    /** Same as the {@link RebalanceFrequency} overload, with the rebalance interval given directly in bars. */
    public static PortfolioBacktestResult run(
            List<BarSeries> series,
            List<DoubleSeries> expectedReturnViews,
            int rebalanceBars,
            int covarianceLookbackBars,
            PortfolioOptimizer optimizer,
            PortfolioConstraints constraints,
            ExecutionModel execution,
            double initialCash) {
        validate(series, expectedReturnViews, rebalanceBars, covarianceLookbackBars,
                optimizer, constraints, execution, initialCash);

        int n = series.size();
        int barCount = series.get(0).size();
        List<String> instruments = new ArrayList<>(n);
        double[][] closes = new double[n][];
        for (int i = 0; i < n; i++) {
            instruments.add(series.get(i).instrument().symbol());
            closes[i] = series.get(i).closes();
        }

        double[] aggregateEquity = new double[barCount];
        java.util.Arrays.fill(aggregateEquity, initialCash);

        List<PortfolioBacktestResult.Rebalance> rebalances = new ArrayList<>();
        double[] previousWeights = new double[n];
        double capital = initialCash;
        double totalTurnover = 0.0;

        int t = covarianceLookbackBars;
        while (t <= barCount - 2) {
            int segmentEnd = Math.min(t + rebalanceBars, barCount - 1);

            double[][] windowReturns = trailingReturns(closes, t, covarianceLookbackBars);
            double[][] covariance = CovarianceEstimator.estimate(windowReturns);
            double[] expectedReturns = viewsAt(expectedReturnViews, t);

            double[] weights = optimizer.optimize(expectedReturns, covariance, previousWeights, constraints);
            totalTurnover += totalAbsoluteChange(weights, previousWeights);
            double[] riskContribution = riskContribution(weights, covariance);
            rebalances.add(new PortfolioBacktestResult.Rebalance(t, weights, riskContribution));

            int segmentLength = segmentEnd - t + 1;
            double[] segmentAggregate = new double[segmentLength];
            double[] segmentFinalEquity = new double[n];

            for (int i = 0; i < n; i++) {
                double allocated = weights[i] * capital;
                if (weights[i] <= MIN_ALLOCATED_WEIGHT || allocated <= 0.0) {
                    continue;
                }
                BarSeries segmentSeries = series.get(i).slice(t, segmentEnd + 1);
                BacktestResult sleeveResult = BacktestEngine.run(new BacktestRequest(
                        segmentSeries, FULLY_INVESTED_SLEEVE, Params.empty(), execution, allocated));
                segmentFinalEquity[i] = sleeveResult.finalEquity();
                DoubleSeries sleeveEquity = sleeveResult.equityCurve();
                for (int j = 0; j < segmentLength; j++) {
                    segmentAggregate[j] += sleeveEquity.get(j);
                }
            }

            for (int j = 0; j < segmentLength; j++) {
                aggregateEquity[t + j] = segmentAggregate[j];
            }

            double newCapital = 0.0;
            for (double value : segmentFinalEquity) {
                newCapital += value;
            }
            capital = newCapital;
            previousWeights = weights;
            t = segmentEnd;
        }

        return new PortfolioBacktestResult(
                instruments, DoubleSeries.of(aggregateEquity), rebalances, totalTurnover);
    }

    private static double[][] trailingReturns(double[][] closes, int endInclusive, int lookbackBars) {
        int n = closes.length;
        double[][] returns = new double[n][lookbackBars];
        int start = endInclusive - lookbackBars;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < lookbackBars; k++) {
                double previous = closes[i][start + k];
                double current = closes[i][start + k + 1];
                returns[i][k] = current / previous - 1.0;
            }
        }
        return returns;
    }

    private static double[] viewsAt(List<DoubleSeries> expectedReturnViews, int index) {
        int n = expectedReturnViews.size();
        double[] views = new double[n];
        for (int i = 0; i < n; i++) {
            double value = expectedReturnViews.get(i).get(index);
            views[i] = Double.isNaN(value) ? 0.0 : value;
        }
        return views;
    }

    private static double totalAbsoluteChange(double[] weights, double[] previousWeights) {
        double total = 0.0;
        for (int i = 0; i < weights.length; i++) {
            total += Math.abs(weights[i] - previousWeights[i]);
        }
        return total;
    }

    /** {@code RC_i = w_i * (Sigma w)_i}, the same definition {@link EqualRiskContributionOptimizer} targets. */
    private static double[] riskContribution(double[] weights, double[][] covariance) {
        int n = weights.length;
        double[] contribution = new double[n];
        for (int i = 0; i < n; i++) {
            double sigmaWi = 0.0;
            for (int j = 0; j < n; j++) {
                sigmaWi += covariance[i][j] * weights[j];
            }
            contribution[i] = weights[i] * sigmaWi;
        }
        return contribution;
    }

    private static void validate(
            List<BarSeries> series,
            List<DoubleSeries> expectedReturnViews,
            int rebalanceBars,
            int covarianceLookbackBars,
            PortfolioOptimizer optimizer,
            PortfolioConstraints constraints,
            ExecutionModel execution,
            double initialCash) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(expectedReturnViews, "expectedReturnViews");
        Objects.requireNonNull(optimizer, "optimizer");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(execution, "execution");

        if (series.isEmpty()) {
            throw new IllegalArgumentException("need at least one instrument, got 0");
        }
        if (expectedReturnViews.size() != series.size()) {
            throw new IllegalArgumentException(
                    "expectedReturnViews must have one entry per instrument: series has " + series.size()
                            + ", expectedReturnViews has " + expectedReturnViews.size());
        }
        int barCount = series.get(0).size();
        for (int i = 0; i < series.size(); i++) {
            BarSeries s = series.get(i);
            Objects.requireNonNull(s, "series[" + i + "]");
            if (s.size() != barCount) {
                throw new IllegalArgumentException(
                        "every instrument must have the same number of bars; instrument 0 has "
                                + barCount + ", instrument " + i + " has " + s.size());
            }
            DoubleSeries view = expectedReturnViews.get(i);
            Objects.requireNonNull(view, "expectedReturnViews[" + i + "]");
            if (view.size() != barCount) {
                throw new IllegalArgumentException(
                        "expectedReturnViews[" + i + "] must have " + barCount + " entries to match series, got: "
                                + view.size());
            }
        }
        if (rebalanceBars < 1) {
            throw new IllegalArgumentException("rebalanceBars must be positive, got: " + rebalanceBars);
        }
        if (covarianceLookbackBars < 2) {
            throw new IllegalArgumentException(
                    "covarianceLookbackBars must be at least 2, got: " + covarianceLookbackBars);
        }
        if (!Double.isFinite(initialCash) || initialCash <= 0.0) {
            throw new IllegalArgumentException("initialCash must be finite and positive, got: " + initialCash);
        }
        if (covarianceLookbackBars > barCount - 2) {
            throw new IllegalArgumentException(
                    "covarianceLookbackBars (" + covarianceLookbackBars + ") leaves no room for a single "
                            + "rebalance segment in a " + barCount + "-bar series");
        }
    }

    /** Always signals fully long: the sleeve exists to be filled, not to decide anything. */
    private static final class FullyInvestedSleeve implements Strategy {
        @Override
        public String id() {
            return "portfolio-sleeve";
        }

        @Override
        public Signal onBar(BarSeries visible, Params params) {
            return Signal.fullyLong();
        }
    }
}
