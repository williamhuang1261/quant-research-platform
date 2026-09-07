package io.github.williamhuang1261.qrp.matching;

/**
 * One match between an incoming (taker) order and a resting (maker) order.
 *
 * <p>{@code price} is always the maker's own resting price, never the
 * taker's: standard price-time-priority convention, and the reason a limit
 * order can cross the spread and still trade at a better price than it was
 * willing to pay.
 *
 * @param makerOrderId the resting order that was already in the book
 * @param takerOrderId the incoming order that crossed into it
 * @param price the maker's resting price
 * @param quantity size of this match; may be less than either order's full quantity
 * @param takerSide which side the taker was on
 */
public record Trade(long makerOrderId, long takerOrderId, double price, double quantity, Side takerSide) {

    public Trade {
        if (makerOrderId < 0) {
            throw new IllegalArgumentException("makerOrderId must not be negative, got: " + makerOrderId);
        }
        if (takerOrderId < 0) {
            throw new IllegalArgumentException("takerOrderId must not be negative, got: " + takerOrderId);
        }
        if (!Double.isFinite(price) || price <= 0.0) {
            throw new IllegalArgumentException("price must be finite and positive, got: " + price);
        }
        if (!Double.isFinite(quantity) || quantity <= 0.0) {
            throw new IllegalArgumentException("quantity must be finite and positive, got: " + quantity);
        }
        if (takerSide == null) {
            throw new IllegalArgumentException("takerSide must not be null");
        }
    }
}
