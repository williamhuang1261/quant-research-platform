package io.github.williamhuang1261.qrp.stats;

import io.github.williamhuang1261.qrp.core.PluginRegistry;
import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import java.util.List;

/**
 * Selects a compute engine from what is on the classpath, falling back to the
 * portable one.
 *
 * <p>The faster engine is optional by construction: it needs a toolchain and an
 * OpenMP runtime that a reviewer cloning the repository may not have. Missing it
 * has to be a non-event, so selection asks each discovered engine whether it is
 * {@link ComputeEngine#isAvailable() available} and takes the first that says
 * yes, with {@link JavaComputeEngine} as the guaranteed answer.
 */
public final class ComputeEngines {

    private ComputeEngines() {
    }

    /** Every engine on the classpath, in declaration order, then the portable one. */
    public static List<ComputeEngine> discovered() {
        List<ComputeEngine> found =
                PluginRegistry.load(ComputeEngine.class, ComputeEngine::id).all();
        if (found.stream().anyMatch(engine -> engine instanceof JavaComputeEngine)) {
            return found;
        }
        return java.util.stream.Stream.concat(found.stream(), java.util.stream.Stream.of(new JavaComputeEngine()))
                .toList();
    }

    /** The first available engine; never null, because the Java engine always is. */
    public static ComputeEngine best() {
        return discovered().stream()
                .filter(ComputeEngine::isAvailable)
                .findFirst()
                .orElseGet(JavaComputeEngine::new);
    }

    /** The portable engine, for tests that need to compare against it. */
    public static ComputeEngine portable() {
        return new JavaComputeEngine();
    }
}
