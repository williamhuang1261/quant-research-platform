package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsvOptionChainProviderTest {

    @Test
    @DisplayName("the bundled sample chain loads and matches the generator's output")
    void loadsTheBundledSample() {
        CsvOptionChainProvider provider = CsvOptionChainProvider.fromDirectory(Path.of("..", "data", "sample"));

        assertEquals(List.of("SYNOPT"), provider.availableUnderlyings());

        List<OptionChainQuote> chain = provider.chain("SYNOPT", LocalDate.of(2026, 1, 2));
        List<OptionChainQuote> generated = SyntheticChainGenerator.generate(LocalDate.of(2026, 1, 2));

        assertEquals(generated.size(), chain.size());
        // Prices round-trip through text with 6 decimal places; compare at that precision.
        for (int i = 0; i < generated.size(); i++) {
            assertEquals(generated.get(i).marketPrice(), chain.get(i).marketPrice(), 1e-5,
                    "mismatch at quote " + i);
            assertEquals(generated.get(i).contract().strike(), chain.get(i).contract().strike(), 1e-9);
        }
    }

    @Test
    @DisplayName("is discoverable through ServiceLoader like every other provider in the platform")
    void isDiscoverableThroughServiceLoader() {
        var found = java.util.ServiceLoader.load(io.github.williamhuang1261.qrp.options.spi.OptionChainProvider.class)
                .stream()
                .map(java.util.ServiceLoader.Provider::type)
                .toList();

        assertTrue(found.contains(CsvOptionChainProvider.class),
                "CsvOptionChainProvider not found via ServiceLoader; found: " + found);
    }

    @Test
    @DisplayName("an unknown underlying is refused with the available list in the message")
    void refusesUnknownUnderlying() {
        CsvOptionChainProvider provider = CsvOptionChainProvider.fromDirectory(Path.of("..", "data", "sample"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> provider.chain("NOTREAL", LocalDate.of(2026, 1, 2)));
        assertTrue(exception.getMessage().contains("SYNOPT"));
    }

    @Test
    @DisplayName("refuses a directory with no chain files")
    void refusesAnEmptyDirectory(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> CsvOptionChainProvider.fromDirectory(tempDir));
    }

    @Test
    @DisplayName("refuses a path that is not a directory")
    void refusesANonDirectory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CsvOptionChainProvider.fromDirectory(Path.of("..", "data", "sample", "SYNOPT_chain.csv")));
    }
}
