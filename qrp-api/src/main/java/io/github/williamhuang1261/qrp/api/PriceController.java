package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.MarketDataException;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.warehouse.InstrumentDimensionRepository;
import io.github.williamhuang1261.qrp.warehouse.PriceBarFactRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint: an indexed date-range query over {@code fact_price_bar},
 * backfilled by {@link WarehouseBackfillRunner} on startup. The one route in
 * this API that reads the warehouse as a data source in its own right,
 * rather than as a cache in front of a compute call.
 */
@RestController
@RequestMapping("/api/warehouse")
public class PriceController {

    private static final Path DEFAULT_DATA_DIRECTORY = Path.of("data/sample");

    private final DataSource dataSource;

    PriceController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/prices")
    public List<PriceBarResponse> prices(
            @RequestParam("symbol") String symbol,
            @RequestParam("timeframe") String timeframe,
            @RequestParam("from") String from,
            @RequestParam("to") String to) {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(DEFAULT_DATA_DIRECTORY);
        Instrument instrument = provider.available().stream()
                .filter(candidate -> candidate.symbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElseThrow(() -> new MarketDataException(
                        "unknown symbol '" + symbol + "'; available: "
                                + provider.available().stream().map(Instrument::symbol).toList()));

        InstrumentDimensionRepository instruments = new InstrumentDimensionRepository(dataSource);
        PriceBarFactRepository prices = new PriceBarFactRepository(dataSource);
        long instrumentId = instruments.findOrCreate(
                instrument.symbol(), instrument.currency(), instrument.assetClass().name());

        Timeframe tf = Timeframe.fromId(timeframe);
        return prices.range(instrumentId, tf.id(), Instant.parse(from), Instant.parse(to)).stream()
                .map(PriceBarResponse::from)
                .toList();
    }
}
