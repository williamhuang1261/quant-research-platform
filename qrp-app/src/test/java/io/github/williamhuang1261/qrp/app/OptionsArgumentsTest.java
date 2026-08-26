package io.github.williamhuang1261.qrp.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OptionsArgumentsTest {

    @Test
    @DisplayName("defaults match the bundled sample chain")
    void defaultsMatchTheBundledChain() {
        OptionsArguments arguments = OptionsArguments.parse(List.of());

        assertEquals(Path.of("data/sample"), arguments.dataDirectory());
        assertEquals("SYNOPT", arguments.underlying());
        assertEquals(LocalDate.of(2026, 1, 2), arguments.valuationDate());
        assertNull(arguments.exportCsv());
    }

    @Test
    @DisplayName("every flag overrides its default")
    void everyFlagOverridesItsDefault() {
        OptionsArguments arguments = OptionsArguments.parse(List.of(
                "--data", "/tmp/chains",
                "--underlying", "OTHER",
                "--date", "2027-06-15",
                "--export", "/tmp/grid.csv"));

        assertEquals(Path.of("/tmp/chains"), arguments.dataDirectory());
        assertEquals("OTHER", arguments.underlying());
        assertEquals(LocalDate.of(2027, 6, 15), arguments.valuationDate());
        assertEquals(Path.of("/tmp/grid.csv"), arguments.exportCsv());
    }

    @Test
    @DisplayName("rejects an unparsable date and an unknown flag")
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> OptionsArguments.parse(List.of("--date", "not-a-date")));
        assertThrows(IllegalArgumentException.class, () -> OptionsArguments.parse(List.of("--nope")));
        assertThrows(IllegalArgumentException.class, () -> OptionsArguments.parse(List.of("--underlying")));
    }
}
