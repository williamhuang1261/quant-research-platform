package io.github.williamhuang1261.qrp.core;

/** Raised when a {@code MarketDataProvider} cannot produce the requested series. */
public class MarketDataException extends RuntimeException {

    public MarketDataException(String message) {
        super(message);
    }

    public MarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
