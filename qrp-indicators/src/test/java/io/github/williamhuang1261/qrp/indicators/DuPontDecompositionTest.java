package io.github.williamhuang1261.qrp.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DuPontDecompositionTest {

    @Test
    @DisplayName("splits ROE into margin, turnover and leverage")
    void splitsReturnOnEquity() {
        DuPontDecomposition dupont = DuPontDecomposition.of(120.0, 1_000.0, 2_000.0, 800.0);

        assertEquals(0.12, dupont.netProfitMargin(), 1e-12);   // 120 / 1000
        assertEquals(0.5, dupont.assetTurnover(), 1e-12);      // 1000 / 2000
        assertEquals(2.5, dupont.equityMultiplier(), 1e-12);   // 2000 / 800
    }

    @Test
    @DisplayName("the factors multiply back to the directly computed ROE")
    void reconcilesWithDirectRoe() {
        double netIncome = 120.0;
        double equity = 800.0;

        DuPontDecomposition dupont = DuPontDecomposition.of(netIncome, 1_000.0, 2_000.0, equity);

        assertEquals(netIncome / equity, dupont.returnOnEquity(), 1e-12);
        assertTrue(dupont.reconciles(netIncome / equity));
    }

    @Test
    @DisplayName("a loss gives a negative ROE without breaking the identity")
    void handlesLosses() {
        DuPontDecomposition dupont = DuPontDecomposition.of(-50.0, 1_000.0, 2_000.0, 800.0);

        assertTrue(dupont.returnOnEquity() < 0.0);
        assertTrue(dupont.reconciles(-50.0 / 800.0));
    }

    @Test
    @DisplayName("refuses negative equity, where ROE stops meaning anything")
    void refusesNegativeEquity() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> DuPontDecomposition.of(120.0, 1_000.0, 2_000.0, -800.0));

        assertTrue(thrown.getMessage().contains("totalEquity"), thrown.getMessage());
    }

    @Test
    @DisplayName("refuses zero revenue or assets rather than returning infinity")
    void refusesDegenerateDenominators() {
        assertThrows(IllegalArgumentException.class,
                () -> DuPontDecomposition.of(120.0, 0.0, 2_000.0, 800.0));
        assertThrows(IllegalArgumentException.class,
                () -> DuPontDecomposition.of(120.0, 1_000.0, 0.0, 800.0));
    }

    @Test
    @DisplayName("reconciles() rejects a mismatched ROE")
    void reconcilesDetectsMismatch() {
        DuPontDecomposition dupont = DuPontDecomposition.of(120.0, 1_000.0, 2_000.0, 800.0);

        assertTrue(dupont.reconciles(0.15));
        org.junit.jupiter.api.Assertions.assertFalse(dupont.reconciles(0.20));
    }
}
