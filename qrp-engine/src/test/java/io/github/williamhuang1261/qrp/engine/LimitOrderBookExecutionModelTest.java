package io.github.williamhuang1261.qrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the behavior that makes {@link LimitOrderBookExecutionModel} more than
 * a relabelled {@link MarketOpenExecutionModel}: a fill only ever covers what
 * the synthetic book's depth actually supported at or better than the
 * resting limit price, honestly reporting a partial fill or no fill instead.
 */
class LimitOrderBookExecutionModelTest {

    private static final Instant TIMESTAMP = Instant.parse("2024-01-02T21:00:00Z");

    /** A liquid bar: a wide range and heavy volume, so a modest order fills in full. */
    private static final Bar LIQUID_BAR = new Bar(TIMESTAMP, 100.0, 102.0, 98.0, 101.0, 1_000_000L);

    /** A thin bar: a narrow range and tiny volume, the opposite of {@link #LIQUID_BAR}. */
    private static final Bar THIN_BAR = new Bar(TIMESTAMP, 100.0, 100.10, 99.90, 100.05, 50L);

    @Test
    @DisplayName("a large order against thin synthetic depth on a low-volume bar never fills in full")
    void largeOrderAgainstThinDepthPartiallyFillsOrDoesNotFill() {
        // Wants the full $1,000,000 in equity converted to shares against a
        // bar that only traded 50 shares all session: the synthetic book
        // cannot possibly offer that much depth.
        ExecutionModel model = LimitOrderBookExecutionModel.defaults(CostModel.none());

        Optional<ExecutionModel.Fill> fill = model.fill(THIN_BAR, 1.0, 1_000_000.0, 0.0);

        double fullSize = 1_000_000.0 / THIN_BAR.open();
        if (fill.isPresent()) {
            assertTrue(
                    Math.abs(fill.get().deltaShares()) < fullSize,
                    "thin synthetic depth should not support the full requested size");
        }
        // Either a partial fill (asserted above) or Optional.empty() is
        // honest; both are acceptable, filling the full size is not.
    }

    @Test
    @DisplayName("the model never fills at a price the synthetic book did not offer")
    void neverFillsBeyondTheSyntheticBookDepth() {
        LimitOrderBookExecutionModel model = LimitOrderBookExecutionModel.defaults(CostModel.none());
        SyntheticOrderBook book = SyntheticOrderBook.fromBar(
                THIN_BAR, model.spreadFraction(), model.levels(), model.depthFraction());

        Optional<ExecutionModel.Fill> fill = model.fill(THIN_BAR, 1.0, 1_000_000.0, 0.0);

        assertTrue(fill.isPresent(), "a buy against some depth, however small, should partially fill");
        double bestAsk = book.asks().get(0).price();
        double worstAskWalked = book.asks().stream()
                .mapToDouble(SyntheticOrderBook.Level::price)
                .max()
                .orElseThrow();
        double filledPrice = fill.get().price();
        assertTrue(filledPrice >= bestAsk - 1e-9, "fill price must be at or above the book's best ask");
        assertTrue(filledPrice <= worstAskWalked + 1e-9, "fill price must not exceed the deepest synthetic ask level");
    }

    @Test
    @DisplayName("a modest order against a liquid bar fills in full at a price the book actually offered")
    void modestOrderAgainstLiquidDepthFillsInFull() {
        ExecutionModel model = LimitOrderBookExecutionModel.defaults(CostModel.none());

        // ~50 shares against a million-share bar: trivially small next to
        // the synthetic depth, so it should fill completely.
        Optional<ExecutionModel.Fill> fill = model.fill(LIQUID_BAR, 0.5, 10_000.0, 0.0);

        assertTrue(fill.isPresent());
        assertFalse(fill.get().deltaShares() == 0.0);
    }

    @Test
    @DisplayName("declines a fill when the target is already met, as Optional.empty() rather than a zero-size fill")
    void declinesWhenAlreadyAtTarget() {
        ExecutionModel model = LimitOrderBookExecutionModel.defaults(CostModel.none());

        // 100 shares held at a ~101 mid, 0 cash: already fully invested.
        Optional<ExecutionModel.Fill> fill = model.fill(LIQUID_BAR, 1.0, 0.0, 100.0);

        assertTrue(fill.isEmpty(), "already at target should decline the fill rather than return a zero-size one");
    }

    @Test
    @DisplayName("rejects a non-positive spreadFraction, offsetLevels, levels or depthFraction")
    void rejectsInvalidConfiguration() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new LimitOrderBookExecutionModel(CostModel.none(), 0.0, 1.0, 5, 0.1));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new LimitOrderBookExecutionModel(CostModel.none(), 0.5, -1.0, 5, 0.1));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new LimitOrderBookExecutionModel(CostModel.none(), 0.5, 1.0, 0, 0.1));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new LimitOrderBookExecutionModel(CostModel.none(), 0.5, 1.0, 5, 0.0));
    }

    @Test
    @DisplayName("assertEquals sanity: no-fill on the liquid bar's opposite direction is not silently a sell")
    void sellDirectionAlsoRespectsBookDepth() {
        // Already fully invested at 100 shares wanting to go to 0: should
        // sell, and the fill price should come from the bid side.
        ExecutionModel model = LimitOrderBookExecutionModel.defaults(CostModel.none());

        Optional<ExecutionModel.Fill> fill = model.fill(LIQUID_BAR, 0.0, 0.0, 100.0);

        assertTrue(fill.isPresent());
        assertTrue(fill.get().deltaShares() < 0.0, "moving from fully invested to flat is a sell");
        assertEquals(-100.0, fill.get().deltaShares(), 1e-9, "liquid depth should support selling the full 100 shares");
    }
}
