package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.PluginRegistry;
import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import io.github.williamhuang1261.qrp.core.spi.Indicator;
import io.github.williamhuang1261.qrp.core.spi.Strategy;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.stats.ComputeEngines;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * The command line front end.
 *
 * <p>{@code run} performs a backtest and prints its report; {@code list} prints
 * what is installed. The second command exists because the platform's behaviour
 * depends on what is on the classpath, and "which indicators do I have" should
 * not require reading a jar.
 */
public final class QrpCli {

    private QrpCli() {
    }

    public static void main(String[] args) {
        System.exit(run(Arrays.asList(args), System.out, System.err));
    }

    /** Testable entry point: no exits, no static output streams. */
    public static int run(List<String> args, PrintStream out, PrintStream err) {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            out.println(CliArguments.usage());
            return args.isEmpty() ? 1 : 0;
        }

        String command = args.get(0);
        List<String> rest = args.subList(1, args.size());
        try {
            return switch (command) {
                case "run" -> runBacktest(rest, out);
                case "list" -> listInstalled(rest, out);
                case "workbench" -> openWorkbench(rest);
                default -> {
                    err.println("unknown command: " + command);
                    err.println(CliArguments.usage());
                    yield 2;
                }
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            err.println("error: " + e.getMessage());
            return 2;
        } catch (RuntimeException e) {
            err.println("error: " + e);
            return 3;
        }
    }

    /**
     * Opens the JavaFX workbench in this JVM.
     *
     * <p>Launching from here rather than through {@code javafx:run} is
     * deliberate: that plugin forks a JVM which puts the implementation jars on
     * the module path, where their ServiceLoader providers are not visible, and
     * the workbench then quietly runs on the slower compute engine.
     */
    private static int openWorkbench(List<String> args) {
        javafx.application.Application.launch(
                io.github.williamhuang1261.qrp.app.workbench.Workbench.class,
                args.toArray(String[]::new));
        return 0;
    }

    private static int runBacktest(List<String> args, PrintStream out) {
        BacktestRunner.Outcome outcome = BacktestRunner.run(CliArguments.parse(args));
        out.print(ReportFormatter.format(
                outcome.result(), outcome.strategyId(), outcome.engineId(), outcome.monteCarlo()));
        return 0;
    }

    private static int listInstalled(List<String> args, PrintStream out) {
        CliArguments arguments = CliArguments.parse(args);

        out.println("indicators");
        PluginRegistry.load(Indicator.class, Indicator::id).all()
                .forEach(indicator -> out.printf("  %-16s %s%n", indicator.id(), indicator.displayName()));

        out.println("strategies");
        PluginRegistry.load(Strategy.class, Strategy::id).all()
                .forEach(strategy -> out.printf("  %-16s %s%n", strategy.id(), strategy.displayName()));

        out.println("compute engines");
        for (ComputeEngine engine : ComputeEngines.discovered()) {
            String selected = engine.id().equals(ComputeEngines.best().id()) ? "  <- selected" : "";
            String state = engine.unavailableReason()
                    .map(reason -> "unavailable: " + reason)
                    .orElse("available");
            out.printf("  %-16s %s%s%n", engine.id(), state, selected);
        }

        out.println("instruments in " + arguments.dataDirectory());
        Path directory = arguments.dataDirectory();
        if (!Files.isDirectory(directory)) {
            out.println("  (no such directory)");
            return 0;
        }
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(directory);
        for (Instrument instrument : provider.available()) {
            out.printf("  %-16s %s %s%n", instrument.symbol(), instrument.assetClass(),
                    provider.timeframesFor(instrument).stream()
                            .map(io.github.williamhuang1261.qrp.core.Timeframe::id).toList());
        }
        return 0;
    }
}
