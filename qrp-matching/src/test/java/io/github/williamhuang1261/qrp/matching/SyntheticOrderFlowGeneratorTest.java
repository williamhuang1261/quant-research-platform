package io.github.williamhuang1261.qrp.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyntheticOrderFlowGeneratorTest {

    private static final Bar BAR = new Bar(Instant.parse("2026-01-02T21:00:00Z"), 100.0, 102.0, 98.0, 101.0, 10_000);

    @Test
    void generatesOrdersOnBothSidesAroundTheOpen() {
        SyntheticOrderFlowGenerator.Flow flow = SyntheticOrderFlowGenerator.fromBar(BAR, 5, 0.5, 0.1, 1);

        assertFalse(flow.orders().isEmpty());
        assertTrue(flow.orders().stream().anyMatch(o -> o.side() == Side.BUY));
        assertTrue(flow.orders().stream().anyMatch(o -> o.side() == Side.SELL));
    }

    @Test
    void bidsRestBelowOpenAndAsksRestAboveIt() {
        SyntheticOrderFlowGenerator.Flow flow = SyntheticOrderFlowGenerator.fromBar(BAR, 5, 0.5, 0.1, 1);

        for (Order order : flow.orders()) {
            if (order.side() == Side.BUY) {
                assertTrue(order.limitPrice() < BAR.open(), "bid must rest below the mid");
            } else {
                assertTrue(order.limitPrice() > BAR.open(), "ask must rest above the mid");
            }
        }
    }

    @Test
    void generatedOrdersNeverCrossEachOther() {
        SyntheticOrderFlowGenerator.Flow flow = SyntheticOrderFlowGenerator.fromBar(BAR, 5, 0.5, 0.1, 1);
        MatchingEngine engine = new MatchingEngine();

        for (Order order : flow.orders()) {
            List<Trade> trades = engine.submit(order);
            assertTrue(trades.isEmpty(), "synthetic flow must rest without matching against itself");
        }
    }

    @Test
    void nextAvailableIdIsUnusedByAnyGeneratedOrder() {
        SyntheticOrderFlowGenerator.Flow flow = SyntheticOrderFlowGenerator.fromBar(BAR, 5, 0.5, 0.1, 1);
        assertTrue(flow.orders().stream().noneMatch(o -> o.id() == flow.nextAvailableId()));
    }

    @Test
    void idsAreAssignedSequentiallyFromStartId() {
        SyntheticOrderFlowGenerator.Flow flow = SyntheticOrderFlowGenerator.fromBar(BAR, 3, 0.5, 0.1, 100);
        long expected = 100;
        for (Order order : flow.orders()) {
            assertEquals(expected++, order.id());
        }
        assertEquals(expected, flow.nextAvailableId());
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> SyntheticOrderFlowGenerator.fromBar(BAR, 0, 0.5, 0.1, 1));
        assertThrows(IllegalArgumentException.class, () -> SyntheticOrderFlowGenerator.fromBar(BAR, 5, 0.0, 0.1, 1));
        assertThrows(IllegalArgumentException.class, () -> SyntheticOrderFlowGenerator.fromBar(BAR, 5, 0.5, 0.0, 1));
        assertThrows(IllegalArgumentException.class, () -> SyntheticOrderFlowGenerator.fromBar(BAR, 5, 0.5, 0.1, -1));
    }
}
