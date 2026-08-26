package io.github.williamhuang1261.qrp.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioConstraintsTest {

    @Test
    @DisplayName("longOnly is fully invested with no sector guideline")
    void longOnlyDefaults() {
        PortfolioConstraints constraints = PortfolioConstraints.longOnly(0.4, 0.2);

        assertEquals(1.0, constraints.leverage());
        assertTrue(constraints.sectors().isEmpty());
        assertFalse(constraints.hasSectorCaps());
    }

    @Test
    @DisplayName("rejects a max weight of zero or above one")
    void rejectsMaxWeightOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> PortfolioConstraints.longOnly(0.0, 0.2));
        assertThrows(IllegalArgumentException.class, () -> PortfolioConstraints.longOnly(1.1, 0.2));
    }

    @Test
    @DisplayName("accepts a max weight of exactly one")
    void acceptsMaxWeightAtUpperBound() {
        PortfolioConstraints.longOnly(1.0, 0.2);
    }

    @Test
    @DisplayName("rejects negative turnover but accepts zero")
    void turnoverBounds() {
        assertThrows(IllegalArgumentException.class, () -> PortfolioConstraints.longOnly(0.5, -0.1));
        PortfolioConstraints.longOnly(0.5, 0.0);
    }

    @Test
    @DisplayName("rejects non-positive leverage")
    void rejectsNonPositiveLeverage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortfolioConstraints(0.5, 0.2, 0.0, List.of(), Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortfolioConstraints(0.5, 0.2, -1.0, List.of(), Map.of()));
    }

    @Test
    @DisplayName("sectors and sectorCaps must both be empty or both populated")
    void sectorsAndCapsMustAgree() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortfolioConstraints(
                        0.5, 0.2, 1.0, List.of("Tech", "Energy"), Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortfolioConstraints(
                        0.5, 0.2, 1.0, List.of(), Map.of("Tech", 0.6)));
    }

    @Test
    @DisplayName("rejects a sector cap naming a sector with no assigned instrument")
    void rejectsUnknownSectorInCaps() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortfolioConstraints(
                        0.5, 0.2, 1.0, List.of("Tech", "Energy"), Map.of("Financials", 0.5)));
    }

    @Test
    @DisplayName("rejects a sector cap outside (0, leverage]")
    void rejectsSectorCapOutOfRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortfolioConstraints(
                        0.5, 0.2, 1.0, List.of("Tech", "Energy"), Map.of("Tech", 0.0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortfolioConstraints(
                        0.5, 0.2, 1.0, List.of("Tech", "Energy"), Map.of("Tech", 1.5)));
    }

    @Test
    @DisplayName("accepts a sector cap at exactly the leverage target")
    void acceptsSectorCapAtLeverage() {
        PortfolioConstraints constraints = new PortfolioConstraints(
                0.5, 0.2, 1.0, List.of("Tech", "Energy"), Map.of("Tech", 1.0));

        assertTrue(constraints.hasSectorCaps());
    }

    @Test
    @DisplayName("defensively copies sectors and sectorCaps so later mutation of the input is invisible")
    void defensiveCopy() {
        List<String> sectors = new ArrayList<>(List.of("Tech", "Energy"));
        Map<String, Double> caps = new HashMap<>(Map.of("Tech", 0.6));

        PortfolioConstraints constraints = new PortfolioConstraints(0.5, 0.2, 1.0, sectors, caps);
        sectors.add("Financials");
        caps.put("Energy", 0.4);

        assertEquals(2, constraints.sectors().size());
        assertEquals(1, constraints.sectorCaps().size());
    }
}
