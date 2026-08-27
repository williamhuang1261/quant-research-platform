package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.MarketDataException;
import io.github.williamhuang1261.qrp.core.PluginRegistry;
import io.github.williamhuang1261.qrp.core.spi.Strategy;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.engine.BacktestEngine;
import io.github.williamhuang1261.qrp.engine.BacktestRequest;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.MarketOpenExecutionModel;
import io.github.williamhuang1261.qrp.report.FundComparisonTable;
import io.github.williamhuang1261.qrp.report.FundProfile;
import io.github.williamhuang1261.qrp.report.ManagementFeeModel;
import io.github.williamhuang1261.qrp.report.NarrativeGenerator;
import io.github.williamhuang1261.qrp.report.OllamaNarrativeGenerator;
import io.github.williamhuang1261.qrp.report.TemplateNarrativeGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the same strategy over every fund in a comparison and hands the results
 * to {@link FundComparisonTable}.
 *
 * <p>Every candidate and the benchmark are backtested identically -- same
 * strategy, same params, same {@link MarketOpenExecutionModel} and cost model
 * -- so the only thing that differs row to row is the instrument and its fee.
 * Comparing execution models is Extension 2's concern, not this command's, so
 * {@code compare} always fills at the next open, matching the {@code run}
 * command's own default.
 */
public final class CompareRunner {

    /** One completed comparison: the table plus what produced it, for the formatter. */
    public record Outcome(
            FundComparisonTable table,
            String strategyId,
            List<String> candidateSymbols,
            String benchmarkSymbol,
            String narrative) {
    }

    private CompareRunner() {
    }

    public static Outcome run(CompareArguments arguments) {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(arguments.dataDirectory());
        Strategy strategy = PluginRegistry.load(Strategy.class, Strategy::id).require(arguments.strategyId());

        List<FundProfile> candidateProfiles = new ArrayList<>();
        List<BacktestResult> candidateResults = new ArrayList<>();
        for (String symbol : arguments.candidateSymbols()) {
            BacktestResult result = runOne(provider, strategy, arguments, symbol);
            candidateProfiles.add(profile(provider, symbol, new ManagementFeeModel(arguments.candidateFeeRate())));
            candidateResults.add(result);
        }

        BacktestResult benchmarkResult = runOne(provider, strategy, arguments, arguments.benchmarkSymbol());
        FundProfile benchmarkProfile = profile(
                provider, arguments.benchmarkSymbol(), new ManagementFeeModel(arguments.benchmarkFeeRate()));

        FundComparisonTable table = FundComparisonTable.of(
                candidateProfiles, candidateResults, benchmarkProfile, benchmarkResult);

        NarrativeGenerator narrativeGenerator = narrativeGenerator(arguments.narrative());
        String narrative = narrativeGenerator.narrate(table);

        return new Outcome(table, strategy.id(), arguments.candidateSymbols(), arguments.benchmarkSymbol(), narrative);
    }

    private static NarrativeGenerator narrativeGenerator(CompareArguments.NarrativeSource source) {
        return switch (source) {
            case TEMPLATE -> new TemplateNarrativeGenerator();
            case OLLAMA -> new OllamaNarrativeGenerator();
        };
    }

    private static BacktestResult runOne(
            CsvMarketDataProvider provider, Strategy strategy, CompareArguments arguments, String symbol) {
        Instrument instrument = resolve(provider, symbol);
        BarSeries series = provider.loadAll(instrument, arguments.timeframe());
        return BacktestEngine.run(new BacktestRequest(
                series, strategy, arguments.params(),
                new MarketOpenExecutionModel(arguments.costs()), arguments.initialCash()));
    }

    private static FundProfile profile(CsvMarketDataProvider provider, String symbol, ManagementFeeModel fee) {
        Instrument instrument = resolve(provider, symbol);
        return new FundProfile(instrument.symbol(), instrument, fee);
    }

    private static Instrument resolve(CsvMarketDataProvider provider, String symbol) {
        return provider.available().stream()
                .filter(candidate -> candidate.symbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElseThrow(() -> new MarketDataException(
                        "unknown symbol '" + symbol + "'; available: "
                                + provider.available().stream().map(Instrument::symbol).toList()));
    }
}
