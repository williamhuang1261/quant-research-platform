package io.github.williamhuang1261.qrp.equity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComparableMultiplesValuationTest {

    @Test
    @DisplayName("averagePeerPe is the unweighted mean of the peer multiples")
    void averagePeerPeIsTheMean() {
        List<Double> peers = List.of(20.0, 24.0, 22.0);

        assertEquals(22.0, ComparableMultiplesValuation.averagePeerPe(peers), 1e-9);
    }

    @Test
    @DisplayName("impliedValuePerShare is trailing EPS times the average peer P/E")
    void impliedValuePerShareMultipliesEpsByAveragePe() {
        List<Double> peers = List.of(20.0, 30.0);

        assertEquals(8.74 * 25.0, ComparableMultiplesValuation.impliedValuePerShare(8.74, peers), 1e-9);
    }

    @Test
    @DisplayName("an empty peer list is rejected")
    void emptyPeerListRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ComparableMultiplesValuation.averagePeerPe(List.of()));
    }

    @Test
    @DisplayName("a non-positive peer P/E is rejected")
    void nonPositivePeerPeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ComparableMultiplesValuation.averagePeerPe(List.of(20.0, -5.0)));
    }
}
