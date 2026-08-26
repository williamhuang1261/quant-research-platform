package io.github.williamhuang1261.qrp.report;

/**
 * One line of a fund comparison table.
 *
 * <p>Every rate is a fraction, matching
 * {@link io.github.williamhuang1261.qrp.engine.PerformanceMetrics}.
 * {@code benchmarkRelativeBps} is the net-CAGR gap to the benchmark, in basis
 * points: positive means this row beat the benchmark net of its own fee.
 *
 * @param grossCagr the strategy's own CAGR, before any management fee
 * @param netCagr the CAGR after this fund's {@link ManagementFeeModel} is applied
 * @param valueAtRisk95 historical 95% VaR of the gross return series, as a positive loss fraction
 * @param expectedShortfall95 historical 95% expected shortfall, as a positive loss fraction
 */
public record FundComparisonRow(
        String displayName,
        boolean isBenchmark,
        double grossCagr,
        double netCagr,
        double sharpeRatio,
        double maxDrawdown,
        double valueAtRisk95,
        double expectedShortfall95,
        double benchmarkRelativeBps) {
}
