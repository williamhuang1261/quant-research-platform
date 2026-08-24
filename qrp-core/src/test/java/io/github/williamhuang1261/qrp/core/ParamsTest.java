package io.github.williamhuang1261.qrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParamsTest {

    @Test
    @DisplayName("with() returns a new instance and leaves the original alone")
    void withIsImmutable() {
        Params base = Params.of("period", 20);

        Params extended = base.with("threshold", 1.5);

        assertEquals(1, base.asMap().size());
        assertEquals(2, extended.asMap().size());
        assertEquals(20.0, extended.require("period"), 1e-12);
    }

    @Test
    @DisplayName("a missing required parameter names the keys that are present")
    void missingParameterIsLoud() {
        Params params = Params.of("period", 20);

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> params.require("window"));
        assertTrue(thrown.getMessage().contains("period"));
    }

    @Test
    @DisplayName("requireInt rejects a fractional value instead of truncating it")
    void requireIntRejectsFractions() {
        Params params = Params.of("period", 14.5);

        assertThrows(IllegalArgumentException.class, () -> params.requireInt("period"));
    }

    @Test
    @DisplayName("requireInt accepts an integral double")
    void requireIntAcceptsWholeNumbers() {
        assertEquals(14, Params.of("period", 14.0).requireInt("period"));
    }

    @Test
    @DisplayName("rejects non-finite values at the boundary")
    void rejectsNonFinite() {
        assertThrows(IllegalArgumentException.class, () -> Params.of("period", Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> Params.from(Map.of("period", Double.POSITIVE_INFINITY)));
    }

    @Test
    @DisplayName("defaults are used only when the key is absent")
    void defaultsApplyOnlyWhenAbsent() {
        Params params = Params.of("period", 20);

        assertEquals(20, params.getIntOrDefault("period", 50));
        assertEquals(50, params.getIntOrDefault("window", 50));
    }
}
