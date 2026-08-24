package io.github.williamhuang1261.qrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BarTest {

    private static final Instant T0 = Instant.parse("2024-01-02T21:00:00Z");

    @Test
    @DisplayName("accepts a well formed bar and derives typical price and range")
    void acceptsWellFormedBar() {
        Bar bar = new Bar(T0, 100.0, 105.0, 99.0, 104.0, 1_000L);

        assertEquals((105.0 + 99.0 + 104.0) / 3.0, bar.typicalPrice(), 1e-12);
        assertEquals(6.0, bar.range(), 1e-12);
    }

    @Test
    @DisplayName("rejects a high below the open or close")
    void rejectsImpossibleHigh() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bar(T0, 100.0, 99.0, 98.0, 98.5, 1L));
    }

    @Test
    @DisplayName("rejects a low above the open or close")
    void rejectsImpossibleLow() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bar(T0, 100.0, 105.0, 101.0, 104.0, 1L));
    }

    @Test
    @DisplayName("rejects non-positive, non-finite prices and negative volume")
    void rejectsUnusablePrices() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bar(T0, 0.0, 105.0, 0.0, 104.0, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Bar(T0, 100.0, Double.NaN, 99.0, 104.0, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Bar(T0, 100.0, 105.0, 99.0, 104.0, -1L));
    }

    @Test
    @DisplayName("orders by timestamp")
    void ordersByTimestamp() {
        Bar earlier = new Bar(T0, 100.0, 105.0, 99.0, 104.0, 1L);
        Bar later = new Bar(T0.plusSeconds(86_400), 104.0, 106.0, 103.0, 105.0, 1L);

        assertEquals(-1, Integer.signum(earlier.compareTo(later)));
    }
}
