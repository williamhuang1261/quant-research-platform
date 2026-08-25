package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComputeEnginesTest {

    @Test
    @DisplayName("there is always an engine, even with nothing declared on the classpath")
    void alwaysResolvesAnEngine() {
        ComputeEngine engine = ComputeEngines.best();

        assertNotNull(engine);
        assertTrue(engine.isAvailable());
    }

    @Test
    @DisplayName("the portable engine is included in the discovered list")
    void portableIsAlwaysDiscovered() {
        assertTrue(ComputeEngines.discovered().stream().anyMatch(e -> e.id().equals("java")),
                "discovered: " + ComputeEngines.discovered().stream().map(ComputeEngine::id).toList());
    }

    @Test
    @DisplayName("with no native engine on the classpath, the portable one is selected")
    void fallsBackToPortable() {
        // qrp-native is not a dependency of this module, so this asserts the fallback
        // path that a reviewer without a C++ toolchain will take.
        assertEquals("java", ComputeEngines.best().id());
    }
}
