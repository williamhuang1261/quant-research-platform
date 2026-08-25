package io.github.williamhuang1261.qrp.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives the CLI end to end against the bundled data: data loading, indicator
 * and strategy discovery, the engine, the resampling and the formatting, in one
 * pass through the real entry point.
 */
class QrpCliTest {

    private record Run(int status, String out, String err) {
    }

    private static Run invoke(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status;
        try (PrintStream outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            status = QrpCli.run(List.of(args), outStream, errStream);
        }
        return new Run(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("run reproduces the golden backtest through the whole stack")
    void runReproducesTheGoldenResult() {
        Run result = invoke("run", "--data", "../data/sample", "--symbol", "SYNA", "--paths", "0");

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("92,229.01"), result.out());
        assertTrue(result.out().contains("-7.77%"), result.out());
        assertTrue(result.out().contains("-4.12%"), result.out());
        assertTrue(result.out().contains("24.15%"), result.out());
        assertTrue(result.out().contains("sma-crossover on SYNA.USD 1d"), result.out());
    }

    @Test
    @DisplayName("the Monte Carlo section appears only when paths are requested")
    void monteCarloIsOptional() {
        Run without = invoke("run", "--data", "../data/sample", "--paths", "0");
        Run with = invoke("run", "--data", "../data/sample", "--paths", "500", "--seed", "1");

        assertTrue(!without.out().contains("Monte Carlo"), without.out());
        assertTrue(with.out().contains("Monte Carlo: 500 resampled paths"), with.out());
        assertTrue(with.out().contains("probability of loss"), with.out());
    }

    @Test
    @DisplayName("every report carries its caveats")
    void reportCarriesCaveats() {
        Run result = invoke("run", "--data", "../data/sample", "--paths", "0");

        assertTrue(result.out().contains("financing, borrow and taxes are not"), result.out());
    }

    @Test
    @DisplayName("list shows the plugins and instruments that are actually installed")
    void listShowsInstalledPlugins() {
        Run result = invoke("list", "--data", "../data/sample");

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("sma"), result.out());
        assertTrue(result.out().contains("sma-crossover"), result.out());
        assertTrue(result.out().contains("java"), result.out());
        assertTrue(result.out().contains("<- selected"), result.out());
        assertTrue(result.out().contains("SYNETF"), result.out());
    }

    @Test
    @DisplayName("an unknown symbol is an error with the available ones listed")
    void unknownSymbolIsExplained() {
        Run result = invoke("run", "--data", "../data/sample", "--symbol", "NOPE");

        assertEquals(3, result.status());
        assertTrue(result.err().contains("SYNA"), result.err());
    }

    @Test
    @DisplayName("bad usage exits non-zero and prints why")
    void badUsageIsRejected() {
        assertEquals(2, invoke("run", "--nope").status());
        assertEquals(2, invoke("frobnicate").status());
        assertEquals(1, invoke().status());
        assertEquals(0, invoke("--help").status());
    }
}
