package io.github.williamhuang1261.qrp.report;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.engine.Annualization;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.stats.EquityCurve;
import io.github.williamhuang1261.qrp.stats.RiskMetrics;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Compares several fund-shaped backtests against one benchmark.
 *
 * <p>Rows are net-CAGR ranked so a reader sees the leader first; the benchmark
 * row is always present and marked, whatever its rank, because "how does it
 * compare" only means something next to what it is compared to.
 *
 * <p>Gross metrics (CAGR, Sharpe, max drawdown) are read directly from each
 * {@link BacktestResult}'s {@code PerformanceMetrics} rather than
 * recomputed — the engine already produced them, and a second implementation
 * of the same formula is a second place for it to drift out of sync. Only the
 * fee-adjusted net CAGR and the tail-risk figures are computed here, since
 * neither exists upstream.
 */
public final class FundComparisonTable {

    private static final double DAYS_PER_YEAR = 365.25;
    private static final double VAR_ES_CONFIDENCE = 0.95;

    private final List<FundComparisonRow> rows;

    private FundComparisonTable(List<FundComparisonRow> rows) {
        this.rows = rows;
    }

    /** Rows in the order a report should print them: ranked candidates, then the benchmark. */
    public List<FundComparisonRow> rows() {
        return rows;
    }

    public static FundComparisonTable of(
            List<FundProfile> candidateProfiles,
            List<BacktestResult> candidateResults,
            FundProfile benchmarkProfile,
            BacktestResult benchmarkResult) {
        Objects.requireNonNull(candidateProfiles, "candidateProfiles");
        Objects.requireNonNull(candidateResults, "candidateResults");
        Objects.requireNonNull(benchmarkProfile, "benchmarkProfile");
        Objects.requireNonNull(benchmarkResult, "benchmarkResult");
        if (candidateProfiles.size() != candidateResults.size()) {
            throw new IllegalArgumentException(
                    "one result is required per candidate profile, got "
                            + candidateProfiles.size() + " profiles and " + candidateResults.size() + " results");
        }
        if (candidateProfiles.isEmpty()) {
            throw new IllegalArgumentException("at least one candidate is required");
        }

        double benchmarkNetCagr = netCagr(benchmarkProfile, benchmarkResult);

        List<FundComparisonRow> ranked = new ArrayList<>(candidateProfiles.size());
        for (int i = 0; i < candidateProfiles.size(); i++) {
            ranked.add(row(candidateProfiles.get(i), candidateResults.get(i), benchmarkNetCagr, false));
        }
        ranked.sort(Comparator.comparingDouble(FundComparisonRow::netCagr).reversed());

        List<FundComparisonRow> built = new ArrayList<>(ranked);
        built.add(row(benchmarkProfile, benchmarkResult, benchmarkNetCagr, true));

        return new FundComparisonTable(List.copyOf(built));
    }

    private static FundComparisonRow row(
            FundProfile profile, BacktestResult result, double benchmarkNetCagr, boolean isBenchmark) {
        double netCagr = netCagr(profile, result);
        double[] grossReturns = EquityCurve.toReturns(result.equityCurve().toArray());
        double valueAtRisk = grossReturns.length == 0
                ? Double.NaN
                : RiskMetrics.valueAtRisk(grossReturns, VAR_ES_CONFIDENCE);
        double expectedShortfall = grossReturns.length == 0
                ? Double.NaN
                : RiskMetrics.expectedShortfall(grossReturns, VAR_ES_CONFIDENCE);

        return new FundComparisonRow(
                profile.displayName(),
                isBenchmark,
                result.metrics().cagr(),
                netCagr,
                result.metrics().sharpeRatio(),
                result.metrics().maxDrawdown(),
                valueAtRisk,
                expectedShortfall,
                (netCagr - benchmarkNetCagr) * 10_000.0);
    }

    private static double netCagr(FundProfile profile, BacktestResult result) {
        double periodsPerYear = Annualization.periodsPerYear(result.series().timeframe());
        double[] netEquity = profile.fee().applyTo(result.equityCurve().toArray(), periodsPerYear);
        return cagr(netEquity, result.series());
    }

    /**
     * Same formula as {@code PerformanceMetrics.cagr}: real calendar years
     * between the first and last bar, not a bar count divided by a nominal
     * periods-per-year. Two CAGR formulas that disagree on the definition of a
     * year would make "net of fees" and "gross" incomparable.
     */
    private static double cagr(double[] equity, BarSeries series) {
        if (equity.length < 2 || equity[0] <= 0.0 || equity[equity.length - 1] <= 0.0) {
            return Double.NaN;
        }
        double years = Duration.between(series.start(), series.end()).toDays() / DAYS_PER_YEAR;
        if (years <= 0.0) {
            return Double.NaN;
        }
        return Math.pow(equity[equity.length - 1] / equity[0], 1.0 / years) - 1.0;
    }
}
