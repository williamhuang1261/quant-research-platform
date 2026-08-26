package io.github.williamhuang1261.qrp.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundComparisonTableTest {

    private static final Instant DAY0 = Instant.parse("2024-01-02T21:00:00Z");
    private static final double DAYS_PER_YEAR = 365.25;

    /** A one-bar-per-day series spanning {@code days} calendar days, prices otherwise irrelevant. */
    private static BarSeries series(String symbol, int days) {
        List<Bar> bars = new ArrayList<>(days + 1);
        for (int i = 0; i <= days; i++) {
            double price = 100.0 + i;
            bars.add(new Bar(DAY0.plus(Duration.ofDays(i)), price, price + 0.5, price - 0.5, price, 1_000L));
        }
        return BarSeries.of(Instrument.equity(symbol), Timeframe.DAY_1, bars);
    }

    /** Same CAGR formula the platform uses, so the fixture's "gross" value is internally consistent. */
    private static double handCagr(double[] equity, BarSeries series) {
        double years = Duration.between(series.start(), series.end()).toDays() / DAYS_PER_YEAR;
        return Math.pow(equity[equity.length - 1] / equity[0], 1.0 / years) - 1.0;
    }

    /** A linearly growing equity curve, one point per bar, ending at {@code totalReturn} above the start. */
    private static double[] linearEquity(int bars, double start, double totalReturn) {
        double[] equity = new double[bars + 1];
        for (int i = 0; i <= bars; i++) {
            equity[i] = start * (1.0 + totalReturn * i / bars);
        }
        return equity;
    }

    private static BacktestResult resultFor(BarSeries series, double[] equity) {
        double grossCagr = handCagr(equity, series);
        PerformanceMetrics metrics = new PerformanceMetrics(
                equity[0], equity[equity.length - 1], equity[equity.length - 1] / equity[0] - 1.0,
                grossCagr, 0.10, 0.5, 0.05, 3, 1.0);
        return new BacktestResult(series, DoubleSeries.of(equity), DoubleSeries.of(equity), List.of(), metrics);
    }

    @Test
    void zeroFeeCandidateReproducesTheEngineOwnGrossCagrExactly() {
        BarSeries series = series("SYNA", 365);
        double[] equity = linearEquity(365, 100_000.0, 0.12);
        BacktestResult candidateResult = resultFor(series, equity);
        FundProfile candidate = new FundProfile("Fund A", series.instrument(), ManagementFeeModel.none());

        BarSeries benchSeries = series("SYNETF", 365);
        double[] benchEquity = linearEquity(365, 100_000.0, 0.08);
        BacktestResult benchResult = resultFor(benchSeries, benchEquity);
        FundProfile benchmark = new FundProfile(
                "Benchmark", benchSeries.instrument(), ManagementFeeModel.none());

        FundComparisonTable table = FundComparisonTable.of(
                List.of(candidate), List.of(candidateResult), benchmark, benchResult);

        FundComparisonRow candidateRow = table.rows().stream()
                .filter(r -> !r.isBenchmark())
                .findFirst()
                .orElseThrow();

        assertEquals(candidateResult.metrics().cagr(), candidateRow.netCagr(), 0.0);
        assertEquals(candidateResult.metrics().cagr(), candidateRow.grossCagr(), 0.0);
    }

    @Test
    void ranksCandidatesByNetCagrAndAlwaysAppendsTheBenchmarkLast() {
        BarSeries series = series("FUND", 365);

        double[] strongEquity = linearEquity(365, 100_000.0, 0.20);
        BacktestResult strongResult = resultFor(series, strongEquity);
        FundProfile strong = new FundProfile("Strong Fund", series.instrument(), ManagementFeeModel.none());

        double[] weakEquity = linearEquity(365, 100_000.0, 0.03);
        BacktestResult weakResult = resultFor(series, weakEquity);
        FundProfile weak = new FundProfile("Weak Fund", series.instrument(), ManagementFeeModel.none());

        double[] benchEquity = linearEquity(365, 100_000.0, 0.10);
        BacktestResult benchResult = resultFor(series, benchEquity);
        FundProfile benchmark = new FundProfile("Benchmark", series.instrument(), ManagementFeeModel.none());

        // Passed in weak-then-strong order; the table must not just echo input order.
        FundComparisonTable table = FundComparisonTable.of(
                List.of(weak, strong), List.of(weakResult, strongResult), benchmark, benchResult);

        List<FundComparisonRow> rows = table.rows();
        assertEquals(3, rows.size());
        assertEquals("Strong Fund", rows.get(0).displayName());
        assertEquals("Weak Fund", rows.get(1).displayName());
        assertTrue(rows.get(2).isBenchmark());
        assertEquals("Benchmark", rows.get(2).displayName());

        // The benchmark's own row is measured against itself.
        assertEquals(0.0, rows.get(2).benchmarkRelativeBps(), 1e-9);
        // The strong fund beat the benchmark, so its relative bps must be positive.
        assertTrue(rows.get(0).benchmarkRelativeBps() > 0.0);
        // The weak fund lagged the benchmark, so its relative bps must be negative.
        assertTrue(rows.get(1).benchmarkRelativeBps() < 0.0);
    }

    @Test
    void aHigherAnnualFeeStrictlyLowersNetCagrThanTheSameFundAtNoFee() {
        BarSeries series = series("FUND", 365);
        double[] equity = linearEquity(365, 100_000.0, 0.10);
        BacktestResult result = resultFor(series, equity);

        FundProfile noFee = new FundProfile("No Fee", series.instrument(), ManagementFeeModel.none());
        FundProfile withFee = new FundProfile("2% MER", series.instrument(), new ManagementFeeModel(0.02));
        FundProfile benchmark = new FundProfile("Benchmark", series.instrument(), ManagementFeeModel.none());

        FundComparisonTable table = FundComparisonTable.of(
                List.of(noFee, withFee), List.of(result, result), benchmark, result);

        double noFeeNetCagr = table.rows().stream()
                .filter(r -> r.displayName().equals("No Fee")).findFirst().orElseThrow().netCagr();
        double withFeeNetCagr = table.rows().stream()
                .filter(r -> r.displayName().equals("2% MER")).findFirst().orElseThrow().netCagr();

        assertTrue(withFeeNetCagr < noFeeNetCagr);
    }

    @Test
    void rejectsMismatchedProfileAndResultCounts() {
        BarSeries series = series("FUND", 10);
        double[] equity = linearEquity(10, 100.0, 0.01);
        BacktestResult result = resultFor(series, equity);
        FundProfile profile = new FundProfile("Fund", series.instrument(), ManagementFeeModel.none());

        assertThrows(IllegalArgumentException.class, () -> FundComparisonTable.of(
                List.of(profile, profile), List.of(result), profile, result));
    }
}
