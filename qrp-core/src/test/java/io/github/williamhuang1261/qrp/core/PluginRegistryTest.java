package io.github.williamhuang1261.qrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PluginRegistryTest {

    private record Plugin(String id) {
    }

    private static PluginRegistry<Plugin> registryOf(Plugin... plugins) {
        return PluginRegistry.of(Plugin.class, List.of(plugins), Plugin::id);
    }

    @Test
    @DisplayName("indexes plugins by id and keeps declaration order")
    void indexesById() {
        Plugin sma = new Plugin("sma");
        Plugin ema = new Plugin("ema");

        PluginRegistry<Plugin> registry = registryOf(sma, ema);

        assertEquals(2, registry.size());
        assertEquals(List.of("sma", "ema"), List.copyOf(registry.ids()));
        assertSame(sma, registry.require("sma"));
        assertEquals(Optional.of(ema), registry.find("ema"));
    }

    @Test
    @DisplayName("a duplicate id names both classes instead of silently overriding")
    void rejectsDuplicateIds() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> registryOf(new Plugin("sma"), new Plugin("sma")));

        assertTrue(thrown.getMessage().contains("duplicate"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("sma"), thrown.getMessage());
    }

    @Test
    @DisplayName("a blank id is a broken plugin")
    void rejectsBlankId() {
        assertThrows(IllegalStateException.class, () -> registryOf(new Plugin("  ")));
    }

    @Test
    @DisplayName("an unknown id lists what is available")
    void unknownIdIsExplained() {
        PluginRegistry<Plugin> registry = registryOf(new Plugin("sma"));

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> registry.require("nope"));

        assertTrue(thrown.getMessage().contains("available: [sma]"), thrown.getMessage());
        assertEquals(Optional.empty(), registry.find("nope"));
    }
}
