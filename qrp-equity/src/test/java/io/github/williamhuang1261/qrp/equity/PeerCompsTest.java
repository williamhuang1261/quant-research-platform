package io.github.williamhuang1261.qrp.equity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeerCompsTest {

    private static final Path SAMPLE_DIRECTORY = Path.of("..", "data", "equity");
    private static final Path SAMPLE_FILE = SAMPLE_DIRECTORY.resolve("comps_2026-09-06.csv");

    @Test
    @DisplayName("loads the committed snapshot, keyed by covered ticker")
    void loadsTheCommittedSnapshot() {
        Map<String, List<Double>> byTicker = PeerComps.load(SAMPLE_FILE);

        assertTrue(byTicker.containsKey("AAPL"));
        assertEquals(3, byTicker.get("AAPL").size());
    }

    @Test
    @DisplayName("loadLatest finds the newest snapshot by filename")
    void loadLatestFindsTheNewestFile() {
        Map<String, List<Double>> byTicker = PeerComps.loadLatest(SAMPLE_DIRECTORY);
        Map<String, List<Double>> direct = PeerComps.load(SAMPLE_FILE);

        assertEquals(direct.get("AAPL"), byTicker.get("AAPL"));
    }

    @Test
    @DisplayName("rejects a missing file")
    void rejectsMissingFile() {
        assertThrows(
                java.io.UncheckedIOException.class,
                () -> PeerComps.load(SAMPLE_DIRECTORY.resolve("does_not_exist.csv")));
    }
}
