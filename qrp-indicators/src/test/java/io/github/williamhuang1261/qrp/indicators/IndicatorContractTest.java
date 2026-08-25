package io.github.williamhuang1261.qrp.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.PluginRegistry;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.core.spi.Indicator;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The contract every indicator must satisfy, applied to whatever the classpath
 * happens to provide. Nothing here names an implementation, so a private jar
 * dropped in beside this module is held to the same rules as the public ones.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndicatorContractTest {

    private static final String SERVICE_FILE =
            "META-INF/services/io.github.williamhuang1261.qrp.core.spi.Indicator";

    private final PluginRegistry<Indicator> registry =
            PluginRegistry.load(Indicator.class, Indicator::id);

    private final BarSeries series = CsvMarketDataProvider
            .ofDirectory(Path.of("..", "data", "sample"))
            .loadAll(Instrument.equity("SYNA"), Timeframe.DAY_1);

    private static Params defaultParams() {
        return Params.of(Periods.PERIOD, 20);
    }

    @Test
    @DisplayName("discovery finds every provider declared on the classpath, with no list in the code")
    void discoversEveryDeclaredProvider() throws IOException {
        List<String> declared = declaredProviders();

        assertEquals(declared.size(), registry.size(),
                "registry holds " + registry.ids() + " but the service file declares " + declared);
        for (String className : declared) {
            assertTrue(registry.all().stream().anyMatch(i -> i.getClass().getName().equals(className)),
                    className + " is declared but was not discovered");
        }
    }

    @Test
    @DisplayName("every indicator returns one value per bar")
    void resultsAreAligned() {
        for (Indicator indicator : registry.all()) {
            DoubleSeries values = indicator.compute(series, defaultParams());

            assertEquals(series.size(), values.size(), indicator.id() + " must align with the bars");
        }
    }

    @Test
    @DisplayName("every indicator leaves its declared warm-up undefined and produces values after it")
    void warmupIsRespected() {
        for (Indicator indicator : registry.all()) {
            Params params = defaultParams();
            int warmup = indicator.warmup(params);
            DoubleSeries values = indicator.compute(series, params);

            for (int i = 0; i < warmup; i++) {
                assertFalse(values.isDefined(i),
                        indicator.id() + " defined a value at " + i + " inside its warm-up of " + warmup);
            }
            assertTrue(values.isDefined(warmup),
                    indicator.id() + " has no value at the end of its warm-up (" + warmup + ")");
            assertEquals(warmup, values.firstDefinedIndex(), indicator.id() + " warm-up mismatch");
        }
    }

    @Test
    @DisplayName("every indicator is deterministic and stateless across calls")
    void isDeterministic() {
        for (Indicator indicator : registry.all()) {
            DoubleSeries first = indicator.compute(series, defaultParams());
            DoubleSeries second = indicator.compute(series, defaultParams());

            assertEquals(first, second, indicator.id() + " is not deterministic");
        }
    }

    @Test
    @DisplayName("every indicator declares a usable id and display name")
    void identifiersAreUsable() {
        for (Indicator indicator : registry.all()) {
            assertFalse(indicator.id().isBlank());
            assertFalse(indicator.displayName().isBlank());
            assertEquals(indicator.id().toLowerCase(), indicator.id(),
                    "ids are lowercase so configuration files stay case-stable");
        }
    }

    private List<String> declaredProviders() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(SERVICE_FILE)) {
            if (stream == null) {
                throw new UncheckedIOException(new IOException("missing " + SERVICE_FILE));
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        }
    }
}
