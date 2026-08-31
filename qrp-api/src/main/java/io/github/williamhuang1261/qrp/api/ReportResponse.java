package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.app.CompareRunner;
import io.github.williamhuang1261.qrp.warehouse.ReportRunRecord;
import java.util.List;

/**
 * A fund comparison's summary, over the wire. Rows keep
 * {@link io.github.williamhuang1261.qrp.report.FundComparisonTable}'s own
 * order (ranked candidates, then the benchmark); the narrative is whichever
 * {@link io.github.williamhuang1261.qrp.report.NarrativeGenerator} the
 * request asked for, already resolved to its final text (including the
 * Ollama generator's fail-closed fallback, if that path was taken).
 *
 * <p>{@code id} and {@code cached} mirror {@link RunResponse}: every report
 * is now a persisted {@code fact_report_run} row, so an identical repeat
 * request returns the same {@code id} with {@code cached: true} and never
 * re-invokes the narrative generator -- including never repeating an Ollama
 * call.
 */
public record ReportResponse(
        long id,
        boolean cached,
        String strategyId,
        List<String> candidateSymbols,
        String benchmarkSymbol,
        List<ReportRowResponse> rows,
        String narrative) {

    static ReportResponse from(CompareRunner.Outcome outcome, long id, boolean cached) {
        return new ReportResponse(
                id,
                cached,
                outcome.strategyId(),
                outcome.candidateSymbols(),
                outcome.benchmarkSymbol(),
                outcome.table().rows().stream().map(ReportRowResponse::from).toList(),
                outcome.narrative());
    }

    /** Rows come pre-deserialized from {@code table_json}: the caller owns the JSON round trip, not this record. */
    static ReportResponse fromCached(
            ReportRunRecord record, String strategyId, List<String> candidateSymbols,
            String benchmarkSymbol, List<ReportRowResponse> rows) {
        return new ReportResponse(
                record.id(), true, strategyId, candidateSymbols, benchmarkSymbol, rows, record.narrative());
    }
}
