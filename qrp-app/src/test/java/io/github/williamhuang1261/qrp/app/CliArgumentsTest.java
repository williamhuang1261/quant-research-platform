package io.github.williamhuang1261.qrp.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.engine.CostModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CliArgumentsTest {

    @Test
    @DisplayName("runs with no flags at all, on the bundled data")
    void defaultsAreUsable() {
        CliArguments arguments = CliArguments.parse(List.of());

        assertEquals("SYNA", arguments.symbol());
        assertEquals(Timeframe.DAY_1, arguments.timeframe());
        assertEquals("sma-crossover", arguments.strategyId());
        assertEquals(100_000.0, arguments.initialCash(), 1e-12);
        assertEquals(CostModel.retail(), arguments.costs());
        assertEquals(20, arguments.params().requireInt("fast"));
        assertEquals(50, arguments.params().requireInt("slow"));
    }

    @Test
    @DisplayName("--param overrides a default and adds new keys")
    void parametersOverrideDefaults() {
        CliArguments arguments = CliArguments.parse(
                List.of("--param", "fast=5", "--param", "threshold=1.5"));

        assertEquals(5, arguments.params().requireInt("fast"));
        assertEquals(50, arguments.params().requireInt("slow"));
        assertEquals(1.5, arguments.params().require("threshold"), 1e-12);
    }

    @Test
    @DisplayName("a strategy other than the reference one gets no invented defaults")
    void unknownStrategyHasNoDefaults() {
        CliArguments arguments = CliArguments.parse(List.of("--strategy", "something-else"));

        assertTrue(arguments.params().asMap().isEmpty());
    }

    @Test
    @DisplayName("parses the remaining options")
    void parsesEveryOption() {
        CliArguments arguments = CliArguments.parse(List.of(
                "--data", "/tmp/data", "--symbol", "SYNB", "--timeframe", "1h",
                "--cash", "50000", "--costs", "none", "--paths", "500",
                "--block", "10", "--level", "0.9", "--seed", "7"));

        assertEquals("/tmp/data", arguments.dataDirectory().toString());
        assertEquals("SYNB", arguments.symbol());
        assertEquals(Timeframe.HOUR_1, arguments.timeframe());
        assertEquals(50_000.0, arguments.initialCash(), 1e-12);
        assertEquals(CostModel.none(), arguments.costs());
        assertEquals(500, arguments.monteCarloPaths());
        assertEquals(10, arguments.blockSize());
        assertEquals(0.9, arguments.confidenceLevel(), 1e-12);
        assertEquals(7L, arguments.seed());
    }

    @Test
    @DisplayName("rejects bad input with a message naming the flag")
    void rejectsBadInput() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(List.of("--nope"))).getMessage().contains("--nope"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(List.of("--cash"))).getMessage().contains("--cash"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(List.of("--cash", "lots"))).getMessage().contains("--cash"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(List.of("--param", "fast"))).getMessage().contains("key=value"));
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(List.of("--costs", "free")));
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(List.of("--timeframe", "3d")));
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(List.of("--paths", "-1")));
    }

    @Test
    @DisplayName("usage names every command")
    void usageIsComplete() {
        String usage = CliArguments.usage();

        assertTrue(usage.contains("qrp run"));
        assertTrue(usage.contains("qrp workbench"));
        assertTrue(usage.contains("qrp list"));
    }
}
