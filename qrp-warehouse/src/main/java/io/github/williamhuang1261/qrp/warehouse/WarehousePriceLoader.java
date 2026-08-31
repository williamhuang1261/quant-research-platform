package io.github.williamhuang1261.qrp.warehouse;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Backfills {@code fact_price_bar} from a {@link CsvMarketDataProvider},
 * i.e. from the same sample data every other front end (the CLI, the
 * workbench, {@code qrp-api}) already reads. Every write goes through
 * {@link PriceBarFactRepository#upsert}, so re-running this against
 * unchanged data is a non-event -- the property {@code qrp-api} relies on to
 * call it on every startup rather than once, by hand.
 */
public final class WarehousePriceLoader {

    private WarehousePriceLoader() {
    }

    /** @return the number of bars written (upserted), across every instrument and timeframe the provider has. */
    public static int backfill(DataSource dataSource, CsvMarketDataProvider provider) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(provider, "provider");
        InstrumentDimensionRepository instruments = new InstrumentDimensionRepository(dataSource);
        PriceBarFactRepository prices = new PriceBarFactRepository(dataSource);

        int written = 0;
        for (Instrument instrument : provider.available()) {
            long instrumentId = instruments.findOrCreate(
                    instrument.symbol(), instrument.currency(), instrument.assetClass().name());
            for (Timeframe timeframe : provider.timeframesFor(instrument)) {
                BarSeries series = provider.loadAll(instrument, timeframe);
                for (Bar bar : series.bars()) {
                    prices.upsert(instrumentId, timeframe.id(), bar);
                    written++;
                }
            }
        }
        return written;
    }
}
