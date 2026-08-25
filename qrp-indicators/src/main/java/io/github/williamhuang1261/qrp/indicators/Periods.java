package io.github.williamhuang1261.qrp.indicators;

import io.github.williamhuang1261.qrp.core.Params;

/** Shared parameter validation, so every indicator rejects a bad window the same way. */
final class Periods {

    static final String PERIOD = "period";

    private Periods() {
    }

    static int require(Params params, String indicatorId, int minimum) {
        int period = params.requireInt(PERIOD);
        if (period < minimum) {
            throw new IllegalArgumentException(
                    indicatorId + " needs a period of at least " + minimum + ", got: " + period);
        }
        return period;
    }
}
