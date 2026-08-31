package io.github.williamhuang1261.qrp.warehouse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class BacktestRunFactRepositoryTest {

    private final DataSource dataSource = WarehouseDataSourceFactory.create();
    private final InstrumentDimensionRepository instruments = new InstrumentDimensionRepository(dataSource);
    private final StrategyDimensionRepository strategies = new StrategyDimensionRepository(dataSource);
    private final BacktestRunFactRepository runs = new BacktestRunFactRepository(dataSource);

    @Test
    void findByKeyIsEmptyBeforeAnyMatchingRunExists() {
        long instrumentId = instruments.findOrCreate(TestSymbols.unique("TS-"), "USD", "EQUITY");
        long strategyId = strategies.findOrCreate("test-strategy-" + UUID.randomUUID());

        assertTrue(runs.findByKey(instrumentId, strategyId, "{}", 100000.0, "retail", "market-open").isEmpty());
    }

    @Test
    void insertedRunIsFoundByItsExactCacheKeyAndByIdWithTheFullResponseShape() {
        long instrumentId = instruments.findOrCreate(TestSymbols.unique("TS-"), "USD", "EQUITY");
        long strategyId = strategies.findOrCreate("test-strategy-" + UUID.randomUUID());
        String paramsJson = "{\"fast\":20,\"slow\":50}";
        double[] equityCurve = {100000.0, 100500.0, 92229.0094522352};

        BacktestRunRecord inserted = runs.insert(
                instrumentId, strategyId, paramsJson, 100000.0, "retail", "market-open", "java",
                100000.0, 92229.0094522352, -0.0777099054526478, -0.0411589578, 0.12, -0.1745144272,
                0.2414947349, 11, 0.63, equityCurve);

        var byKey = runs.findByKey(instrumentId, strategyId, paramsJson, 100000.0, "retail", "market-open");
        assertTrue(byKey.isPresent());
        assertEquals(inserted.id(), byKey.get().id());

        var byId = runs.findById(inserted.id());
        assertTrue(byId.isPresent());
        assertEquals(92229.0094522352, byId.get().finalEquity());
        assertEquals(11, byId.get().trades());
        assertEquals("java", byId.get().engineId());
        assertEquals(0.63, byId.get().timeInMarket());
        assertArrayEquals(equityCurve, byId.get().equityCurve());
    }

    @Test
    void aDifferentCacheKeyIsANonMatch() {
        long instrumentId = instruments.findOrCreate(TestSymbols.unique("TS-"), "USD", "EQUITY");
        long strategyId = strategies.findOrCreate("test-strategy-" + UUID.randomUUID());
        runs.insert(instrumentId, strategyId, "{\"fast\":20}", 100000.0, "retail", "market-open", "java",
                100000.0, 50000.0, -0.5, 0.01, 0.2, 0.5, 0.1, 3, 0.4, new double[] {100000.0, 50000.0});

        var differentParams = runs.findByKey(instrumentId, strategyId, "{\"fast\":10}", 100000.0, "retail", "market-open");
        var differentCash = runs.findByKey(instrumentId, strategyId, "{\"fast\":20}", 50000.0, "retail", "market-open");

        assertTrue(differentParams.isEmpty());
        assertTrue(differentCash.isEmpty());
    }
}
