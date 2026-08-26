package io.github.williamhuang1261.qrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link ExecutionModel} contract itself, independent of which
 * implementation is under test: "no fill" is {@link Optional#empty()}, never
 * a {@link ExecutionModel.Fill} with a zero {@code deltaShares}. A caller that
 * cannot tell "declined to trade" from "traded exactly zero shares" cannot
 * report a fill rate, which is the whole point of adding a second execution
 * model in the first place.
 */
class ExecutionModelContractTest {

    private static final Bar BAR = new Bar(Instant.parse("2024-01-02T21:00:00Z"), 100.0, 101.0, 99.0, 100.5, 1_000L);

    @Test
    @DisplayName("a Fill record refuses a zero deltaShares; that outcome must be Optional.empty() instead")
    void fillRejectsZeroDelta() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionModel.Fill(100.0, 0.0, 0.0));
    }

    @Test
    @DisplayName("a Fill record refuses a non-positive or non-finite price")
    void fillRejectsBadPrice() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionModel.Fill(0.0, 10.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionModel.Fill(-5.0, 10.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionModel.Fill(Double.NaN, 10.0, 0.0));
    }

    @Test
    @DisplayName("a Fill record refuses negative commission")
    void fillRejectsNegativeCommission() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionModel.Fill(100.0, 10.0, -1.0));
    }

    @Test
    @DisplayName("MarketOpenExecutionModel declines a fill when the target is already met, as Optional.empty()")
    void marketOpenDeclinesWhenAlreadyAtTarget() {
        // 100 shares held, 10,000 cash, price 100: exposure is already 100% of equity.
        ExecutionModel model = new MarketOpenExecutionModel(CostModel.none());

        Optional<ExecutionModel.Fill> fill = model.fill(BAR, 1.0, 0.0, 100.0);

        assertTrue(fill.isEmpty(), "already at target should decline the fill rather than return a zero-size one");
    }

    @Test
    @DisplayName("MarketOpenExecutionModel fills when the target actually changes exposure")
    void marketOpenFillsOnARealTargetChange() {
        ExecutionModel model = new MarketOpenExecutionModel(CostModel.none());

        Optional<ExecutionModel.Fill> fill = model.fill(BAR, 1.0, 10_000.0, 0.0);

        assertTrue(fill.isPresent());
        assertEquals(100.0, fill.get().price(), 1e-12, "no slippage means the fill is exactly the open");
        assertFalse(fill.get().deltaShares() == 0.0);
    }
}
