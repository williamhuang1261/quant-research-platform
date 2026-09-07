package io.github.williamhuang1261.qrp.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void limitOrderRejectsNonPositivePrice() {
        assertThrows(IllegalArgumentException.class, () -> Order.limit(1, Side.BUY, 0.0, 10));
        assertThrows(IllegalArgumentException.class, () -> Order.limit(1, Side.BUY, -5.0, 10));
    }

    @Test
    void ordersRejectNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> Order.limit(1, Side.BUY, 100.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Order.market(1, Side.SELL, -1.0));
    }

    @Test
    void marketOrderHasNoLimitPrice() {
        Order order = Order.market(1, Side.BUY, 10);
        assertTrue(Double.isNaN(order.limitPrice()));
    }

    @Test
    void sequenceCannotBeAssignedTwice() {
        Order order = Order.limit(1, Side.BUY, 100.0, 10);
        order.assignSequence(0);
        assertThrows(IllegalStateException.class, () -> order.assignSequence(1));
    }

    @Test
    void reduceRemainingTracksPartialFills() {
        Order order = Order.limit(1, Side.BUY, 100.0, 10);
        order.reduceRemaining(4);
        assertEquals(6.0, order.remainingQuantity());
        assertFalse(order.isFullyFilled());
        order.reduceRemaining(6);
        assertEquals(0.0, order.remainingQuantity());
        assertTrue(order.isFullyFilled());
    }

    @Test
    void cannotFillMoreThanRemains() {
        Order order = Order.limit(1, Side.BUY, 100.0, 10);
        assertThrows(IllegalStateException.class, () -> order.reduceRemaining(11));
    }
}
