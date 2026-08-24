package io.github.williamhuang1261.qrp.core;

import java.util.Objects;

/**
 * A tradable instrument, identified by its symbol and quote currency.
 *
 * <p>Deliberately thin: exchange calendars, tick sizes and contract multipliers
 * belong to a venue model, and this platform does not simulate a venue.
 */
public record Instrument(String symbol, String currency, AssetClass assetClass) {

    public Instrument {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(assetClass, "assetClass");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        symbol = symbol.trim().toUpperCase();
        currency = currency.trim().toUpperCase();
        if (currency.length() != 3) {
            throw new IllegalArgumentException(
                    "currency must be a 3-letter ISO 4217 code, got: " + currency);
        }
    }

    /** An equity quoted in US dollars, the common case in the sample data. */
    public static Instrument equity(String symbol) {
        return new Instrument(symbol, "USD", AssetClass.EQUITY);
    }

    @Override
    public String toString() {
        return symbol + "." + currency;
    }
}
