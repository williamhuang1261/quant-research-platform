package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.Bar;
import java.util.Optional;

/**
 * Decides whether, and at what price, a pending target exposure actually
 * fills against one bar.
 *
 * <p>This is the seam between "the strategy wants this position" and "the
 * account now holds it": everything about <em>when</em> a decision is
 * evaluated (next-open timing, {@code visibleAt(i)}) stays in
 * {@link BacktestEngine}, which calls this interface once per pending target
 * with the bar the fill executes against. What differs between
 * implementations is purely the mechanics of the fill itself — a flat
 * concession against the open, versus walking a synthetic order book.
 *
 * <p>Returning {@link Optional#empty()} is a first-class outcome, not an
 * error: a model that cannot support the requested size at any acceptable
 * price should say so rather than filling at a price it never actually
 * offered.
 */
public interface ExecutionModel {

    /**
     * @param referenceBar the bar the fill executes against (its open is the
     *     earliest honest execution price for a decision made on the
     *     previous bar)
     * @param pendingTarget the target exposure, as a fraction of equity, the
     *     strategy asked for
     * @param cash cash on hand before this fill
     * @param shares shares held before this fill
     * @return the fill, or {@link Optional#empty()} if nothing filled at all
     */
    Optional<Fill> fill(Bar referenceBar, double pendingTarget, double cash, double shares);

    /**
     * One fill: the price actually paid, the signed change in shares (positive
     * for a buy, negative for a sell), and the commission charged on it.
     */
    record Fill(double price, double deltaShares, double commission) {

        public Fill {
            if (!Double.isFinite(price) || price <= 0.0) {
                throw new IllegalArgumentException("price must be finite and positive, got: " + price);
            }
            if (deltaShares == 0.0) {
                throw new IllegalArgumentException(
                        "a Fill must change the position; a no-op fill should be Optional.empty() instead");
            }
            if (!Double.isFinite(commission) || commission < 0.0) {
                throw new IllegalArgumentException(
                        "commission must be finite and non-negative, got: " + commission);
            }
        }
    }
}
