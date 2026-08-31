package io.github.williamhuang1261.qrp.warehouse;

import java.time.Instant;

/** One persisted row of {@code fact_report_run}. */
public record ReportRunRecord(
        long id,
        long benchmarkInstrumentId,
        long strategyId,
        String candidateSymbolsCsv,
        double cash,
        String costModel,
        String narrativeSource,
        String paramsJson,
        String timeframe,
        String tableJson,
        String narrative,
        Instant createdAt) {
}
