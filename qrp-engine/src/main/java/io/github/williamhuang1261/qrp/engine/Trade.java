package io.github.williamhuang1261.qrp.engine;

import java.time.Instant;

/**
 * One executed fill.
 *
 * <p>{@code shares} is signed: positive bought, negative sold. {@code price} is
 * what was actually paid after slippage, while {@code referencePrice} is the
 * quote it was measured against, so the concession is auditable rather than
 * folded invisibly into the fill.
 */
public record Trade(
        int barIndex,
        Instant timestamp,
        double shares,
        double price,
        double referencePrice,
        double commission) {

    public boolean isBuy() {
        return shares > 0.0;
    }

    /** Cash leaving the account: negative when selling. */
    public double notional() {
        return shares * price;
    }

    /** What the slippage cost on this fill, always non-negative. */
    public double slippageCost() {
        return Math.abs(shares) * Math.abs(price - referencePrice);
    }
}
