package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TreasuryCurveLoaderTest {

    private static final Path SAMPLE_DIRECTORY = Path.of("..", "data", "rates");
    private static final Path SAMPLE_FILE = SAMPLE_DIRECTORY.resolve("ust_cmt_2026-08-25.csv");

    @Test
    @DisplayName("loads the committed snapshot and converts percent to decimal")
    void loadsTheCommittedSnapshot() {
        RatesCurve curve = TreasuryCurveLoader.load(SAMPLE_FILE);

        // 10 Yr = 4.64 in the file, i.e. 0.0464 as a decimal.
        assertEquals(0.0464, curve.zeroRate(10.0), 1e-9);
        // 1 Mo = 3.79 -> 0.0379; the shortest tenor in the file, 1/12 year.
        assertEquals(0.0379, curve.zeroRate(1.0 / 12.0), 1e-6);
        assertEquals(30.0, curve.longestTenor(), 1e-9);
    }

    @Test
    @DisplayName("loadLatest finds the newest snapshot by filename")
    void loadLatestFindsTheNewestFile() {
        RatesCurve curve = TreasuryCurveLoader.loadLatest(SAMPLE_DIRECTORY);
        RatesCurve direct = TreasuryCurveLoader.load(SAMPLE_FILE);

        assertEquals(direct.zeroRate(10.0), curve.zeroRate(10.0), 1e-15);
    }

    @Test
    @DisplayName("rejects a missing file")
    void rejectsMissingFile() {
        assertThrows(
                java.io.UncheckedIOException.class,
                () -> TreasuryCurveLoader.load(SAMPLE_DIRECTORY.resolve("does_not_exist.csv")));
    }

    @Test
    @DisplayName("rejects a malformed row with the line number in the message")
    void rejectsMalformedRow(@org.junit.jupiter.api.io.TempDir Path tempDir) throws java.io.IOException {
        Path badFile = tempDir.resolve("ust_cmt_bad.csv");
        java.nio.file.Files.writeString(badFile, "tenor_years,yield_pct,tenor_label\n1.0,notanumber,1 Yr\n");

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> TreasuryCurveLoader.load(badFile));
        assertEquals(true, exception.getMessage().contains("ust_cmt_bad.csv:2"));
    }
}
