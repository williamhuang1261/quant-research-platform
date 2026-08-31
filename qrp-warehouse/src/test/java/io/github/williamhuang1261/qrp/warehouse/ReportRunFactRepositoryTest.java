package io.github.williamhuang1261.qrp.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class ReportRunFactRepositoryTest {

    private final DataSource dataSource = WarehouseDataSourceFactory.create();
    private final InstrumentDimensionRepository instruments = new InstrumentDimensionRepository(dataSource);
    private final StrategyDimensionRepository strategies = new StrategyDimensionRepository(dataSource);
    private final ReportRunFactRepository reports = new ReportRunFactRepository(dataSource);

    @Test
    void insertedReportIsFoundByItsExactCacheKeyAndById() {
        long benchmarkId = instruments.findOrCreate(TestSymbols.unique("TSBM-"), "USD", "ETF");
        long strategyId = strategies.findOrCreate("test-strategy-" + UUID.randomUUID());
        String candidates = "TEST-A,TEST-B";
        String paramsJson = "{\"__benchmarkFeeRate\":0.0009,\"__candidateFeeRate\":0.02}";

        ReportRunRecord inserted = reports.insert(
                benchmarkId, strategyId, candidates, 100000.0, "retail", "template", paramsJson, "1d",
                "{\"rows\":[]}", "TEST-A leads on net CAGR.");

        var byKey = reports.findByKey(
                benchmarkId, strategyId, candidates, 100000.0, "retail", "template", paramsJson, "1d");
        assertTrue(byKey.isPresent());
        assertEquals(inserted.id(), byKey.get().id());

        var byId = reports.findById(inserted.id());
        assertTrue(byId.isPresent());
        assertEquals("TEST-A leads on net CAGR.", byId.get().narrative());
    }

    @Test
    void aDifferentNarrativeSourceIsANonMatch() {
        long benchmarkId = instruments.findOrCreate(TestSymbols.unique("TSBM-"), "USD", "ETF");
        long strategyId = strategies.findOrCreate("test-strategy-" + UUID.randomUUID());
        reports.insert(benchmarkId, strategyId, "TEST-A", 100000.0, "retail", "template", "{}", "1d",
                "{}", "narrative");

        var ollamaKey = reports.findByKey(
                benchmarkId, strategyId, "TEST-A", 100000.0, "retail", "ollama", "{}", "1d");

        assertTrue(ollamaKey.isEmpty());
    }

    @Test
    void aDifferentFeeRateIsANonMatch() {
        long benchmarkId = instruments.findOrCreate(TestSymbols.unique("TSBM-"), "USD", "ETF");
        long strategyId = strategies.findOrCreate("test-strategy-" + UUID.randomUUID());
        reports.insert(benchmarkId, strategyId, "TEST-A", 100000.0, "retail", "template",
                "{\"__candidateFeeRate\":0.02}", "1d", "{}", "narrative");

        var differentFee = reports.findByKey(
                benchmarkId, strategyId, "TEST-A", 100000.0, "retail", "template",
                "{\"__candidateFeeRate\":0.05}", "1d");

        assertTrue(differentFee.isEmpty(), "a different fee rate must not reuse another fee's cached report");
    }
}
