package io.github.williamhuang1261.qrp.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    @Test
    void nonCrossingLimitOrderRestsWithoutTrading() {
        MatchingEngine engine = new MatchingEngine();
        List<Trade> trades = engine.submit(Order.limit(1, Side.BUY, 100.0, 10));

        assertTrue(trades.isEmpty());
        assertEquals(100.0, engine.bestBid().getAsDouble());
        assertTrue(engine.bestAsk().isEmpty());
    }

    @Test
    void crossingOrderExecutesAtRestingPrice() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(Order.limit(1, Side.SELL, 101.0, 10));

        List<Trade> trades = engine.submit(Order.limit(2, Side.BUY, 105.0, 10));

        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals(101.0, trade.price(), "must fill at the resting maker's price, not the taker's limit");
        assertEquals(10.0, trade.quantity());
        assertEquals(1L, trade.makerOrderId());
        assertEquals(2L, trade.takerOrderId());
    }

    @Test
    void priceTimePriorityAcrossMultipleRestingOrdersAtSamePrice() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(Order.limit(1, Side.SELL, 100.0, 5));
        engine.submit(Order.limit(2, Side.SELL, 100.0, 5));
        engine.submit(Order.limit(3, Side.SELL, 100.0, 5));

        // A buy for 8 should fill the first resting order in full (5) and
        // the second one partially (3), leaving order 2 resting at 2 and
        // order 3 untouched -- exactly what first-in, first-filled means.
        List<Trade> trades = engine.submit(Order.limit(4, Side.BUY, 100.0, 8));

        assertEquals(2, trades.size());
        assertEquals(1L, trades.get(0).makerOrderId());
        assertEquals(5.0, trades.get(0).quantity());
        assertEquals(2L, trades.get(1).makerOrderId());
        assertEquals(3.0, trades.get(1).quantity());
    }

    @Test
    void partialFillLeavesCorrectRemainderResting() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(Order.limit(1, Side.SELL, 100.0, 5));

        Order buyer = Order.limit(2, Side.BUY, 100.0, 12);
        List<Trade> trades = engine.submit(buyer);

        assertEquals(1, trades.size());
        assertEquals(5.0, trades.get(0).quantity());
        assertEquals(7.0, buyer.remainingQuantity(), "the unfilled 7 shares must remain on the taker's own order");
        assertEquals(100.0, engine.bestBid().getAsDouble(), "the unfilled remainder must now rest");
    }

    @Test
    void cancelRemovesOrderWithNoSideEffectOnRestOfBook() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(Order.limit(1, Side.BUY, 99.0, 5));
        engine.submit(Order.limit(2, Side.BUY, 100.0, 5));

        var cancelled = engine.cancel(2);

        assertTrue(cancelled.isPresent());
        assertEquals(99.0, engine.bestBid().getAsDouble(), "the remaining order must be untouched");

        List<Trade> trades = engine.submit(Order.limit(3, Side.SELL, 100.0, 5));
        assertTrue(trades.isEmpty(), "the cancelled order must not be matchable");
    }

    @Test
    void cancelUnknownOrderReturnsEmpty() {
        MatchingEngine engine = new MatchingEngine();
        assertFalse(engine.cancel(42).isPresent());
    }

    @Test
    void marketOrderNeverRestsItsUnfilledRemainder() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(Order.limit(1, Side.SELL, 100.0, 3));

        List<Trade> trades = engine.submit(Order.market(2, Side.BUY, 10));

        assertEquals(1, trades.size());
        assertEquals(3.0, trades.get(0).quantity());
        assertTrue(engine.bestBid().isEmpty(), "an unfilled market order remainder must be discarded, not queued");
    }

    @Test
    void limitOrderDoesNotCrossAtAnUnacceptablePrice() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(Order.limit(1, Side.SELL, 105.0, 5));

        List<Trade> trades = engine.submit(Order.limit(2, Side.BUY, 100.0, 5));

        assertTrue(trades.isEmpty());
        assertEquals(100.0, engine.bestBid().getAsDouble());
        assertEquals(105.0, engine.bestAsk().getAsDouble());
    }

    @Test
    void resubmittingAnAlreadyRestingIdIsRejected() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(Order.limit(1, Side.BUY, 100.0, 5));
        assertTrue(
                org.junit.jupiter.api.Assertions.assertThrows(
                                IllegalStateException.class,
                                () -> engine.submit(Order.limit(1, Side.BUY, 99.0, 5)))
                        .getMessage()
                        .contains("already resting"));
    }
}
