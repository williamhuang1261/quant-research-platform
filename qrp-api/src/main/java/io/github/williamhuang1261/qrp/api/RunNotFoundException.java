package io.github.williamhuang1261.qrp.api;

/** No {@code fact_backtest_run} row exists for the requested id. */
final class RunNotFoundException extends RuntimeException {

    RunNotFoundException(long id) {
        super("no run with id " + id);
    }
}
