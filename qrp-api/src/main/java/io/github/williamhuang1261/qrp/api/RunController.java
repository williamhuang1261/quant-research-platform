package io.github.williamhuang1261.qrp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.williamhuang1261.qrp.app.BacktestRunner;
import io.github.williamhuang1261.qrp.app.CliArguments;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.MarketDataException;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;
import io.github.williamhuang1261.qrp.warehouse.BacktestRunFactRepository;
import io.github.williamhuang1261.qrp.warehouse.BacktestRunRecord;
import io.github.williamhuang1261.qrp.warehouse.InstrumentDimensionRepository;
import io.github.williamhuang1261.qrp.warehouse.StrategyDimensionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two endpoints: run a backtest, and fetch one back by id. The controller's
 * compute path still only translates {@link RunRequest} into the
 * {@code --flag value} list {@link CliArguments#parse} already accepts, then
 * hands the parsed record to the unmodified {@link BacktestRunner#run} --
 * every default, every validation rule and every error message for a fresh
 * run is still the CLI's own.
 *
 * <p>What is new is the read/write layer around that call: every outcome is
 * persisted to {@code fact_backtest_run} keyed by everything that determines
 * it (instrument, strategy, params, cash, cost model, execution model), so an
 * identical repeat request is served from Postgres instead of recomputed, and
 * a prior run can be fetched by id with no engine invocation at all.
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    RunController(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public RunResponse run(@RequestBody(required = false) RunRequest request) {
        RunRequest body = request == null ? RunRequest.empty() : request;
        CliArguments arguments = CliArguments.parse(toCliArgs(body));

        InstrumentDimensionRepository instruments = new InstrumentDimensionRepository(dataSource);
        StrategyDimensionRepository strategies = new StrategyDimensionRepository(dataSource);
        BacktestRunFactRepository runs = new BacktestRunFactRepository(dataSource);

        Instrument instrument = resolveInstrument(arguments);
        long instrumentId = instruments.findOrCreate(
                instrument.symbol(), instrument.currency(), instrument.assetClass().name());
        long strategyId = strategies.findOrCreate(arguments.strategyId());
        String paramsJson = CacheKeys.canonicalParamsJson(arguments, objectMapper);
        String costModel = CacheKeys.costModelName(arguments.costs());
        String executionModel = arguments.execution().id();

        Optional<BacktestRunRecord> cached = runs.findByKey(
                instrumentId, strategyId, paramsJson, arguments.initialCash(), costModel, executionModel);
        if (cached.isPresent()) {
            return RunResponse.from(cached.get(), arguments.strategyId(), true);
        }

        BacktestRunner.Outcome outcome = BacktestRunner.run(arguments);
        PerformanceMetrics metrics = outcome.result().metrics();
        BacktestRunRecord inserted = runs.insert(
                instrumentId, strategyId, paramsJson, arguments.initialCash(), costModel, executionModel,
                outcome.engineId(), metrics.initialEquity(), metrics.finalEquity(), metrics.totalReturn(),
                metrics.cagr(), metrics.annualisedVolatility(), metrics.sharpeRatio(), metrics.maxDrawdown(),
                metrics.tradeCount(), metrics.timeInMarket(), outcome.result().equityCurve().toArray());

        return RunResponse.from(outcome, inserted.id(), false);
    }

    @GetMapping("/{id}")
    public RunResponse getById(@PathVariable("id") long id) {
        BacktestRunFactRepository runs = new BacktestRunFactRepository(dataSource);
        StrategyDimensionRepository strategies = new StrategyDimensionRepository(dataSource);

        BacktestRunRecord record = runs.findById(id).orElseThrow(() -> new RunNotFoundException(id));
        String strategyId = strategies.findNameById(record.strategyId())
                .orElseThrow(() -> new RunNotFoundException(id));
        return RunResponse.from(record, strategyId, true);
    }

    /** Resolves the same {@link Instrument} {@link BacktestRunner#run} would, so the cache key names the instrument it actually ran against. */
    private static Instrument resolveInstrument(CliArguments arguments) {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(arguments.dataDirectory());
        return provider.available().stream()
                .filter(candidate -> candidate.symbol().equalsIgnoreCase(arguments.symbol()))
                .findFirst()
                .orElseThrow(() -> new MarketDataException(
                        "unknown symbol '" + arguments.symbol() + "'; available: "
                                + provider.available().stream().map(Instrument::symbol).toList()));
    }

    static List<String> toCliArgs(RunRequest request) {
        List<String> args = new ArrayList<>();
        addFlag(args, "--symbol", request.symbol());
        addFlag(args, "--timeframe", request.timeframe());
        addFlag(args, "--strategy", request.strategy());
        addFlag(args, "--cash", request.cash());
        addFlag(args, "--costs", request.costs());
        addFlag(args, "--paths", request.paths());
        addFlag(args, "--seed", request.seed());
        addFlag(args, "--execution", request.execution());
        addFlag(args, "--lob-spread", request.lobSpreadFraction());
        addFlag(args, "--lob-offset", request.lobOffsetLevels());
        addFlag(args, "--lob-levels", request.lobLevels());
        addFlag(args, "--lob-depth", request.lobDepthFraction());
        if (request.params() != null) {
            request.params().forEach((key, value) -> {
                args.add("--param");
                args.add(key + "=" + value);
            });
        }
        return args;
    }

    private static void addFlag(List<String> args, String flag, Object value) {
        if (value != null) {
            args.add(flag);
            args.add(String.valueOf(value));
        }
    }
}
