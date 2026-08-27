package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.report.FundComparisonRow;
import java.util.List;
import java.util.Locale;

/**
 * Renders a {@link io.github.williamhuang1261.qrp.report.FundComparisonTable}
 * as a one-page plain-text report, matching {@link ReportFormatter} and
 * {@link OptionsReportFormatter}'s conventions -- fixed width, no colour, the
 * caveats printed rather than left in a README -- so a reader moving between
 * this platform's three reports finds the same rules every time.
 */
public final class FundComparisonReportFormatter {

    private FundComparisonReportFormatter() {
    }

    public static String format(CompareRunner.Outcome outcome) {
        List<FundComparisonRow> rows = outcome.table().rows();
        StringBuilder out = new StringBuilder();

        out.append(rule())
                .append(String.format(Locale.ROOT, "  fund comparison: %s vs. %s%n",
                        String.join(", ", outcome.candidateSymbols()), outcome.benchmarkSymbol()))
                .append(String.format(Locale.ROOT, "  strategy: %s, benchmark: %s%n",
                        outcome.strategyId(), outcome.benchmarkSymbol()))
                .append(rule());

        out.append(String.format(Locale.ROOT, "  %-16s %10s %10s %8s %10s %8s %8s %12s%n",
                "fund", "gross", "net", "sharpe", "max dd", "VaR95", "ES95", "vs. bench"));
        out.append(rule());

        for (FundComparisonRow row : rows) {
            String label = row.isBenchmark() ? row.displayName() + " (bench)" : row.displayName();
            out.append(String.format(Locale.ROOT, "  %-16s %10s %10s %8s %10s %8s %8s %12s%n",
                    label,
                    percent(row.grossCagr()),
                    percent(row.netCagr()),
                    ratio(row.sharpeRatio()),
                    unsignedPercent(row.maxDrawdown()),
                    unsignedPercent(row.valueAtRisk95()),
                    unsignedPercent(row.expectedShortfall95()),
                    row.isBenchmark() ? "--" : bps(row.benchmarkRelativeBps())));
        }

        out.append(rule())
                .append("  Ranked by net CAGR, highest first; the benchmark row always prints\n")
                .append("  last regardless of rank. \"vs. bench\" is the net-CAGR gap in basis\n")
                .append("  points, positive meaning this fund beat the benchmark net of its own\n")
                .append("  fee. Fees are a single flat annual rate (see ManagementFeeModel); a\n")
                .append("  real fund's MER is neither flat nor uniform across share classes.\n")
                .append(rule());

        out.append(wrap(outcome.narrative())).append(rule());

        return out.toString();
    }

    private static String wrap(String narrative) {
        StringBuilder out = new StringBuilder();
        int width = 74;
        String[] words = narrative.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.append("  ").append(line).append('\n');
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.append("  ").append(line).append('\n');
        }
        return out.toString();
    }

    private static String rule() {
        return "  " + "-".repeat(76) + "\n";
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%+.2f%%", value * 100.0);
    }

    private static String unsignedPercent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static String ratio(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%+.2f", value);
    }

    private static String bps(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%+.0f bps", value);
    }
}
