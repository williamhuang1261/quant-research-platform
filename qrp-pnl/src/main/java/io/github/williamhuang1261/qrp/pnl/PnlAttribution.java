package io.github.williamhuang1261.qrp.pnl;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.engine.Annualization;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.Trade;
import java.util.List;
import java.util.Objects;

/**
 * Decomposes one backtest run's realized P&amp;L into where it actually came
 * from: the price move on the position held, and the cost of executing
 * (commission plus slippage).
 *
 * <p>{@link #priceMovePnl()} and {@link #executionCostPnl()} are derived
 * directly from the same equity curve and trade list {@link BacktestResult}
 * already produces, by rearranging the bar-by-bar identity
 * {@code equity[i] - equity[i-1] = sharesHeld * (close[i] - close[i-1])
 * + fillDelta * (close[i] - referencePrice) - commission - slippageCost}.
 * Summed across every bar, the two components add back to exactly
 * {@link #totalReconciledPnl()}, which by construction equals the run's
 * actual final-minus-initial equity change: see {@link #reconciles(double)}.
 *
 * <p>{@link #impliedCarryPnl()} is a separate, memo-only figure: the interest
 * a stated annual rate would have earned (or cost) on the cash balance
 * actually held bar to bar. The engine itself charges no interest on cash, so
 * this number is <b>not</b> part of {@link #totalReconciledPnl()} and must
 * never be added to it; it exists only to answer "what would financing this
 * position have cost," the same question a real desk's carry line answers.
 * See {@code docs/spec-pnl-attribution.md} for the full disclosure.
 */
public record PnlAttribution(double priceMovePnl, double executionCostPnl, double impliedCarryPnl) {

    public PnlAttribution {
        requireFinite(priceMovePnl, "priceMovePnl");
        requireFinite(executionCostPnl, "executionCostPnl");
        requireFinite(impliedCarryPnl, "impliedCarryPnl");
    }

    /** {@link #priceMovePnl()} plus {@link #executionCostPnl()}, deliberately excluding the carry memo figure. */
    public double totalReconciledPnl() {
        return priceMovePnl + executionCostPnl;
    }

    /** Whether {@link #totalReconciledPnl()} matches an independently computed equity change within {@code tolerance}. */
    public boolean reconciles(double expectedEquityChange, double tolerance) {
        return Math.abs(totalReconciledPnl() - expectedEquityChange) <= tolerance;
    }

    /**
     * @param annualCarryRate a stated annual rate (e.g. 0.05 for 5%), applied to the
     *     cash balance actually held each bar via {@link Annualization#periodsPerYear};
     *     may be negative to represent a financing cost rather than income
     */
    public static PnlAttribution of(BacktestResult result, double annualCarryRate) {
        Objects.requireNonNull(result, "result");
        requireFinite(annualCarryRate, "annualCarryRate");

        BarSeries series = result.series();
        DoubleSeries equityCurve = result.equityCurve();
        List<Trade> trades = result.trades();
        int barCount = series.size();
        if (barCount < 2) {
            throw new IllegalArgumentException("attribution needs at least 2 bars, got: " + barCount);
        }

        double perBarCarryRate = annualCarryRate / Annualization.periodsPerYear(series.timeframe());

        double cash = equityCurve.get(0);
        double shares = 0.0;
        double priceMovePnl = 0.0;
        double executionCostPnl = 0.0;
        double impliedCarryPnl = 0.0;
        int tradeIndex = 0;

        for (int i = 1; i < barCount; i++) {
            Bar previousBar = series.get(i - 1);
            Bar bar = series.get(i);

            impliedCarryPnl += cash * perBarCarryRate;

            double sharesBeforeFill = shares;
            priceMovePnl += sharesBeforeFill * (bar.close() - previousBar.close());

            if (tradeIndex < trades.size() && trades.get(tradeIndex).barIndex() == i) {
                Trade trade = trades.get(tradeIndex);
                tradeIndex++;

                priceMovePnl += trade.shares() * (bar.close() - trade.referencePrice());
                executionCostPnl -= trade.slippageCost();
                executionCostPnl -= trade.commission();

                cash -= trade.shares() * trade.price() + trade.commission();
                shares += trade.shares();
            }
        }

        return new PnlAttribution(priceMovePnl, executionCostPnl, impliedCarryPnl);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got: " + value);
        }
    }
}
