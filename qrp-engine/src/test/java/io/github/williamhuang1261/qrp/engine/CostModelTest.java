package io.github.williamhuang1261.qrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CostModelTest {

    @Test
    @DisplayName("slippage always moves against the trade")
    void slippageMovesAgainstTheTrade() {
        CostModel costs = new CostModel(0.0, 0.0, 10.0);   // 10 bps

        assertEquals(100.10, costs.fillPrice(100.0, true), 1e-12);
        assertEquals(99.90, costs.fillPrice(100.0, false), 1e-12);
    }

    @Test
    @DisplayName("commission combines a rate on notional with a ticket fee")
    void commissionCombinesRateAndTicket() {
        CostModel costs = new CostModel(2.0, 1.0, 0.0);   // 2 bps + $1

        assertEquals(1.0 + 10_000.0 * 0.0002, costs.commission(10_000.0), 1e-12);
        assertEquals(costs.commission(10_000.0), costs.commission(-10_000.0), 1e-12,
                "a sale costs the same as a purchase of the same size");
    }

    @Test
    @DisplayName("none() charges nothing, retail() charges something")
    void presets() {
        assertEquals(100.0, CostModel.none().fillPrice(100.0, true), 1e-12);
        assertEquals(0.0, CostModel.none().commission(10_000.0), 1e-12);
        assertTrue(CostModel.retail().commission(10_000.0) > 0.0);
    }

    @Test
    @DisplayName("rejects negative costs, which would pay the account to trade")
    void rejectsNegativeCosts() {
        assertThrows(IllegalArgumentException.class, () -> new CostModel(-1.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CostModel(0.0, -1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CostModel(0.0, 0.0, -1.0));
    }
}
