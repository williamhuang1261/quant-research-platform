package io.github.williamhuang1261.qrp.onchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AmmSwapCsvReaderTest {

    private static final Path REAL_CSV = Path.of("../data/onchain/amm_swaps_2026-09-03.csv");

    @Test
    void readsAllRowsFromTheCommittedSnapshot() {
        List<AmmSwapRow> rows = AmmSwapCsvReader.read(REAL_CSV);

        assertEquals(40, rows.size());
        AmmSwapRow first = rows.get(0);
        assertEquals(9L, first.blockNumber());
        assertFalse(first.tokenInIsToken0(), "the committed snapshot's first swap sells token1 into the pool");
        assertTrue(first.amountIn().compareTo(BigInteger.ZERO) > 0);
        assertTrue(first.reserve0Before().compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    void everyRowsReservesAreInternallyConsistentWithTheNextRowsBefore() {
        List<AmmSwapRow> rows = AmmSwapCsvReader.read(REAL_CSV);
        for (int i = 0; i + 1 < rows.size(); i++) {
            AmmSwapRow row = rows.get(i);
            AmmSwapRow next = rows.get(i + 1);
            assertEquals(row.reserve0After(), next.reserve0Before(),
                    "row " + i + "'s reserve0After must equal row " + (i + 1) + "'s reserve0Before");
            assertEquals(row.reserve1After(), next.reserve1Before(),
                    "row " + i + "'s reserve1After must equal row " + (i + 1) + "'s reserve1Before");
        }
    }

    @Test
    void constantProductNeverDecreasesAcrossAnySwap() {
        List<AmmSwapRow> rows = AmmSwapCsvReader.read(REAL_CSV);
        for (AmmSwapRow row : rows) {
            BigInteger kBefore = row.reserve0Before().multiply(row.reserve1Before());
            BigInteger kAfter = row.reserve0After().multiply(row.reserve1After());
            assertTrue(kAfter.compareTo(kBefore) >= 0,
                    "post-fee constant product must never decrease, block " + row.blockNumber());
        }
    }

    @Test
    void rejectsAWrongHeader(@org.junit.jupiter.api.io.TempDir Path tempDir) throws IOException {
        Path badFile = tempDir.resolve("bad.csv");
        Files.writeString(badFile, "not,the,right,header\n1,2,3,4\n");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AmmSwapCsvReader.read(badFile));
        assertTrue(ex.getMessage().contains("bad.csv:1"));
    }

    @Test
    void rejectsARowWithTheWrongFieldCount(@org.junit.jupiter.api.io.TempDir Path tempDir) throws IOException {
        Path badFile = tempDir.resolve("bad.csv");
        Files.writeString(badFile,
                "block_number,token_in,amount_in,amount_out,reserve0_before,reserve1_before,"
                        + "reserve0_after,reserve1_after,realized_price_1e18\n"
                        + "1,token0,100\n");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AmmSwapCsvReader.read(badFile));
        assertTrue(ex.getMessage().contains("bad.csv:2"));
        assertFalse(ex.getMessage().contains("token1"));
    }
}
