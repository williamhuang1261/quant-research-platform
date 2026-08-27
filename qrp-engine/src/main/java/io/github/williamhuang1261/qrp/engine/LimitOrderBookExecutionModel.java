package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.Bar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fills a resting limit order against a {@link SyntheticOrderBook} built from
 * the reference bar, rather than assuming the full requested size always
 * fills.
 *
 * <p>The order rests at a configurable offset beyond the book's mid — {@code
 * offsetLevels} half-spreads into the book, on the side it needs to cross —
 * and only the size the book's levels actually support at or better than
 * that limit price fills. Unlike {@link MarketOpenExecutionModel}, which
 * always fills the full requested size once a fill is warranted at all, this
 * model can report a <strong>partial</strong> fill, or {@link
 * Optional#empty()}, when the synthetic book's depth does not cover the full
 * desired size at the price the order is willing to pay. Filling the
 * remainder at a price the book never offered would be exactly the dishonest
 * shortcut this model exists to avoid.
 *
 * @param costs commission on the notional actually filled; slippage is not
 *     applied on top of it because the limit price and the book walk already
 *     express the cost of demanding liquidity
 * @param spreadFraction the synthetic book's spread as a fraction of the
 *     bar's high-low range; see {@link SyntheticOrderBook#fromBar}
 * @param offsetLevels how many half-spreads beyond the mid the resting limit
 *     is placed, on the side it needs to cross; {@code 1.0} rests exactly at
 *     the book's best price, values above {@code 1.0} cross further into the
 *     book (more fills, worse average price), values below {@code 1.0} are a
 *     limit that may not even reach the top of book
 * @param levels how many synthetic price levels per side; see {@link
 *     SyntheticOrderBook#fromBar}
 * @param depthFraction the fraction of the bar's volume treated as visible
 *     resting depth; see {@link SyntheticOrderBook#fromBar}
 */
public record LimitOrderBookExecutionModel(
        CostModel costs, double spreadFraction, double offsetLevels, int levels, double depthFraction)
        implements ExecutionModel {

    public LimitOrderBookExecutionModel {
        Objects.requireNonNull(costs, "costs");
        if (!Double.isFinite(spreadFraction) || spreadFraction <= 0.0) {
            throw new IllegalArgumentException(
                    "spreadFraction must be finite and positive, got: " + spreadFraction);
        }
        if (!Double.isFinite(offsetLevels) || offsetLevels < 0.0) {
            throw new IllegalArgumentException("offsetLevels must be finite and non-negative, got: " + offsetLevels);
        }
        if (levels < 1) {
            throw new IllegalArgumentException("levels must be at least 1, got: " + levels);
        }
        if (!Double.isFinite(depthFraction) || depthFraction <= 0.0) {
            throw new IllegalArgumentException("depthFraction must be finite and positive, got: " + depthFraction);
        }
    }

    /**
     * Reasonable defaults: a spread half the bar's high-low range, a limit
     * that rests exactly at the synthetic top of book, 5 price levels per
     * side, and 10% of the bar's volume counted as visible resting depth.
     */
    public static LimitOrderBookExecutionModel defaults(CostModel costs) {
        return new LimitOrderBookExecutionModel(costs, 0.5, 1.0, 5, 0.1);
    }

    @Override
    public Optional<Fill> fill(Bar referenceBar, double pendingTarget, double cash, double shares) {
        SyntheticOrderBook book = SyntheticOrderBook.fromBar(referenceBar, spreadFraction, levels, depthFraction);

        // Direction first, against the book's mid, mirroring
        // MarketOpenExecutionModel: sizing against the price actually paid
        // (rather than the mid) is what keeps cash from going negative.
        double provisional = targetShares(pendingTarget, cash + shares * book.mid(), book.mid());
        boolean buying = provisional > shares;
        double offset = offsetLevels * book.halfSpread();
        double limitPrice = buying ? book.mid() + offset : book.mid() - offset;

        double desiredShares = targetShares(pendingTarget, cash + shares * limitPrice, limitPrice);
        double desiredDelta = desiredShares - shares;

        // Degenerate only if the limit price reverses the direction, which a
        // limit at or beyond the mid never does; trading through it would
        // fill the wrong way.
        if (desiredDelta == 0.0 || (desiredDelta > 0.0) != buying) {
            return Optional.empty();
        }

        List<SyntheticOrderBook.Level> side = buying ? book.asks() : book.bids();
        Optional<SyntheticOrderBook.WalkResult> walked =
                SyntheticOrderBook.walk(side, Math.abs(desiredDelta), limitPrice, buying);
        if (walked.isEmpty()) {
            // The book offered nothing at or better than the limit at all:
            // an honest no-fill, not a fill at a price never on offer.
            return Optional.empty();
        }

        SyntheticOrderBook.WalkResult result = walked.get();
        double filledDelta = buying ? result.filledSize() : -result.filledSize();
        double commission = costs.commission(filledDelta * result.averagePrice());
        return Optional.of(new Fill(result.averagePrice(), filledDelta, commission));
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
