package io.github.williamhuang1261.qrp.matching;

/**
 * One order submitted to a {@link MatchingEngine}.
 *
 * <p>Unlike the rest of this platform's domain types, this is a plain class
 * rather than a record: a resting limit order's {@link #remainingQuantity()}
 * genuinely changes as it is partially filled, and a record's whole point is
 * that it does not. {@code sequence} is assigned by {@link OrderBook} the
 * moment the order is accepted, not by the caller — time priority has to be
 * the book's own clock, not whatever order two calls happened to arrive from
 * client code in, which is not guaranteed to match submission order under
 * concurrent callers.
 *
 * <p>A {@link OrderType#MARKET} order carries no {@code limitPrice}
 * ({@link Double#NaN}); it is never eligible to rest, and {@link
 * #limitPrice()} must not be read for one.
 */
public final class Order {

    private final long id;
    private final Side side;
    private final OrderType type;
    private final double limitPrice;
    private final double originalQuantity;
    private double remainingQuantity;
    private long sequence = -1;

    private Order(long id, Side side, OrderType type, double limitPrice, double quantity) {
        this.id = id;
        this.side = side;
        this.type = type;
        this.limitPrice = limitPrice;
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;
    }

    public static Order limit(long id, Side side, double price, double quantity) {
        requireId(id);
        requirePositiveFinite(price, "price");
        requirePositiveFinite(quantity, "quantity");
        return new Order(id, requireSide(side), OrderType.LIMIT, price, quantity);
    }

    public static Order market(long id, Side side, double quantity) {
        requireId(id);
        requirePositiveFinite(quantity, "quantity");
        return new Order(id, requireSide(side), OrderType.MARKET, Double.NaN, quantity);
    }

    public long id() {
        return id;
    }

    public Side side() {
        return side;
    }

    public OrderType type() {
        return type;
    }

    /** Only meaningful for {@link OrderType#LIMIT}; {@link Double#NaN} for a market order. */
    public double limitPrice() {
        return limitPrice;
    }

    public double originalQuantity() {
        return originalQuantity;
    }

    public double remainingQuantity() {
        return remainingQuantity;
    }

    public boolean isFullyFilled() {
        return remainingQuantity <= 0.0;
    }

    /** Assigned once by {@link OrderBook} when the order is accepted; -1 beforehand. */
    public long sequence() {
        return sequence;
    }

    void assignSequence(long sequence) {
        if (this.sequence != -1) {
            throw new IllegalStateException("sequence already assigned to order " + id);
        }
        this.sequence = sequence;
    }

    /**
     * Reduces the resting quantity by a fill. Package-private: only {@link
     * MatchingEngine} decides when and how much of an order fills.
     */
    void reduceRemaining(double filledQuantity) {
        if (!Double.isFinite(filledQuantity) || filledQuantity <= 0.0) {
            throw new IllegalArgumentException("filledQuantity must be finite and positive, got: " + filledQuantity);
        }
        if (filledQuantity > remainingQuantity + 1.0e-9) {
            throw new IllegalStateException(
                    "cannot fill " + filledQuantity + ", only " + remainingQuantity + " remaining on order " + id);
        }
        remainingQuantity = Math.max(0.0, remainingQuantity - filledQuantity);
    }

    private static Side requireSide(Side side) {
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        return side;
    }

    private static void requireId(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must not be negative, got: " + id);
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive, got: " + value);
        }
    }

    @Override
    public String toString() {
        return "Order{id=%d, side=%s, type=%s, limitPrice=%s, remaining=%s/%s}"
                .formatted(id, side, type, limitPrice, remainingQuantity, originalQuantity);
    }
}
