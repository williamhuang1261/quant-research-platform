package io.github.williamhuang1261.qrp.onchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden-run test: the expected values below were computed independently in
 * Python (Decimal arithmetic, 50-digit precision) directly from the
 * committed {@code data/onchain/amm_swaps_2026-09-03.csv}, not derived from
 * this class's own implementation — the point is to catch this class
 * disagreeing with an independent calculation, not to confirm it agrees
 * with itself. Matches the golden-run convention {@code merge_gate.py}
 * (Extension 10) already protects elsewhere in this platform.
 */
class AmmLpAnalyzerTest {

    private static final Path REAL_CSV = Path.of("../data/onchain/amm_swaps_2026-09-03.csv");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.0000000001");

    @Test
    void impermanentLossOnTheCommittedSnapshotMatchesTheIndependentPythonCalculation() {
        List<AmmSwapRow> rows = AmmSwapCsvReader.read(REAL_CSV);

        BigDecimal il = AmmLpAnalyzer.impermanentLossFraction(rows);

        // python: 2*sqrt(r)/(r+1) - 1 with r = pt/p0 = 1.0448262247298159558...
        BigDecimal expected = new BigDecimal("-0.00024031103055390253619073065044982520662489790503");
        assertTrue(il.subtract(expected).abs().compareTo(TOLERANCE) < 0,
                "expected " + expected + " within " + TOLERANCE + ", got " + il);
        // IL is a real cost of providing liquidity through a moving price: always <= 0.
        assertTrue(il.compareTo(BigDecimal.ZERO) <= 0);
    }

    @Test
    void cumulativeFeeIncomeOnTheCommittedSnapshotMatchesTheIndependentPythonCalculation() {
        List<AmmSwapRow> rows = AmmSwapCsvReader.read(REAL_CSV);

        BigDecimal feeIncomeRaw = AmmLpAnalyzer.cumulativeFeeIncomeInToken1(rows);
        BigDecimal feeIncomeToken1 = feeIncomeRaw.divide(new BigDecimal(BigInteger.TEN.pow(18)));

        // python: cumulative_fee_token1 / 1e18 = 1294.8828281398548566474167391747649373583949253225
        BigDecimal expected = new BigDecimal("1294.8828281398548566474167391747649373583949253225");
        BigDecimal relativeTolerance = new BigDecimal("0.0000000001");
        assertTrue(feeIncomeToken1.subtract(expected).abs().compareTo(relativeTolerance) < 0,
                "expected " + expected + " token1, got " + feeIncomeToken1);
        assertTrue(feeIncomeToken1.compareTo(BigDecimal.ZERO) > 0, "fee income must be strictly positive");
    }

    @Test
    void impermanentLossIsZeroWhenThePriceHasNotMoved() {
        AmmSwapRow noMove = new AmmSwapRow(
                1L, true,
                BigInteger.valueOf(1_000), BigInteger.valueOf(997),
                BigInteger.valueOf(1_000_000), BigInteger.valueOf(1_000_000),
                BigInteger.valueOf(1_000_000), BigInteger.valueOf(1_000_000));

        BigDecimal il = AmmLpAnalyzer.impermanentLossFraction(List.of(noMove));

        assertEquals(0, il.compareTo(BigDecimal.ZERO));
    }

    @Test
    void rejectsAnEmptySwapList() {
        assertThrows(IllegalArgumentException.class, () -> AmmLpAnalyzer.impermanentLossFraction(List.of()));
    }
}
