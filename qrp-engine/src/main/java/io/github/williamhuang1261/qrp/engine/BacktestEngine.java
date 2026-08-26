package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Signal;
import io.github.williamhuang1261.qrp.core.spi.Strategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs one strategy over one series, bar by bar.
 *
 * <p>The loop is deliberately boring, because every interesting decision in a
 * backtest engine is about <em>when</em> things happen:
 *
 * <ol>
 *   <li>a fill decided on the previous bar executes at this bar's <b>open</b>;</li>
 *   <li>the account is marked to market at this bar's <b>close</b>;</li>
 *   <li>the strategy sees history up to and including this bar and states a target.</li>
 * </ol>
 *
 * <p>A decision taken on a close cannot be filled at that same close: the price
 * was not knowable until the bar ended. Filling at the next open is the earliest
 * honest execution, and it is what separates a backtest from a look-ahead. The
 * target stated on the final bar is therefore never executed, which is correct
 * rather than a rounding error.
 *
 * <p>Positions are whole shares. Fractional sizing quietly assumes a broker that
 * supports it and flatters small accounts, and rounding down is the conservative
 * direction.
 */
public final class BacktestEngine {

    private BacktestEngine() {
    }

    public static BacktestResult run(BacktestRequest request) {
        Objects.requireNonNull(request, "request");

        BarSeries series = request.series();
        Strategy strategy = request.strategy();
        ExecutionModel execution = request.execution();
        int barCount = series.size();

        strategy.onStart(series, request.params());
        int warmup = strategy.warmup(request.params());
        if (warmup < 0) {
            throw new IllegalArgumentException(
                    strategy.id() + " declares a negative warm-up: " + warmup);
        }

        double cash = request.initialCash();
        double shares = 0.0;
        double currentTarget = 0.0;
        Double pendingTarget = null;

        double[] equity = new double[barCount];
        double[] exposure = new double[barCount];
        boolean[] invested = new boolean[barCount];
        List<Trade> trades = new ArrayList<>();

        for (int i = 0; i < barCount; i++) {
            Bar bar = series.get(i);

            // 1. Execute what the previous bar decided, at this bar's open.
            if (pendingTarget != null) {
                Optional<ExecutionModel.Fill> fill = execution.fill(bar, pendingTarget, cash, shares);
                if (fill.isPresent()) {
                    ExecutionModel.Fill f = fill.get();
                    cash -= f.deltaShares() * f.price() + f.commission();
                    shares += f.deltaShares();
                    trades.add(new Trade(i, bar.timestamp(), f.deltaShares(), f.price(), bar.open(), f.commission()));
                }
                currentTarget = pendingTarget;
                pendingTarget = null;
            }

            // 2. Mark to market at the close.
            equity[i] = cash + shares * bar.close();
            exposure[i] = currentTarget;
            invested[i] = shares != 0.0;

            // 3. Decide, seeing nothing after this bar.
            if (i >= warmup) {
                Signal signal = strategy.onBar(series.visibleAt(i), request.params());
                Objects.requireNonNull(signal, strategy.id() + " returned a null signal at bar " + i);
                if (signal.targetExposure() != currentTarget) {
                    pendingTarget = signal.targetExposure();
                }
            }
        }

        PerformanceMetrics metrics = PerformanceMetrics.from(
                equity, invested, trades, series, Annualization.periodsPerYear(series.timeframe()));

        return new BacktestResult(
                series, DoubleSeries.of(equity), DoubleSeries.of(exposure), trades, metrics);
    }
}
