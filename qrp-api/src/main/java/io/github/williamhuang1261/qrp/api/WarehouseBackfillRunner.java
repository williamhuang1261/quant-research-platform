package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.warehouse.WarehousePriceLoader;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Backfills {@code fact_price_bar} from the CLI's own default sample data
 * directory on every {@code qrp-api} startup. Safe to run every time:
 * {@link WarehousePriceLoader#backfill} upserts, so a restart is a
 * non-event, not a growing table.
 */
@Component
class WarehouseBackfillRunner implements CommandLineRunner {

    private static final String DEFAULT_DATA_DIRECTORY = "data/sample";

    private final DataSource dataSource;

    WarehouseBackfillRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(Path.of(DEFAULT_DATA_DIRECTORY));
        WarehousePriceLoader.backfill(dataSource, provider);
    }
}
