package io.github.williamhuang1261.qrp.matching;

import io.github.williamhuang1261.qrp.core.Bar;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a single OHLCV {@link Bar} into a set of resting limit orders that
 * seed a {@link MatchingEngine} with plausible synthetic liquidity.
 *
 * <p><strong>This is synthetic order flow, not a recorded order stream.</strong>
 * No exchange feed in this data set records individual orders — the same
 * honest limitation as {@code qrp-engine}'s {@code SyntheticOrderBook}, whose
 * geometric-decay depth construction this class deliberately mirrors: a bar's
 * open anchors the mid, the spread is a fraction of the bar's high-low range,
 * and depth is apportioned across a handful of price levels per side, more of
 * it resting closer to the top of book. The difference is what happens to it
 * next — {@code SyntheticOrderBook} is walked once and discarded, while the
 * orders built here become real, cancellable, individually-matchable {@link
 * Order} objects inside a {@link MatchingEngine}.
 */
public final class SyntheticOrderFlowGenerator {

    private SyntheticOrderFlowGenerator() {
    }

    /** The orders generated, plus the next order id not yet used by any of them. */
    public record Flow(List<Order> orders, long nextAvailableId) {
        public Flow {
            orders = List.copyOf(Objects.requireNonNull(orders, "orders"));
        }
    }

    /**
     * @param bar the bar to derive synthetic flow from
     * @param levelsPerSide how many price levels to synthesize per side
     * @param spreadFraction the bid/ask spread as a fraction of the bar's
     *     high-low range, centred on the bar's open
     * @param depthFraction the fraction of the bar's volume treated as
     *     synthetic resting depth, split evenly between both sides
     * @param startId the first order id to assign; ids are then sequential
     */
    public static Flow fromBar(Bar bar, int levelsPerSide, double spreadFraction, double depthFraction, long startId) {
        Objects.requireNonNull(bar, "bar");
        if (levelsPerSide < 1) {
            throw new IllegalArgumentException("levelsPerSide must be at least 1, got: " + levelsPerSide);
        }
        if (!Double.isFinite(spreadFraction) || spreadFraction <= 0.0) {
            throw new IllegalArgumentException(
                    "spreadFraction must be finite and positive, got: " + spreadFraction);
        }
        if (!Double.isFinite(depthFraction) || depthFraction <= 0.0) {
            throw new IllegalArgumentException("depthFraction must be finite and positive, got: " + depthFraction);
        }
        if (startId < 0) {
            throw new IllegalArgumentException("startId must not be negative, got: " + startId);
        }

        double mid = bar.open();
        double range = bar.range();
        double effectiveRange = range > 0.0 ? range : mid * 1.0e-4;
        double halfSpread = spreadFraction * effectiveRange / 2.0;

        double perSideDepth = bar.volume() * depthFraction / 2.0;
        double[] weights = new double[levelsPerSide];
        double weightSum = 0.0;
        for (int i = 0; i < levelsPerSide; i++) {
            weights[i] = Math.pow(0.5, i);
            weightSum += weights[i];
        }

        List<Order> orders = new ArrayList<>(levelsPerSide * 2);
        long nextId = startId;
        for (int i = 0; i < levelsPerSide; i++) {
            double size = weightSum > 0.0 ? perSideDepth * weights[i] / weightSum : 0.0;
            double distance = halfSpread * (i + 1);
            double bidPrice = mid - distance;
            double askPrice = mid + distance;

            if (size > 0.0 && bidPrice > 0.0) {
                orders.add(Order.limit(nextId++, Side.BUY, bidPrice, size));
            }
            if (size > 0.0) {
                orders.add(Order.limit(nextId++, Side.SELL, askPrice, size));
            }
        }

        return new Flow(orders, nextId);
    }
}
