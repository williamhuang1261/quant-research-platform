package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.spi.Strategy;
import java.util.Objects;

/**
 * Everything one backtest run needs. A record rather than a builder: every field
 * is required, and a run that silently defaulted its execution model would be
 * the kind of result that gets quoted without the caveat.
 */
public record BacktestRequest(
        BarSeries series, Strategy strategy, Params params, ExecutionModel execution, double initialCash) {

    public BacktestRequest {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(execution, "execution");
        if (!Double.isFinite(initialCash) || initialCash <= 0.0) {
            throw new IllegalArgumentException("initialCash must be finite and positive, got: " + initialCash);
        }
        if (series.size() < 2) {
            throw new IllegalArgumentException(
                    "a backtest needs at least 2 bars, got: " + series.size());
        }
    }
}
