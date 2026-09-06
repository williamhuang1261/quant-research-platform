package io.github.williamhuang1261.qrp.equity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EquityFundamentalsTest {

    private static final Path SAMPLE_DIRECTORY = Path.of("..", "data", "equity");
    private static final Path SAMPLE_FILE = SAMPLE_DIRECTORY.resolve("fundamentals_2026-09-06.csv");

    @Test
    @DisplayName("loads the committed snapshot, keyed by ticker")
    void loadsTheCommittedSnapshot() {
        Map<String, EquityFundamentals> byTicker = EquityFundamentals.load(SAMPLE_FILE);

        assertTrue(byTicker.containsKey("AAPL"));
        assertTrue(byTicker.containsKey("MSFT"));
        assertEquals("Apple Inc.", byTicker.get("AAPL").companyName());
        assertEquals(8.74, byTicker.get("AAPL").trailingEps(), 1e-9);
    }

    @Test
    @DisplayName("loadLatest finds the newest snapshot by filename")
    void loadLatestFindsTheNewestFile() {
        Map<String, EquityFundamentals> byTicker = EquityFundamentals.loadLatest(SAMPLE_DIRECTORY);
        Map<String, EquityFundamentals> direct = EquityFundamentals.load(SAMPLE_FILE);

        assertEquals(direct.get("AAPL"), byTicker.get("AAPL"));
    }

    @Test
    @DisplayName("rejects a missing file")
    void rejectsMissingFile() {
        assertThrows(
                java.io.UncheckedIOException.class,
                () -> EquityFundamentals.load(SAMPLE_DIRECTORY.resolve("does_not_exist.csv")));
    }

    @Test
    @DisplayName("rejects a malformed row with the line number in the message")
    void rejectsMalformedRow(@org.junit.jupiter.api.io.TempDir Path tempDir) throws java.io.IOException {
        Path badFile = tempDir.resolve("fundamentals_bad.csv");
        java.nio.file.Files.writeString(
                badFile,
                "ticker,company_name,sector,fetch_date,total_revenue,trailing_eps,net_margin,"
                        + "diluted_shares_outstanding,trailing_pe,current_price,free_cash_flow\n"
                        + "AAPL,Apple Inc.,Technology,2026-09-06,notanumber,8.74,0.28,14594180000,36.6,319.97,107721875456\n");

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> EquityFundamentals.load(badFile));
        assertEquals(true, exception.getMessage().contains("fundamentals_bad.csv:2"));
    }
}
