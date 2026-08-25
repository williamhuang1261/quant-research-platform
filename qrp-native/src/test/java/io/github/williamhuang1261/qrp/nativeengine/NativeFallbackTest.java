package io.github.williamhuang1261.qrp.nativeengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import io.github.williamhuang1261.qrp.stats.BlockBootstrap;
import io.github.williamhuang1261.qrp.stats.ComputeEngines;
import io.github.williamhuang1261.qrp.stats.ConfidenceInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What happens when the kernel is not there — the configuration most reviewers
 * will be in. These tests run whether or not the library was built.
 */
class NativeFallbackTest {

    private final NativeComputeEngine engine = new NativeComputeEngine();

    private static double[] sample() {
        double[] returns = new double[200];
        for (int i = 0; i < returns.length; i++) {
            returns[i] = 0.001 + 0.01 * Math.sin(i / 5.0);
        }
        return returns;
    }

    @Test
    @DisplayName("availability is a question with an answer either way, never an exception")
    void availabilityIsAlwaysAnswerable() {
        boolean available = engine.isAvailable();

        assertEquals(available, engine.unavailableReason().isEmpty());
        if (!available) {
            assertFalse(engine.unavailableReason().orElseThrow().isBlank(),
                    "an unavailable kernel must say why");
        }
    }

    @Test
    @DisplayName("an unavailable kernel refuses to compute rather than returning wrong numbers")
    void unavailableKernelRefusesToCompute() {
        if (engine.isAvailable()) {
            return;
        }

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> engine.bootstrapMeans(sample(), 100, 10, 1L));
        assertTrue(thrown.getMessage().contains("unavailable"), thrown.getMessage());
    }

    @Test
    @DisplayName("selection never returns an engine that cannot run")
    void selectionOnlyReturnsAvailableEngines() {
        ComputeEngine selected = ComputeEngines.best();

        assertNotNull(selected);
        assertTrue(selected.isAvailable());
        // Whichever was chosen, it must be able to answer.
        assertEquals(500, selected.bootstrapMeans(sample(), 500, 10, 1L).length);
    }

    @Test
    @DisplayName("the native engine is discovered through the SPI when it is on the classpath")
    void isDiscoveredThroughTheSpi() {
        assertTrue(ComputeEngines.discovered().stream().anyMatch(e -> e.id().equals("openmp")),
                "discovered: " + ComputeEngines.discovered().stream().map(ComputeEngine::id).toList());
        assertTrue(ComputeEngines.discovered().stream().anyMatch(e -> e.id().equals("java")));
    }

    @Test
    @DisplayName("callers get the same interval whichever engine served it")
    void resultsDoNotDependOnTheEngine() {
        ConfidenceInterval viaPortable =
                new BlockBootstrap(ComputeEngines.portable()).meanInterval(sample(), 2_000, 20, 0.95, 5L);
        ConfidenceInterval viaBest =
                new BlockBootstrap(ComputeEngines.best()).meanInterval(sample(), 2_000, 20, 0.95, 5L);

        assertEquals(viaPortable, viaBest);
    }
}
