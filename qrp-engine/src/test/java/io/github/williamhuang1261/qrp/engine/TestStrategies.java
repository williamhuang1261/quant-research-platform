package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.Signal;
import io.github.williamhuang1261.qrp.core.spi.Strategy;
import java.util.function.IntFunction;

/** Strategies whose behaviour a test states outright, so the engine is what is under test. */
final class TestStrategies {

    private TestStrategies() {
    }

    static Strategy alwaysFlat() {
        return byBarIndex("always-flat", index -> Signal.flat());
    }

    static Strategy alwaysLong() {
        return byBarIndex("always-long", index -> Signal.fullyLong());
    }

    /** Decides purely from the bar index, which makes fill timing easy to assert. */
    static Strategy byBarIndex(String id, IntFunction<Signal> decision) {
        return new Strategy() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Signal onBar(BarSeries visible, Params params) {
                return decision.apply(visible.size() - 1);
            }
        };
    }
}
