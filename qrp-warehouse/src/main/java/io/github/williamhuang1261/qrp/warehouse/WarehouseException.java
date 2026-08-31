package io.github.williamhuang1261.qrp.warehouse;

/** Wraps a {@link java.sql.SQLException} behind an unchecked exception, matching this platform's error style. */
public final class WarehouseException extends RuntimeException {

    public WarehouseException(String message, Throwable cause) {
        super(message, cause);
    }
}
