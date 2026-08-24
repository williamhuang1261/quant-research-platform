package io.github.williamhuang1261.qrp.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.AssetClass;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.MarketDataException;
import io.github.williamhuang1261.qrp.core.Timeframe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvMarketDataProviderTest {

    private static final Instrument TEST = Instrument.equity("TEST");

    private static final String GOOD_ROWS = """
            timestamp,open,high,low,close,volume
            2024-01-02T21:00:00Z,100.00,101.00,99.00,100.50,1000
            2024-01-03T21:00:00Z,100.50,102.00,100.00,101.75,1100
            2024-01-04T21:00:00Z,101.75,103.00,101.00,102.25,1200
            """;

    private static Path directoryWith(Path root, String manifest, String rows) throws IOException {
        Files.writeString(root.resolve("instruments.csv"), manifest);
        Files.writeString(root.resolve("TEST_1d.csv"), rows);
        return root;
    }

    private static Path standardDirectory(Path root) throws IOException {
        return directoryWith(root, """
                symbol,currency,asset_class,timeframe,file
                TEST,USD,EQUITY,1d,TEST_1d.csv
                """, GOOD_ROWS);
    }

    @Test
    @DisplayName("loads every bar in the file when no range is given")
    void loadsWholeSeries(@TempDir Path root) throws IOException {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(standardDirectory(root));

        BarSeries series = provider.loadAll(TEST, Timeframe.DAY_1);

        assertEquals(3, series.size());
        assertEquals(100.50, series.get(0).close(), 1e-9);
        assertEquals(102.25, series.last().close(), 1e-9);
        assertEquals(Timeframe.DAY_1, series.timeframe());
    }

    @Test
    @DisplayName("the range is inclusive of from and exclusive of to")
    void filtersByHalfOpenRange(@TempDir Path root) throws IOException {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(standardDirectory(root));

        BarSeries series = provider.load(TEST, Timeframe.DAY_1,
                Instant.parse("2024-01-03T21:00:00Z"), Instant.parse("2024-01-04T21:00:00Z"));

        assertEquals(1, series.size());
        assertEquals(Instant.parse("2024-01-03T21:00:00Z"), series.get(0).timestamp());
    }

    @Test
    @DisplayName("lists what the manifest declares")
    void listsAvailableInstruments(@TempDir Path root) throws IOException {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(standardDirectory(root));

        assertEquals(1, provider.available().size());
        assertEquals(TEST, provider.available().get(0));
        assertEquals(java.util.List.of(Timeframe.DAY_1), provider.timeframesFor(TEST));
        assertEquals("csv", provider.id());
    }

    @Test
    @DisplayName("rejects out-of-order rows and names the file")
    void rejectsUnsortedTimestamps(@TempDir Path root) throws IOException {
        Path directory = directoryWith(root, """
                symbol,currency,asset_class,timeframe,file
                TEST,USD,EQUITY,1d,TEST_1d.csv
                """, """
                timestamp,open,high,low,close,volume
                2024-01-04T21:00:00Z,101.75,103.00,101.00,102.25,1200
                2024-01-02T21:00:00Z,100.00,101.00,99.00,100.50,1000
                """);
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(directory);

        MarketDataException thrown = assertThrows(MarketDataException.class,
                () -> provider.loadAll(TEST, Timeframe.DAY_1));

        assertTrue(thrown.getMessage().contains("TEST_1d.csv"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("strictly increasing"), thrown.getMessage());
    }

    @Test
    @DisplayName("rejects a non-positive price and names the line")
    void rejectsNonPositivePrice(@TempDir Path root) throws IOException {
        Path directory = directoryWith(root, """
                symbol,currency,asset_class,timeframe,file
                TEST,USD,EQUITY,1d,TEST_1d.csv
                """, """
                timestamp,open,high,low,close,volume
                2024-01-02T21:00:00Z,100.00,101.00,99.00,100.50,1000
                2024-01-03T21:00:00Z,0.00,102.00,0.00,101.75,1100
                """);
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(directory);

        MarketDataException thrown = assertThrows(MarketDataException.class,
                () -> provider.loadAll(TEST, Timeframe.DAY_1));

        assertTrue(thrown.getMessage().contains("TEST_1d.csv:3"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("must be positive"), thrown.getMessage());
    }

    @Test
    @DisplayName("rejects a row with the wrong number of columns and names the line")
    void rejectsMalformedRow(@TempDir Path root) throws IOException {
        Path directory = directoryWith(root, """
                symbol,currency,asset_class,timeframe,file
                TEST,USD,EQUITY,1d,TEST_1d.csv
                """, """
                timestamp,open,high,low,close,volume
                2024-01-02T21:00:00Z,100.00,101.00,99.00,100.50
                """);
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(directory);

        MarketDataException thrown = assertThrows(MarketDataException.class,
                () -> provider.loadAll(TEST, Timeframe.DAY_1));

        assertTrue(thrown.getMessage().contains("TEST_1d.csv:2"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("expected 6 columns"), thrown.getMessage());
    }

    @Test
    @DisplayName("rejects an unparseable timestamp")
    void rejectsBadTimestamp(@TempDir Path root) throws IOException {
        Path directory = directoryWith(root, """
                symbol,currency,asset_class,timeframe,file
                TEST,USD,EQUITY,1d,TEST_1d.csv
                """, """
                timestamp,open,high,low,close,volume
                02/01/2024,100.00,101.00,99.00,100.50,1000
                """);
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(directory);

        assertThrows(MarketDataException.class, () -> provider.loadAll(TEST, Timeframe.DAY_1));
    }

    @Test
    @DisplayName("rejects a wrong header instead of treating it as data")
    void rejectsBadHeader(@TempDir Path root) throws IOException {
        Path directory = directoryWith(root, """
                symbol,currency,asset_class,timeframe,file
                TEST,USD,EQUITY,1d,TEST_1d.csv
                """, """
                date,o,h,l,c,v
                2024-01-02T21:00:00Z,100.00,101.00,99.00,100.50,1000
                """);
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(directory);

        MarketDataException thrown = assertThrows(MarketDataException.class,
                () -> provider.loadAll(TEST, Timeframe.DAY_1));

        assertTrue(thrown.getMessage().contains("expected header"), thrown.getMessage());
    }

    @Test
    @DisplayName("a manifest pointing at a missing file fails at construction, not at first use")
    void missingSeriesFileFailsEagerly(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("instruments.csv"), """
                symbol,currency,asset_class,timeframe,file
                TEST,USD,EQUITY,1d,ABSENT_1d.csv
                """);

        MarketDataException thrown = assertThrows(MarketDataException.class,
                () -> CsvMarketDataProvider.ofDirectory(root));

        assertTrue(thrown.getMessage().contains("ABSENT_1d.csv"), thrown.getMessage());
    }

    @Test
    @DisplayName("a duplicated manifest entry is a configuration error")
    void duplicateManifestEntryFails(@TempDir Path root) throws IOException {
        Path directory = directoryWith(root, """
                symbol,currency,asset_class,timeframe,file
                TEST,USD,EQUITY,1d,TEST_1d.csv
                TEST,USD,EQUITY,1d,TEST_1d.csv
                """, GOOD_ROWS);

        MarketDataException thrown = assertThrows(MarketDataException.class,
                () -> CsvMarketDataProvider.ofDirectory(directory));

        assertTrue(thrown.getMessage().contains("duplicates"), thrown.getMessage());
    }

    @Test
    @DisplayName("an unknown series lists what is available")
    void unknownSeriesIsExplained(@TempDir Path root) throws IOException {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(standardDirectory(root));

        MarketDataException thrown = assertThrows(MarketDataException.class,
                () -> provider.loadAll(Instrument.equity("NOPE"), Timeframe.DAY_1));

        assertTrue(thrown.getMessage().contains("available"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("TEST"), thrown.getMessage());
    }

    @Test
    @DisplayName("a mismatched asset class is reported rather than silently served")
    void instrumentMismatchIsReported(@TempDir Path root) throws IOException {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(standardDirectory(root));

        MarketDataException thrown = assertThrows(MarketDataException.class,
                () -> provider.loadAll(new Instrument("TEST", "USD", AssetClass.ETF), Timeframe.DAY_1));

        assertTrue(thrown.getMessage().contains("manifest declares TEST as EQUITY/USD"), thrown.getMessage());
    }

    @Test
    @DisplayName("a directory without a manifest is refused")
    void missingManifestIsRefused(@TempDir Path root) {
        MarketDataException thrown = assertThrows(MarketDataException.class,
                () -> CsvMarketDataProvider.ofDirectory(root));

        assertTrue(thrown.getMessage().contains("instruments.csv"), thrown.getMessage());
    }
}
