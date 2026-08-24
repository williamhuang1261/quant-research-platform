package io.github.williamhuang1261.qrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeframeTest {

    @Test
    @DisplayName("ids round-trip and are case insensitive")
    void idsRoundTrip() {
        for (Timeframe timeframe : Timeframe.values()) {
            assertEquals(timeframe, Timeframe.fromId(timeframe.id().toUpperCase()));
        }
    }

    @Test
    @DisplayName("nominal durations are what the name says")
    void durationsMatchNames() {
        assertEquals(Duration.ofMinutes(5), Timeframe.MINUTE_5.duration());
        assertEquals(Duration.ofDays(1), Timeframe.DAY_1.duration());
    }

    @Test
    @DisplayName("an unknown id fails loudly")
    void unknownIdFails() {
        assertThrows(IllegalArgumentException.class, () -> Timeframe.fromId("3d"));
    }
}
