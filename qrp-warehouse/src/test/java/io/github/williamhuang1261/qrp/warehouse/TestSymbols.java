package io.github.williamhuang1261.qrp.warehouse;

import java.util.UUID;

/** A unique-per-call suffix short enough to fit {@code dim_instrument.symbol VARCHAR(32)}. */
final class TestSymbols {

    private TestSymbols() {
    }

    static String unique(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return prefix + suffix;
    }
}
