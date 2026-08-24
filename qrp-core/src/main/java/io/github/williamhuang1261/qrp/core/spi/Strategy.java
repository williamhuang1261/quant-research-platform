package io.github.williamhuang1261.qrp.core.spi;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.Signal;

/**
 * A rule that turns visible history into a target exposure.
 *
 * <p>The engine passes {@code visible}, a view ending at the bar being decided,
 * so a strategy <em>cannot</em> read a price it would not have had. Look-ahead
 * bias is the failure that makes a backtest worthless, and it is prevented here
 * structurally rather than by convention.
 *
 * <p>Implementations may keep state between {@link #onStart} and the last
 * {@link #onBar} of a run, but must not share it across runs; the engine creates
 * or resets one instance per backtest.
 */
public interface Strategy {

    String id();

    default String displayName() {
        return id();
    }

    /** Called once before the first bar. Validate parameters here, loudly. */
    default void onStart(BarSeries fullSeries, Params params) {
    }

    /** Bars needed before the first meaningful decision; earlier bars are skipped. */
    default int warmup(Params params) {
        return 0;
    }

    /**
     * @param visible history up to and including the current bar, never empty
     * @return the exposure the strategy wants after this bar
     */
    Signal onBar(BarSeries visible, Params params);
}
