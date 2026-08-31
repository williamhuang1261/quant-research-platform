package io.github.williamhuang1261.qrp.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class WarehousePriceLoaderTest {

    private final DataSource dataSource = WarehouseDataSourceFactory.create();

    @Test
    void backfillsEveryBarFromTheBundledSampleData() {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(Path.of("../data/sample"));

        int written = WarehousePriceLoader.backfill(dataSource, provider);

        int expected = provider.available().stream()
                .mapToInt(instrument -> provider.timeframesFor(instrument).stream()
                        .mapToInt(tf -> provider.loadAll(instrument, tf).size())
                        .sum())
                .sum();
        assertEquals(expected, written, "every bar the CSV provider has must be written exactly once");
        assertTrue(written > 0, "the bundled sample data must not be empty");
    }

    @Test
    void aSecondBackfillIsANonEventBecauseUpsertIsIdempotent() {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(Path.of("../data/sample"));
        InstrumentDimensionRepository instruments = new InstrumentDimensionRepository(dataSource);
        PriceBarFactRepository prices = new PriceBarFactRepository(dataSource);

        WarehousePriceLoader.backfill(dataSource, provider);
        WarehousePriceLoader.backfill(dataSource, provider);

        Instrument first = provider.available().get(0);
        Timeframe timeframe = provider.timeframesFor(first).get(0);
        long instrumentId = instruments.findOrCreate(
                first.symbol(), first.currency(), first.assetClass().name());
        var series = provider.loadAll(first, timeframe);
        var stored = prices.range(instrumentId, timeframe.id(), series.start(), series.end().plusSeconds(1));

        assertEquals(series.size(), stored.size(), "a repeat backfill must not duplicate rows");
    }
}
