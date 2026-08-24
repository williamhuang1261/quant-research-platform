package io.github.williamhuang1261.qrp.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.AssetClass;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Loads the data set that ships with the repository. If a clone cannot read
 * {@code data/sample}, nothing else in the project can be demonstrated, so this
 * is the closest thing the data module has to a smoke test.
 */
class SampleDataTest {

    /** Surefire runs with the module directory as the working directory. */
    private static final Path SAMPLE_DIRECTORY = Path.of("..", "data", "sample");

    private static CsvMarketDataProvider provider;

    @BeforeAll
    static void loadProvider() {
        assertTrue(Files.isDirectory(SAMPLE_DIRECTORY),
                "bundled sample data is missing at " + SAMPLE_DIRECTORY.toAbsolutePath());
        provider = CsvMarketDataProvider.ofDirectory(SAMPLE_DIRECTORY);
    }

    @Test
    @DisplayName("ships three synthetic instruments")
    void shipsThreeInstruments() {
        List<Instrument> available = provider.available();

        assertEquals(3, available.size());
        assertEquals(List.of("SYNA", "SYNB", "SYNETF"),
                available.stream().map(Instrument::symbol).toList());
        assertEquals(AssetClass.ETF, available.get(2).assetClass());
    }

    @Test
    @DisplayName("every bundled series carries two years of daily bars")
    void everySeriesLoads() {
        for (Instrument instrument : provider.available()) {
            BarSeries series = provider.loadAll(instrument, Timeframe.DAY_1);

            assertEquals(504, series.size(), instrument + " should hold 504 bars");
            assertTrue(series.start().isBefore(series.end()));
            for (double close : series.closes()) {
                assertTrue(close > 0.0, instrument + " has a non-positive close");
            }
        }
    }

    @Test
    @DisplayName("the only gaps are weekends")
    void hasNoUnexpectedGaps() {
        BarSeries series = provider.loadAll(Instrument.equity("SYNA"), Timeframe.DAY_1);

        // Friday to Monday is three days; anything longer would mean missing rows.
        assertEquals(List.of(), series.gapsLongerThan(Duration.ofDays(3)));
    }
}
