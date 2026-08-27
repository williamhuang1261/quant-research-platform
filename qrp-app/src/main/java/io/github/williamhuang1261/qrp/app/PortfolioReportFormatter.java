package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.portfolio.PortfolioBacktestEngine.RebalanceFrequency;
import io.github.williamhuang1261.qrp.portfolio.PortfolioBacktestResult;
import java.util.List;
import java.util.Locale;

/**
 * Renders a {@link PortfolioRunner.Outcome} as a one-page plain-text report,
 * matching {@link ReportFormatter} and {@link FundComparisonReportFormatter}'s
 * conventions -- fixed width, no colour, the caveats printed rather than left
 * in a README -- so a reader moving between this platform's reports finds the
 * same rules every time.
 *
 * <p>Unlike a single-instrument run, a portfolio run has no one "trades" or
 * "Sharpe ratio" line to headline: the thing this report exists to show is how
 * capital and risk were split across instruments, and how much churn the
 * schedule cost getting there, so weights, risk contribution and turnover are
 * the report's body rather than an appendix to an equity curve.
 */
public final class PortfolioReportFormatter {

    private PortfolioReportFormatter() {
    }

    public static String format(PortfolioRunner.Outcome outcome) {
        PortfolioBacktestResult result = outcome.result();
        List<String> instruments = result.instruments();
        double[] averageWeights = result.averageWeights();
        double[] averageRiskContribution = result.averageRiskContribution();

        StringBuilder out = new StringBuilder();

        out.append(rule())
                .append(String.format(Locale.ROOT, "  portfolio: %s%n", String.join(", ", instruments)))
                .append(String.format(Locale.ROOT, "  optimizer: %s, rebalance: %s%n",
                        outcome.optimizerId(), rebalanceLabel(outcome.rebalance())))
                .append(rule());

        outcome.signalReport().ifPresent(report -> {
            var significance = report.significance();
            out.append(String.format(Locale.ROOT, "  signal: %s (%d periods)%n",
                            report.indicatorId(), report.periods()))
                    .append(String.format(Locale.ROOT,
                            "  mean IC: %+.4f   std. error: %.4f   z: %+.3f   p: %.4f   significant at 5%%: %s%n",
                            significance.meanIc(), significance.standardError(), significance.zStatistic(),
                            significance.pValue(), significance.isSignificant(0.05) ? "yes" : "no"))
                    .append(rule());
        });

        out.append(row("initial equity", money(outcome.initialCash())))
                .append(row("final equity", money(result.finalEquity())))
                .append(row("total return", percent(result.finalEquity() / outcome.initialCash() - 1.0)))
                .append(row("rebalances", String.valueOf(result.rebalances().size())))
                .append(row("total turnover", ratio(result.totalTurnover())))
                .append(rule());

        out.append(String.format(Locale.ROOT, "  %-16s %14s %22s%n",
                "instrument", "avg. weight", "avg. risk contribution"));
        out.append(rule());
        for (int i = 0; i < instruments.size(); i++) {
            out.append(String.format(Locale.ROOT, "  %-16s %14s %22s%n",
                    instruments.get(i), unsignedPercent(averageWeights[i]), ratio6(averageRiskContribution[i])));
        }

        out.append(rule())
                .append("  Weight and risk contribution are averaged across every scheduled\n")
                .append("  rebalance. Risk contribution is w_i * (Sigma w)_i, the same quantity\n")
                .append("  the risk-parity optimizer targets directly and mean-variance reports\n")
                .append("  as a byproduct of its own objective; it sums to the\n")
                .append("  portfolio's variance at each rebalance, not to 1. Turnover is the\n")
                .append("  sum of |weight change| across every instrument and rebalance -- the\n")
                .append("  same quantity a turnover cap bounds -- not measured from the literal\n")
                .append("  fills the per-instrument composition happens to make. Every sleeve\n")
                .append("  is priced through the existing single-instrument engine and its cost\n")
                .append("  model; there is no covariance shrinkage and no transaction-cost\n")
                .append("  optimization beyond the turnover cap. ")
                .append(outcome.signalReport().isPresent()
                        ? "The view above comes from a single\n  indicator's cross-sectional rank, not a multi-factor model, and its own\n"
                                + "  IC/significance line is the only evidence it is worth trusting.\n"
                        : "There is no factor model:\n  the view above is a flat trailing-momentum placeholder, not an\n"
                                + "  estimated forecast.\n")
                .append(rule());

        return out.toString();
    }

    private static String rebalanceLabel(RebalanceFrequency frequency) {
        return frequency.name().toLowerCase(Locale.ROOT);
    }

    private static String rule() {
        return "  " + "-".repeat(60) + "\n";
    }

    private static String row(String label, String value) {
        return String.format(Locale.ROOT, "  %-28s %31s%n", label, value);
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%,.2f", value);
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%+.2f%%", value * 100.0);
    }

    private static String unsignedPercent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static String ratio(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.4f", value);
    }

    private static String ratio6(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.6f", value);
    }
}
