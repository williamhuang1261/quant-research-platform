package io.github.williamhuang1261.qrp.report;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic, rule-based narrative -- no network, no model, always
 * available offline.
 *
 * <p>Net return and Sharpe ratio are independent rankings; nothing here
 * assumes the fund that led on one also leads on the other, so both leaders
 * are found by an explicit comparison over the candidate rows rather than by
 * reading off the first (net-CAGR-ranked) row twice.
 */
public final class TemplateNarrativeGenerator implements NarrativeGenerator {

    public static final String LABEL_PREFIX = "[template summary] ";

    @Override
    public String narrate(FundComparisonTable table) {
        List<FundComparisonRow> candidates = table.rows().stream()
                .filter(row -> !row.isBenchmark())
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("cannot narrate a table with no candidate rows");
        }
        Optional<FundComparisonRow> benchmark = table.rows().stream()
                .filter(FundComparisonRow::isBenchmark)
                .findFirst();

        FundComparisonRow netLeader = leaderBy(candidates, FundComparisonRow::netCagr);
        FundComparisonRow sharpeLeader = leaderBy(candidates, FundComparisonRow::sharpeRatio);

        StringBuilder text = new StringBuilder(LABEL_PREFIX);
        text.append(netLeader.displayName())
                .append(" posted the strongest net-of-fee return in this comparison, at ")
                .append(percent(netLeader.netCagr()))
                .append(" annualized. ");

        if (netLeader.displayName().equals(sharpeLeader.displayName())) {
            text.append(netLeader.displayName())
                    .append(" also delivered the best risk-adjusted return, with a Sharpe ratio of ")
                    .append(ratio(sharpeLeader.sharpeRatio()))
                    .append(", so the same fund led on both measures. ");
        } else {
            text.append(sharpeLeader.displayName())
                    .append(" led on risk-adjusted return instead, with a Sharpe ratio of ")
                    .append(ratio(sharpeLeader.sharpeRatio()))
                    .append(" versus ")
                    .append(ratio(netLeader.sharpeRatio()))
                    .append(" for ")
                    .append(netLeader.displayName())
                    .append(", so the top net performer was not the steadiest one. ");
        }

        benchmark.ifPresent(bench -> {
            if (netLeader.benchmarkRelativeBps() >= 0.0) {
                text.append(netLeader.displayName())
                        .append(" beat the ")
                        .append(bench.displayName())
                        .append(" benchmark by ")
                        .append(bps(netLeader.benchmarkRelativeBps()))
                        .append(" net of fees.");
            } else {
                text.append(netLeader.displayName())
                        .append(" trailed the ")
                        .append(bench.displayName())
                        .append(" benchmark by ")
                        .append(bps(-netLeader.benchmarkRelativeBps()))
                        .append(" net of fees.");
            }
        });

        return text.toString();
    }

    private static FundComparisonRow leaderBy(
            List<FundComparisonRow> rows, java.util.function.ToDoubleFunction<FundComparisonRow> metric) {
        return rows.stream()
                .max(Comparator.comparingDouble(metric::applyAsDouble))
                .orElseThrow();
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%+.2f%%", value * 100.0);
    }

    private static String ratio(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%+.2f", value);
    }

    private static String bps(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.0f bps", value);
    }
}
