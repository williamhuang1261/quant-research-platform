package io.github.williamhuang1261.qrp.matching;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
 * Resting orders, indexed by price then by arrival order.
 *
 * <p>Bids are kept best-first (highest price first); asks are kept best-first
 * (lowest price first). Within one price level, orders sit in an {@link
 * ArrayDeque} in the order they were accepted, so {@link #peekBest} and
 * {@link #removeBest} always surface the order that has been resting longest
 * at the best price: price-time priority, enforced by the data structure's
 * own shape rather than by a matching loop remembering to check it.
 *
 * <p>Time priority is this book's own monotonic counter ({@link
 * #nextSequence}), assigned the moment an order is accepted, not derived from
 * a wall clock or from whatever order two calls happened to arrive from
 * caller code in.
 *
 * <p>This class only manages resting orders; it has no notion of crossing or
 * matching two orders against each other. {@link MatchingEngine} is what
 * decides whether an incoming order should walk this book at all before
 * anything unfilled from it ever reaches {@link #addResting}.
 */
final class OrderBook {

    private final NavigableMap<Double, Deque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Double, Deque<Order>> asks = new TreeMap<>();
    private final Map<Long, Order> byId = new java.util.HashMap<>();
    private long nextSequence = 0;

    /** Adds a {@link OrderType#LIMIT} order to rest at its own price. Market orders never rest. */
    void addResting(Order order) {
        Objects.requireNonNull(order, "order");
        if (order.type() != OrderType.LIMIT) {
            throw new IllegalArgumentException("only LIMIT orders can rest, got: " + order.type());
        }
        if (order.isFullyFilled()) {
            throw new IllegalArgumentException("a fully filled order must not be added to the book");
        }
        order.assignSequence(nextSequence++);
        levels(order.side()).computeIfAbsent(order.limitPrice(), price -> new ArrayDeque<>()).addLast(order);
        byId.put(order.id(), order);
    }

    /** The order resting longest at the best price on {@code side}, or {@code null} if that side is empty. */
    Order peekBest(Side side) {
        Map.Entry<Double, Deque<Order>> topLevel = levels(side).firstEntry();
        return topLevel == null ? null : topLevel.getValue().peekFirst();
    }

    /**
     * Removes {@code order} from wherever it rests. Safe to call on an order
     * that has just been fully filled, in which case it is simply dropped
     * from the front of its level's queue.
     */
    void remove(Order order) {
        Objects.requireNonNull(order, "order");
        Deque<Order> level = levels(order.side()).get(order.limitPrice());
        if (level == null) {
            return;
        }
        level.remove(order);
        if (level.isEmpty()) {
            levels(order.side()).remove(order.limitPrice());
        }
        byId.remove(order.id());
    }

    /**
     * Cancels a resting order by id.
     *
     * @return the cancelled order, or {@code null} if no order with that id
     *     is resting (already fully filled, already cancelled, or never
     *     existed)
     */
    Order cancel(long orderId) {
        Order order = byId.get(orderId);
        if (order == null) {
            return null;
        }
        remove(order);
        return order;
    }

    OptionalDouble bestPrice(Side side) {
        Map.Entry<Double, Deque<Order>> topLevel = levels(side).firstEntry();
        return topLevel == null ? OptionalDouble.empty() : OptionalDouble.of(topLevel.getKey());
    }

    boolean isEmpty(Side side) {
        return levels(side).isEmpty();
    }

    private NavigableMap<Double, Deque<Order>> levels(Side side) {
        return side == Side.BUY ? bids : asks;
    }
}
