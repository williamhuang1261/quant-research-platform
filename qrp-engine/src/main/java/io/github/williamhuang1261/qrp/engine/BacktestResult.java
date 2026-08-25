package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import java.util.List;

/**
 * The output of one run: the equity curve aligned to the bars, every fill, and
 * the summary statistics.
 *
 * @param equityCurve mark-to-market account value at each bar's close
 * @param exposure    target exposure held into each bar's close, in [-1, 1]
 */
public record BacktestResult(
        BarSeries series,
        DoubleSeries equityCurve,
        DoubleSeries exposure,
        List<Trade> trades,
        PerformanceMetrics metrics) {

    public BacktestResult {
        trades = List.copyOf(trades);
    }

    public double finalEquity() {
        return metrics.finalEquity();
    }
}
