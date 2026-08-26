package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.Bar;
import java.util.Objects;
import java.util.Optional;

/**
 * Fills the full requested size at the reference bar's open, concession
 * included, via {@link CostModel}. This is the platform's original execution
 * logic, extracted from {@link BacktestEngine} unchanged: a flat commission
 * and slippage against notional, with no notion of how much liquidity the bar
 * actually offered at that price.
 *
 * <p>It always fills in full when a fill is warranted at all — the honest
 * limitation this model does not model is size. {@link
 * LimitOrderBookExecutionModel} is what adds that.
 */
public record MarketOpenExecutionModel(CostModel costs) implements ExecutionModel {

    public MarketOpenExecutionModel {
        Objects.requireNonNull(costs, "costs");
    }

    @Override
    public Optional<Fill> fill(Bar referenceBar, double pendingTarget, double cash, double shares) {
        double reference = referenceBar.open();

        // Direction first, then size at the price actually paid. Sizing at the
        // reference price and filling above it overshoots by exactly the
        // concession, which shows up as negative cash: the account would have
        // borrowed to pay its own slippage.
        double provisional = targetShares(pendingTarget, cash + shares * reference, reference);
        boolean buying = provisional > shares;
        double fillPrice = costs.fillPrice(reference, buying);
        double desiredShares = targetShares(pendingTarget, cash + shares * fillPrice, fillPrice);
        double delta = desiredShares - shares;

        // Degenerate only if the concession reverses the direction, which no
        // realistic slippage does; trading through it would fill the wrong way.
        if (delta != 0.0 && (delta > 0.0) == buying) {
            double commission = costs.commission(delta * fillPrice);
            return Optional.of(new Fill(fillPrice, delta, commission));
        }
        return Optional.empty();
    }

    /**
     * Whole shares whose market value is closest to the target fraction of
     * equity without exceeding it. Truncation, not rounding: overshooting the
     * target would borrow cash the account was never given.
     */
    private static double targetShares(double targetExposure, double equity, double price) {
        if (equity <= 0.0) {
            return 0.0;
        }
        return (double) (long) (targetExposure * equity / price);
    }
}
