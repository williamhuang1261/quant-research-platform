package io.github.williamhuang1261.qrp.report;

/**
 * Turns a {@link FundComparisonTable} into a short, plain-English paragraph.
 *
 * <p>The table already carries every number a reader would need; a narrative
 * generator's job is to say, in words, which fund led and by how much -- the
 * "written communication" half of a fund comparison, not a second source of
 * numbers. {@link TemplateNarrativeGenerator} is deterministic and always
 * available; {@link OllamaNarrativeGenerator} is an optional, clearly labelled
 * alternative that falls back to the template rather than failing the report.
 */
public interface NarrativeGenerator {

    /**
     * @param table a non-empty comparison table (at least one candidate row plus the benchmark)
     * @return a short narrative paragraph, never {@code null} or empty
     */
    String narrate(FundComparisonTable table);
}
