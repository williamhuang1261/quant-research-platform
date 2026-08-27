package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.report.FundComparisonRow;

/**
 * One row of a fund comparison, over the wire. A hand-picked mirror of
 * {@link FundComparisonRow}'s fields rather than a direct serialization, same
 * reasoning as {@link RunResponse}: the wire format is a stable contract
 * chosen for this API, not whatever shape the internal record happens to have.
 */
public record ReportRowResponse(
        String displayName,
        boolean isBenchmark,
        double grossCagr,
        double netCagr,
        double sharpeRatio,
        double maxDrawdown,
        double valueAtRisk95,
        double expectedShortfall95,
        double benchmarkRelativeBps) {

    static ReportRowResponse from(FundComparisonRow row) {
        return new ReportRowResponse(
                row.displayName(),
                row.isBenchmark(),
                row.grossCagr(),
                row.netCagr(),
                row.sharpeRatio(),
                row.maxDrawdown(),
                row.valueAtRisk95(),
                row.expectedShortfall95(),
                row.benchmarkRelativeBps());
    }
}
