package io.github.williamhuang1261.qrp.portfolio;

import io.github.williamhuang1261.qrp.core.DoubleSeries;
import java.util.List;
import java.util.Objects;

/**
 * The output of one {@link PortfolioBacktestEngine} run: the aggregate equity
 * curve across every instrument, one {@link Rebalance} per scheduled rebalance
 * date, and the total turnover accumulated across the whole run.
 *
 * @param instruments   instrument symbols, in the same order as every array in
 *                      every {@link Rebalance}
 * @param equityCurve   mark-to-market total portfolio value at each bar of the
 *                      shared series (cash before the first rebalance, the sum
 *                      of every instrument sleeve's own equity after it)
 * @param rebalances    one entry per rebalance date, in chronological order
 * @param totalTurnover sum of {@code sum(|newWeight - oldWeight|)} across every
 *                      rebalance — the same quantity {@link
 *                      PortfolioConstraints#maxTurnover()} bounds per rebalance,
 *                      accumulated over the run rather than measured from the
 *                      literal fills the per-instrument sleeves happen to make
 */
public record PortfolioBacktestResult(
        List<String> instruments,
        DoubleSeries equityCurve,
        List<Rebalance> rebalances,
        double totalTurnover) {

    public PortfolioBacktestResult {
        Objects.requireNonNull(instruments, "instruments");
        Objects.requireNonNull(equityCurve, "equityCurve");
        Objects.requireNonNull(rebalances, "rebalances");
        instruments = List.copyOf(instruments);
        rebalances = List.copyOf(rebalances);
        if (!(totalTurnover >= 0.0)) {
            throw new IllegalArgumentException("totalTurnover must be non-negative, got: " + totalTurnover);
        }
    }

    /**
     * One scheduled rebalance: the target weights the optimizer produced and
     * each instrument's realized contribution to portfolio variance at that
     * moment, {@code RC_i = w_i * (Sigma w)_i}, computed from the same
     * trailing-return covariance the optimizer was given.
     *
     * @param barIndex          index into the shared series this rebalance fired at
     * @param weights           target weight per instrument, same order as {@link #instruments()}
     * @param riskContribution  {@code w_i * (Sigma w)_i} per instrument, same order
     */
    public record Rebalance(int barIndex, double[] weights, double[] riskContribution) {

        public Rebalance {
            Objects.requireNonNull(weights, "weights");
            Objects.requireNonNull(riskContribution, "riskContribution");
            if (weights.length != riskContribution.length) {
                throw new IllegalArgumentException(
                        "weights and riskContribution must have the same length, got: "
                                + weights.length + " and " + riskContribution.length);
            }
            weights = weights.clone();
            riskContribution = riskContribution.clone();
        }
    }

    public double finalEquity() {
        return equityCurve.get(equityCurve.size() - 1);
    }

    /** Each instrument's weight averaged across every rebalance, same order as {@link #instruments()}. */
    public double[] averageWeights() {
        int n = instruments.size();
        double[] totals = new double[n];
        if (rebalances.isEmpty()) {
            return totals;
        }
        for (Rebalance rebalance : rebalances) {
            for (int i = 0; i < n; i++) {
                totals[i] += rebalance.weights()[i];
            }
        }
        for (int i = 0; i < n; i++) {
            totals[i] /= rebalances.size();
        }
        return totals;
    }

    /**
     * Each instrument's realized risk contribution averaged across every
     * rebalance, same order as {@link #instruments()} — a summary of how
     * balanced the run kept each instrument's share of portfolio variance.
     */
    public double[] averageRiskContribution() {
        int n = instruments.size();
        double[] totals = new double[n];
        if (rebalances.isEmpty()) {
            return totals;
        }
        for (Rebalance rebalance : rebalances) {
            for (int i = 0; i < n; i++) {
                totals[i] += rebalance.riskContribution()[i];
            }
        }
        for (int i = 0; i < n; i++) {
            totals[i] /= rebalances.size();
        }
        return totals;
    }
}
