package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.options.NoArbitrageDiagnostics;
import io.github.williamhuang1261.qrp.options.OptionChainQuote;
import io.github.williamhuang1261.qrp.options.VolatilitySurface;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Renders a chain analysis as plain text, matching {@link ReportFormatter}'s
 * layout so a reviewer reading both reports finds the same conventions.
 */
public final class OptionsReportFormatter {

    private OptionsReportFormatter() {
    }

    public static String format(OptionsRunner.Outcome outcome) {
        List<OptionChainQuote> chain = outcome.chain();
        LocalDate valuationDate = outcome.valuationDate();
        VolatilitySurface surface = outcome.surface();

        List<LocalDate> expiryDates = chain.stream().map(q -> q.contract().expiry()).distinct().sorted().toList();
        List<Double> strikes = chain.stream().map(q -> q.contract().strike()).distinct().sorted().toList();

        StringBuilder out = new StringBuilder();
        out.append(rule())
                .append(String.format(Locale.ROOT, "  volatility surface: %s, valued %s%n",
                        outcome.underlying(), valuationDate))
                .append(String.format(Locale.ROOT, "  %d quotes, %d expiries, %d strikes%n",
                        chain.size(), expiryDates.size(), strikes.size()))
                .append(rule());

        out.append(String.format(Locale.ROOT, "  %-10s", "strike"));
        for (LocalDate expiry : expiryDates) {
            out.append(String.format(Locale.ROOT, "%12s", expiry));
        }
        out.append('\n');

        for (double strike : strikes) {
            out.append(String.format(Locale.ROOT, "  %-10.2f", strike));
            for (LocalDate expiry : expiryDates) {
                double years = yearsTo(valuationDate, expiry);
                out.append(formatCell(surface, strike, years));
            }
            out.append('\n');
        }

        out.append(rule())
                .append(String.format(Locale.ROOT, "  no-arbitrage diagnostics: %s%n",
                        outcome.diagnostics().isClean() ? "clean"
                                : outcome.diagnostics().violations().size() + " violation(s)"));
        for (NoArbitrageDiagnostics.Violation violation : outcome.diagnostics().violations()) {
            out.append(String.format(Locale.ROOT, "    [%s] %s%n", violation.kind(), violation.description()));
        }

        return out.append(rule())
                .append("  Vol is interpolated in total variance from a chain that discounts each\n")
                .append("  quote at its own flat rate; RatesCurve exists (qrp-options) but is not\n")
                .append("  wired into this surface's IV solving yet.\n")
                .append("  Surface fit does not extrapolate past the quoted strikes or expiries.\n")
                .append(rule())
                .toString();
    }

    /**
     * A strike quoted at one expiry but not another is a legitimate real-chain
     * shape (a grid need not be rectangular); rather than let one missing corner
     * take down the whole report, that cell prints {@code n/a} and every other
     * cell still renders.
     */
    private static String formatCell(VolatilitySurface surface, double strike, double years) {
        try {
            return String.format(Locale.ROOT, "%11.2f%%", surface.impliedVolatility(strike, years) * 100.0);
        } catch (IllegalArgumentException e) {
            return String.format(Locale.ROOT, "%12s", "n/a");
        }
    }

    private static double yearsTo(LocalDate valuationDate, LocalDate expiry) {
        return java.time.temporal.ChronoUnit.DAYS.between(valuationDate, expiry) / 365.0;
    }

    private static String rule() {
        return "  " + "-".repeat(60) + "\n";
    }
}
