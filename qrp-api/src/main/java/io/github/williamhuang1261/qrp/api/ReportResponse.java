package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.app.CompareRunner;
import java.util.List;

/**
 * A fund comparison's summary, over the wire. Rows keep
 * {@link io.github.williamhuang1261.qrp.report.FundComparisonTable}'s own
 * order (ranked candidates, then the benchmark); the narrative is whichever
 * {@link io.github.williamhuang1261.qrp.report.NarrativeGenerator} the
 * request asked for, already resolved to its final text (including the
 * Ollama generator's fail-closed fallback, if that path was taken).
 */
public record ReportResponse(
        String strategyId,
        List<String> candidateSymbols,
        String benchmarkSymbol,
        List<ReportRowResponse> rows,
        String narrative) {

    static ReportResponse from(CompareRunner.Outcome outcome) {
        return new ReportResponse(
                outcome.strategyId(),
                outcome.candidateSymbols(),
                outcome.benchmarkSymbol(),
                outcome.table().rows().stream().map(ReportRowResponse::from).toList(),
                outcome.narrative());
    }
}
