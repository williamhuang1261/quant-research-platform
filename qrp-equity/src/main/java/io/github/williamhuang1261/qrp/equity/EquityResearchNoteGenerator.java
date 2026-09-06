package io.github.williamhuang1261.qrp.equity;

import java.util.List;
import java.util.Locale;

/**
 * Turns a company's fundamentals plus its DCF/comps valuation output into a
 * short, plain-English Markdown research note -- the same "structured result
 * in, ruled-based prose out" shape {@code qrp-report}'s
 * {@code TemplateNarrativeGenerator} already applies to a fund comparison,
 * here applied to a single equity instead.
 *
 * <p>The catalyst/thesis paragraph and the key-risks list are the
 * candidate's own analysis, passed through verbatim -- this class only
 * assembles them next to the numbers it computes. Generating that prose
 * would defeat the point of the note: the written judgment is the artifact,
 * not the arithmetic around it.
 */
public final class EquityResearchNoteGenerator {

    private EquityResearchNoteGenerator() {
    }

    /**
     * @param fundamentals              the company's fundamentals snapshot
     * @param dcfFairValuePerShare      {@link EquityDcfValuation#valuePerShare}'s output
     * @param compsImpliedValuePerShare {@link ComparableMultiplesValuation#impliedValuePerShare}'s output
     * @param catalystThesis            a hand-written paragraph naming one concrete near-term catalyst
     * @param keyRisks                  a hand-written, non-empty list of key risks
     */
    public record NoteInputs(
            EquityFundamentals fundamentals,
            double dcfFairValuePerShare,
            double compsImpliedValuePerShare,
            String catalystThesis,
            List<String> keyRisks) {

        public NoteInputs {
            if (fundamentals == null) {
                throw new IllegalArgumentException("fundamentals must not be null");
            }
            if (!(dcfFairValuePerShare > 0.0) || !Double.isFinite(dcfFairValuePerShare)) {
                throw new IllegalArgumentException(
                        "dcfFairValuePerShare must be positive and finite, got: " + dcfFairValuePerShare);
            }
            if (!(compsImpliedValuePerShare > 0.0) || !Double.isFinite(compsImpliedValuePerShare)) {
                throw new IllegalArgumentException(
                        "compsImpliedValuePerShare must be positive and finite, got: " + compsImpliedValuePerShare);
            }
            if (catalystThesis == null || catalystThesis.isBlank()) {
                throw new IllegalArgumentException("catalystThesis must not be blank");
            }
            if (keyRisks == null || keyRisks.isEmpty()) {
                throw new IllegalArgumentException("keyRisks must not be empty");
            }
        }
    }

    /** Renders {@code inputs} as a one-page Markdown research note. */
    public static String generate(NoteInputs inputs) {
        EquityFundamentals f = inputs.fundamentals();
        StringBuilder md = new StringBuilder();

        md.append("# ").append(f.companyName()).append(" (").append(f.ticker()).append(")\n\n");
        md.append("*Equity research note -- as of ").append(f.fetchDate()).append("*\n\n");

        md.append("## Business Summary\n\n");
        md.append(f.companyName()).append(" (").append(f.ticker()).append(") is a ")
                .append(f.sector()).append(" company with trailing twelve-month revenue of ")
                .append(usd(f.totalRevenue())).append(" and a net margin of ")
                .append(percent(f.netMargin())).append(". Shares last traded at ")
                .append(usdPerShare(f.currentPrice())).append(", a trailing P/E of ")
                .append(ratio(f.trailingPe())).append(".\n\n");

        md.append("## Catalyst / Thesis\n\n");
        md.append(inputs.catalystThesis()).append("\n\n");

        md.append("## Valuation Summary\n\n");
        double upsideVsCurrent = inputs.dcfFairValuePerShare() / f.currentPrice() - 1.0;
        md.append("| Method | Value per share |\n");
        md.append("| --- | --- |\n");
        md.append("| DCF fair value | ").append(usdPerShare(inputs.dcfFairValuePerShare())).append(" |\n");
        md.append("| Comps-implied value (peer avg. P/E) | ")
                .append(usdPerShare(inputs.compsImpliedValuePerShare())).append(" |\n");
        md.append("| Current price | ").append(usdPerShare(f.currentPrice())).append(" |\n\n");
        md.append("The DCF fair value implies ").append(percent(upsideVsCurrent))
                .append(upsideVsCurrent >= 0.0 ? " upside" : " downside")
                .append(" versus the current price.\n\n");

        md.append("## Key Risks\n\n");
        for (String risk : inputs.keyRisks()) {
            md.append("- ").append(risk).append("\n");
        }

        return md.toString();
    }

    private static String usd(double value) {
        return String.format(Locale.ROOT, "$%,.0f", value);
    }

    private static String usdPerShare(double value) {
        return String.format(Locale.ROOT, "$%,.2f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%+.1f%%", value * 100.0);
    }

    private static String ratio(double value) {
        return String.format(Locale.ROOT, "%.1fx", value);
    }
}
