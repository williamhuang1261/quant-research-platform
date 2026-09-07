package io.github.williamhuang1261.qrp.matching;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.engine.CostModel;
import io.github.williamhuang1261.qrp.engine.ExecutionModel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fills a pending target by routing an order through a real {@link
 * MatchingEngine} instead of walking a heuristic book snapshot.
 *
 * <p>A fresh engine is built for each call, seeded with {@link
 * SyntheticOrderFlowGenerator}'s resting orders derived from the reference
 * bar, then the strategy's own desired size is submitted as a {@link
 * OrderType#MARKET} order against it. Whatever the engine actually matches
 * becomes the fill; whatever it does not (the synthetic book ran out of
 * depth) is honestly left unfilled, the same "no fill invented at a price
 * never on offer" principle {@code LimitOrderBookExecutionModel} already
 * follows.
 *
 * <p>This model exists alongside {@code MarketOpenExecutionModel} and {@code
 * LimitOrderBookExecutionModel}, neither of which this class modifies: it is
 * a third, additive choice behind the same {@link ExecutionModel} seam,
 * useful specifically to demonstrate order-matching mechanics (price-time
 * priority, resting orders, partial fills against a live book) rather than a
 * single static walk.
 *
 * @param costs commission on the notional actually filled; as with the LOB
 *     model, no separate slippage concession is layered on top, because the
 *     matching engine's own fill price already reflects the cost of crossing
 *     the synthetic book
 * @param levelsPerSide synthetic price levels per side; see {@link
 *     SyntheticOrderFlowGenerator#fromBar}
 * @param spreadFraction the synthetic spread as a fraction of the bar's
 *     high-low range; see {@link SyntheticOrderFlowGenerator#fromBar}
 * @param depthFraction the fraction of the bar's volume treated as synthetic
 *     resting depth; see {@link SyntheticOrderFlowGenerator#fromBar}
 */
public record MatchingEngineExecutionModel(CostModel costs, int levelsPerSide, double spreadFraction, double depthFraction)
        implements ExecutionModel {

    public MatchingEngineExecutionModel {
        Objects.requireNonNull(costs, "costs");
        if (levelsPerSide < 1) {
            throw new IllegalArgumentException("levelsPerSide must be at least 1, got: " + levelsPerSide);
        }
        if (!Double.isFinite(spreadFraction) || spreadFraction <= 0.0) {
            throw new IllegalArgumentException(
                    "spreadFraction must be finite and positive, got: " + spreadFraction);
        }
        if (!Double.isFinite(depthFraction) || depthFraction <= 0.0) {
            throw new IllegalArgumentException("depthFraction must be finite and positive, got: " + depthFraction);
        }
    }

    /** Same defaults as {@code LimitOrderBookExecutionModel.defaults}: 5 levels, half-range spread, 10% depth. */
    public static MatchingEngineExecutionModel defaults(CostModel costs) {
        return new MatchingEngineExecutionModel(costs, 5, 0.5, 0.1);
    }

    @Override
    public Optional<Fill> fill(Bar referenceBar, double pendingTarget, double cash, double shares) {
        SyntheticOrderFlowGenerator.Flow flow =
                SyntheticOrderFlowGenerator.fromBar(referenceBar, levelsPerSide, spreadFraction, depthFraction, 1);

        MatchingEngine engine = new MatchingEngine();
        for (Order restingOrder : flow.orders()) {
            engine.submit(restingOrder);
        }

        double mid = referenceBar.open();
        double desiredShares = targetShares(pendingTarget, cash + shares * mid, mid);
        double desiredDelta = desiredShares - shares;
        if (desiredDelta == 0.0) {
            return Optional.empty();
        }

        boolean buying = desiredDelta > 0.0;
        Order taker = Order.market(flow.nextAvailableId(), buying ? Side.BUY : Side.SELL, Math.abs(desiredDelta));
        List<Trade> trades = engine.submit(taker);
        if (trades.isEmpty()) {
            // The synthetic book offered nothing at all on that side: an
            // honest no-fill, exactly as SyntheticOrderBook.walk reports when
            // its levels are empty.
            return Optional.empty();
        }

        double filledSize = trades.stream().mapToDouble(Trade::quantity).sum();
        double notional = trades.stream().mapToDouble(t -> t.price() * t.quantity()).sum();
        double averagePrice = notional / filledSize;
        double filledDelta = buying ? filledSize : -filledSize;
        double commission = costs.commission(filledDelta * averagePrice);
        return Optional.of(new Fill(averagePrice, filledDelta, commission));
    }

    /**
     * Whole shares whose market value is closest to the target fraction of
     * equity without exceeding it. Truncation, not rounding: overshooting the
     * target would borrow cash the account was never given. Mirrors {@code
     * LimitOrderBookExecutionModel}'s own private helper of the same name.
     */
    private static double targetShares(double targetExposure, double equity, double price) {
        if (equity <= 0.0) {
            return 0.0;
        }
        return (double) (long) (targetExposure * equity / price);
    }
}
