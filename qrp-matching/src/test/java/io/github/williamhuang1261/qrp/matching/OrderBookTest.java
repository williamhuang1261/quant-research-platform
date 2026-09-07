package io.github.williamhuang1261.qrp.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderBookTest {

    @Test
    void samePriceOrdersPopInSubmissionOrder() {
        OrderBook book = new OrderBook();
        Order first = Order.limit(1, Side.BUY, 100.0, 5);
        Order second = Order.limit(2, Side.BUY, 100.0, 5);
        Order third = Order.limit(3, Side.BUY, 100.0, 5);

        book.addResting(first);
        book.addResting(second);
        book.addResting(third);

        assertEquals(first, book.peekBest(Side.BUY));
        book.remove(first);
        assertEquals(second, book.peekBest(Side.BUY));
        book.remove(second);
        assertEquals(third, book.peekBest(Side.BUY));
    }

    @Test
    void bidsAreBestFirstHighestPrice() {
        OrderBook book = new OrderBook();
        book.addResting(Order.limit(1, Side.BUY, 99.0, 1));
        book.addResting(Order.limit(2, Side.BUY, 101.0, 1));
        book.addResting(Order.limit(3, Side.BUY, 100.0, 1));

        assertEquals(101.0, book.bestPrice(Side.BUY).getAsDouble());
        assertEquals(2L, book.peekBest(Side.BUY).id());
    }

    @Test
    void asksAreBestFirstLowestPrice() {
        OrderBook book = new OrderBook();
        book.addResting(Order.limit(1, Side.SELL, 101.0, 1));
        book.addResting(Order.limit(2, Side.SELL, 99.0, 1));
        book.addResting(Order.limit(3, Side.SELL, 100.0, 1));

        assertEquals(99.0, book.bestPrice(Side.SELL).getAsDouble());
        assertEquals(2L, book.peekBest(Side.SELL).id());
    }

    @Test
    void cancelRemovesOrderAndReturnsIt() {
        OrderBook book = new OrderBook();
        Order order = Order.limit(1, Side.BUY, 100.0, 5);
        book.addResting(order);

        Order cancelled = book.cancel(1);
        assertEquals(order, cancelled);
        assertTrue(book.isEmpty(Side.BUY));
    }

    @Test
    void cancelUnknownIdReturnsNull() {
        OrderBook book = new OrderBook();
        assertNull(book.cancel(999));
    }

    @Test
    void emptyLevelIsRemovedAfterLastOrderLeaves() {
        OrderBook book = new OrderBook();
        Order order = Order.limit(1, Side.BUY, 100.0, 5);
        book.addResting(order);
        book.remove(order);

        assertTrue(book.isEmpty(Side.BUY));
        assertNull(book.peekBest(Side.BUY));
    }

    @Test
    void onlyLimitOrdersCanRest() {
        OrderBook book = new OrderBook();
        assertThrows(IllegalArgumentException.class, () -> book.addResting(Order.market(1, Side.BUY, 5)));
    }
}
