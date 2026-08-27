package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.MarketDataException;
import io.github.williamhuang1261.qrp.core.PluginRegistry;
import io.github.williamhuang1261.qrp.core.spi.ComputeEngine;
import io.github.williamhuang1261.qrp.core.spi.Strategy;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.engine.BacktestEngine;
import io.github.williamhuang1261.qrp.engine.BacktestRequest;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.ExecutionModel;
import io.github.williamhuang1261.qrp.engine.LimitOrderBookExecutionModel;
import io.github.williamhuang1261.qrp.engine.MarketOpenExecutionModel;
import io.github.williamhuang1261.qrp.stats.ComputeEngines;
import io.github.williamhuang1261.qrp.stats.EquityCurve;
import io.github.williamhuang1261.qrp.stats.MonteCarloSimulation;
import java.util.Optional;

/**
 * Assembles a run from the pieces on the classpath: data, a strategy, the
 * engine, and the resampling that puts an interval around the result.
 *
 * <p>Shared by both front ends. The CLI and the workbench differ in how they
 * present a run, not in how they perform one, and duplicating this would let the
 * two drift into reporting different numbers for the same configuration.
 */
public final class BacktestRunner {

    /** One completed run, plus what produced it. */
    public record Outcome(
            BacktestResult result,
            String strategyId,
            String engineId,
            String executionId,
            Optional<MonteCarloSimulation.Report> monteCarlo) {
    }

    private BacktestRunner() {
    }

    public static Outcome run(CliArguments arguments) {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(arguments.dataDirectory());

        Instrument instrument = provider.available().stream()
                .filter(candidate -> candidate.symbol().equalsIgnoreCase(arguments.symbol()))
                .findFirst()
                .orElseThrow(() -> new MarketDataException(
                        "unknown symbol '" + arguments.symbol() + "'; available: "
                                + provider.available().stream().map(Instrument::symbol).toList()));

        BarSeries series = provider.loadAll(instrument, arguments.timeframe());

        Strategy strategy = PluginRegistry.load(Strategy.class, Strategy::id)
                .require(arguments.strategyId());

        ExecutionModel execution = executionModel(arguments);
        BacktestResult result = BacktestEngine.run(new BacktestRequest(
                series, strategy, arguments.params(), execution, arguments.initialCash()));

        ComputeEngine engine = ComputeEngines.best();
        Optional<MonteCarloSimulation.Report> monteCarlo = arguments.monteCarloPaths() > 0
                ? Optional.of(simulate(result, arguments))
                : Optional.empty();

        return new Outcome(result, strategy.id(), engine.id(), arguments.execution().id(), monteCarlo);
    }

    /**
     * Builds the requested {@link ExecutionModel}. Kept as a switch rather than
     * a lookup table: two implementations exist, and a table would be more
     * ceremony than the thing it replaces.
     */
    private static ExecutionModel executionModel(CliArguments arguments) {
        return switch (arguments.execution()) {
            case MARKET_OPEN -> new MarketOpenExecutionModel(arguments.costs());
            case LOB -> new LimitOrderBookExecutionModel(
                    arguments.costs(),
                    arguments.lobSpreadFraction(),
                    arguments.lobOffsetLevels(),
                    arguments.lobLevels(),
                    arguments.lobDepthFraction());
        };
    }

    private static MonteCarloSimulation.Report simulate(BacktestResult result, CliArguments arguments) {
        double[] returns = EquityCurve.toReturns(result.equityCurve().toArray());
        return new MonteCarloSimulation().run(
                returns,
                result.metrics().initialEquity(),
                arguments.monteCarloPaths(),
                Math.min(arguments.blockSize(), returns.length),
                arguments.confidenceLevel(),
                arguments.seed());
    }
}
