package io.github.williamhuang1261.qrp.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A single-instrument, price-time-priority matching engine.
 *
 * <p>Submitting an order walks the opposite side of the book while the two
 * are crossable, generating a {@link Trade} for each match. A trade always
 * executes at the <strong>resting (maker)</strong> order's price, never the
 * incoming (taker) order's: the standard price-time-priority convention, and
 * the reason a buy limit resting at $101 that gets crossed by an aggressive
 * sell still trades at $101, not at whatever price the seller would have
 * accepted. Whatever a {@link OrderType#LIMIT} order does not fill rests in
 * the book; a {@link OrderType#MARKET} order's unfilled remainder is
 * discarded, never queued.
 *
 * <p>This is the engine {@code qrp-engine}'s {@code SyntheticOrderBook} is
 * deliberately not: that class walks a single static book built fresh from
 * one bar's OHLCV, once, with no persistent order state. This engine holds
 * real resting orders that survive across many {@link #submit} calls, can be
 * cancelled individually, and fill against each other directly rather than
 * against a heuristic depth curve.
 */
public final class MatchingEngine {

    private final OrderBook book = new OrderBook();
    private long tradeCount = 0;

    /**
     * Submits an order for matching. Returns every {@link Trade} it produced,
     * in the order they occurred; an empty list means it rested (or, for a
     * market order, matched nothing at all).
     *
     * @throws IllegalStateException if an order with the same id is already
     *     resting in the book
     */
    public List<Trade> submit(Order order) {
        if (book.contains(order.id())) {
            throw new IllegalStateException("an order with id " + order.id() + " is already resting");
        }

        List<Trade> trades = new ArrayList<>();
        Side opposite = order.side().opposite();

        while (!order.isFullyFilled()) {
            Order resting = book.peekBest(opposite);
            if (resting == null || !crosses(order, resting)) {
                break;
            }

            double tradeQuantity = Math.min(order.remainingQuantity(), resting.remainingQuantity());
            double tradePrice = resting.limitPrice();

            order.reduceRemaining(tradeQuantity);
            resting.reduceRemaining(tradeQuantity);
            trades.add(new Trade(resting.id(), order.id(), tradePrice, tradeQuantity, order.side()));
            tradeCount++;

            if (resting.isFullyFilled()) {
                book.remove(resting);
            }
        }

        if (order.type() == OrderType.LIMIT && !order.isFullyFilled()) {
            book.addResting(order);
        }

        return trades;
    }

    /**
     * Cancels a resting order.
     *
     * @return the cancelled order, or {@link Optional#empty()} if no order
     *     with that id is currently resting
     */
    public Optional<Order> cancel(long orderId) {
        return Optional.ofNullable(book.cancel(orderId));
    }

    public OptionalDouble bestBid() {
        return book.bestPrice(Side.BUY);
    }

    public OptionalDouble bestAsk() {
        return book.bestPrice(Side.SELL);
    }

    /** Total trades matched by this engine since construction. */
    public long tradeCount() {
        return tradeCount;
    }

    /**
     * Whether {@code incoming} can trade against {@code resting} right now: a
     * market order crosses anything, a limit order crosses only at a price at
     * least as good as its own limit.
     */
    private static boolean crosses(Order incoming, Order resting) {
        if (incoming.type() == OrderType.MARKET) {
            return true;
        }
        return incoming.side() == Side.BUY
                ? incoming.limitPrice() >= resting.limitPrice()
                : incoming.limitPrice() <= resting.limitPrice();
    }
}
