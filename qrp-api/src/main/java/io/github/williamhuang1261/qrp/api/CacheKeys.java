package io.github.williamhuang1261.qrp.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.williamhuang1261.qrp.app.CliArguments;
import io.github.williamhuang1261.qrp.app.CompareArguments;
import io.github.williamhuang1261.qrp.engine.CostModel;
import java.util.TreeMap;

/**
 * Builds the request-derived pieces of a {@code fact_backtest_run} or
 * {@code fact_report_run} cache key that are not already plain fields on
 * {@link CliArguments} / {@link CompareArguments}.
 */
final class CacheKeys {

    private CacheKeys() {
    }

    /**
     * Every number that determines the backtest's result, as canonical JSON:
     * the strategy's own params, plus -- only when execution is LOB, so a
     * market-open run's key never changes shape -- the four LOB tuning knobs
     * under keys no real indicator parameter would collide with. A
     * {@link TreeMap} sorts by key so two requests carrying the same values
     * in a different field order still land on the same key.
     */
    static String canonicalParamsJson(CliArguments arguments, ObjectMapper objectMapper) {
        TreeMap<String, Double> combined = new TreeMap<>(arguments.params().asMap());
        if (arguments.execution() == CliArguments.ExecutionKind.LOB) {
            combined.put("__lobSpreadFraction", arguments.lobSpreadFraction());
            combined.put("__lobOffsetLevels", arguments.lobOffsetLevels());
            combined.put("__lobLevels", (double) arguments.lobLevels());
            combined.put("__lobDepthFraction", arguments.lobDepthFraction());
        }
        try {
            return objectMapper.writeValueAsString(combined);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize backtest params to JSON", e);
        }
    }

    /**
     * Every number that determines a fund comparison's result beyond the
     * fixed cache-key columns: the strategy's own params, plus both
     * management fee rates -- {@code netCagr} and {@code benchmarkRelativeBps}
     * both depend on them, so two requests differing only in {@code --fee}
     * must not collide onto the same cached report.
     */
    static String canonicalReportParamsJson(CompareArguments arguments, ObjectMapper objectMapper) {
        TreeMap<String, Double> combined = new TreeMap<>(arguments.params().asMap());
        combined.put("__candidateFeeRate", arguments.candidateFeeRate());
        combined.put("__benchmarkFeeRate", arguments.benchmarkFeeRate());
        try {
            return objectMapper.writeValueAsString(combined);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize report params to JSON", e);
        }
    }

    /**
     * {@code CliArguments} only ever holds one of the two {@link CostModel}
     * constants {@code --costs} accepts ({@link CostModel#none()} or
     * {@link CostModel#retail()}; see its own {@code costModel(String)}
     * parser) -- so the cache key can store the name a request would send,
     * not a serialization of the model's numeric fields.
     */
    static String costModelName(CostModel costs) {
        if (costs.equals(CostModel.none())) {
            return "none";
        }
        if (costs.equals(CostModel.retail())) {
            return "retail";
        }
        throw new IllegalStateException("unrecognized cost model: " + costs);
    }
}
