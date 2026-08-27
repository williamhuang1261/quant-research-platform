package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.Bar;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A synthetic bid/ask book built from a single OHLCV bar.
 *
 * <p><strong>This is a heuristic over OHLCV, not a reconstructed real
 * book.</strong> No exchange feed records individual resting orders in this
 * data set — the same honest limitation as the {@code SYNA}/{@code SYNOPT}
 * series and chains elsewhere in this repository, which are labelled
 * synthetic rather than presented as recorded quotes. This class follows the
 * same precedent: the spread is a configurable fraction of the bar's
 * high-low range, centred on the bar's open (the same "earliest honest
 * execution price" anchor {@link ExecutionModel} already uses), and depth is
 * apportioned across a small number of price levels scaled to the bar's
 * traded volume. It is a plausible microstructure invented from a single
 * candle, useful for asking "would this size have filled here," never for
 * claiming what the real book actually looked like.
 *
 * @param mid the price the book is centred on (the reference bar's open)
 * @param halfSpread half the synthetic bid/ask spread; the best bid sits at
 *     {@code mid - halfSpread}, the best ask at {@code mid + halfSpread}
 * @param bids resting buy levels, best price first (highest price first)
 * @param asks resting sell levels, best price first (lowest price first)
 */
public record SyntheticOrderBook(double mid, double halfSpread, List<Level> bids, List<Level> asks) {

    public SyntheticOrderBook {
        if (!Double.isFinite(mid) || mid <= 0.0) {
            throw new IllegalArgumentException("mid must be finite and positive, got: " + mid);
        }
        if (!Double.isFinite(halfSpread) || halfSpread < 0.0) {
            throw new IllegalArgumentException("halfSpread must be finite and non-negative, got: " + halfSpread);
        }
        bids = List.copyOf(Objects.requireNonNull(bids, "bids"));
        asks = List.copyOf(Objects.requireNonNull(asks, "asks"));
    }

    /** One resting price level: a price and the synthetic size resting there. */
    public record Level(double price, double size) {

        public Level {
            if (!Double.isFinite(price) || price <= 0.0) {
                throw new IllegalArgumentException("price must be finite and positive, got: " + price);
            }
            if (!Double.isFinite(size) || size < 0.0) {
                throw new IllegalArgumentException("size must be finite and non-negative, got: " + size);
            }
        }
    }

    /**
     * The result of walking a book side toward a limit price: how much size
     * was actually available at or better than the limit, and the
     * volume-weighted average price paid for it.
     */
    public record WalkResult(double filledSize, double averagePrice) {
    }

    /**
     * Builds a synthetic book from one bar.
     *
     * @param bar the bar to derive the book from
     * @param spreadFraction the synthetic spread as a fraction of the bar's
     *     high-low range (e.g. {@code 0.5} means the full bid/ask spread is
     *     half the bar's traded range)
     * @param levels how many price levels to synthesize per side, best price
     *     first; must be at least 1
     * @param depthFraction the fraction of the bar's volume treated as
     *     visible resting depth, split evenly between the two sides and then
     *     apportioned across levels with more size resting closer to the top
     *     of book
     */
    public static SyntheticOrderBook fromBar(Bar bar, double spreadFraction, int levels, double depthFraction) {
        Objects.requireNonNull(bar, "bar");
        if (!Double.isFinite(spreadFraction) || spreadFraction <= 0.0) {
            throw new IllegalArgumentException(
                    "spreadFraction must be finite and positive, got: " + spreadFraction);
        }
        if (levels < 1) {
            throw new IllegalArgumentException("levels must be at least 1, got: " + levels);
        }
        if (!Double.isFinite(depthFraction) || depthFraction <= 0.0) {
            throw new IllegalArgumentException("depthFraction must be finite and positive, got: " + depthFraction);
        }

        double mid = bar.open();
        double range = bar.range();
        // A bar with a zero high-low range (a single print, or a synthetic
        // bar with high == low) still needs a nonzero spread to be a book at
        // all; fall back to a tiny fraction of price rather than collapsing
        // every bid and ask onto the same level.
        double effectiveRange = range > 0.0 ? range : mid * 1.0e-4;
        double halfSpread = spreadFraction * effectiveRange / 2.0;

        double totalDepth = bar.volume() * depthFraction;
        double perSideDepth = totalDepth / 2.0;

        // Geometric decay: each level further from the top of book rests
        // half the size of the one before it. A thin, low-volume bar then
        // produces a book that runs out of depth after a handful of levels,
        // which is the whole point of the model.
        double[] weights = new double[levels];
        double weightSum = 0.0;
        for (int i = 0; i < levels; i++) {
            weights[i] = Math.pow(0.5, i);
            weightSum += weights[i];
        }

        List<Level> bidLevels = new ArrayList<>(levels);
        List<Level> askLevels = new ArrayList<>(levels);
        for (int i = 0; i < levels; i++) {
            double levelSize = weightSum > 0.0 ? perSideDepth * weights[i] / weightSum : 0.0;
            double distance = halfSpread * (i + 1);
            double bidPrice = mid - distance;
            double askPrice = mid + distance;
            if (bidPrice > 0.0) {
                bidLevels.add(new Level(bidPrice, levelSize));
            }
            askLevels.add(new Level(askPrice, levelSize));
        }

        return new SyntheticOrderBook(mid, halfSpread, bidLevels, askLevels);
    }

    /**
     * Walks {@code side} (best price first, as returned by {@link #bids()} or
     * {@link #asks()}) accumulating size toward {@code desiredSize}, but only
     * while each level's price is at or better than {@code limitPrice}.
     * "Better" means at or below the limit while buying (walking the asks),
     * and at or above the limit while selling (walking the bids). Because
     * levels are ordered best-first, the walk stops at the first level that
     * fails that test rather than skipping past it.
     *
     * @return empty if no size at all is available at or better than the
     *     limit; otherwise the size actually fillable (which may be less
     *     than {@code desiredSize}) and its volume-weighted average price
     */
    public static Optional<WalkResult> walk(
            List<Level> side, double desiredSize, double limitPrice, boolean buying) {
        Objects.requireNonNull(side, "side");
        if (!Double.isFinite(desiredSize) || desiredSize <= 0.0) {
            throw new IllegalArgumentException("desiredSize must be finite and positive, got: " + desiredSize);
        }

        double remaining = desiredSize;
        double filled = 0.0;
        double notional = 0.0;
        for (Level level : side) {
            boolean acceptable = buying ? level.price() <= limitPrice : level.price() >= limitPrice;
            if (!acceptable) {
                break;
            }
            double take = Math.min(remaining, level.size());
            filled += take;
            notional += take * level.price();
            remaining -= take;
            if (remaining <= 0.0) {
                break;
            }
        }

        if (filled <= 0.0) {
            return Optional.empty();
        }
        return Optional.of(new WalkResult(filled, notional / filled));
    }
}
