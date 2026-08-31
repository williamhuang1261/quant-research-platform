package io.github.williamhuang1261.qrp.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.williamhuang1261.qrp.app.CompareArguments;
import io.github.williamhuang1261.qrp.app.CompareRunner;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.MarketDataException;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.warehouse.InstrumentDimensionRepository;
import io.github.williamhuang1261.qrp.warehouse.ReportRunFactRepository;
import io.github.williamhuang1261.qrp.warehouse.ReportRunRecord;
import io.github.williamhuang1261.qrp.warehouse.StrategyDimensionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint: run the fund comparison report, return it as JSON. A second
 * caller of the platform's existing entry points, alongside {@code qrp
 * compare} -- this controller's compute path still only translates query
 * parameters into the exact {@code --flag value} list
 * {@link CompareArguments#parse} already accepts, then hands the parsed
 * record to the unmodified {@link CompareRunner#run}.
 *
 * <p>What is new is the read/write layer around that call: every report is
 * persisted to {@code fact_report_run} keyed by everything that determines
 * it, so an identical repeat request is served from Postgres, and the
 * narrative generator -- including an opt-in Ollama call -- is never
 * invoked a second time for the same request.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    ReportController(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/compare")
    public ReportResponse compare(
            @RequestParam(name = "symbol", required = false) List<String> symbol,
            @RequestParam(name = "benchmark", required = false) String benchmark,
            @RequestParam(name = "timeframe", required = false) String timeframe,
            @RequestParam(name = "strategy", required = false) String strategy,
            @RequestParam(name = "cash", required = false) Double cash,
            @RequestParam(name = "costs", required = false) String costs,
            @RequestParam(name = "fee", required = false) Double fee,
            @RequestParam(name = "benchmarkFee", required = false) Double benchmarkFee,
            @RequestParam(name = "narrative", required = false) String narrative) {
        List<String> args = new ArrayList<>();
        if (symbol != null) {
            symbol.forEach(value -> addFlag(args, "--symbol", value));
        }
        addFlag(args, "--benchmark", benchmark);
        addFlag(args, "--timeframe", timeframe);
        addFlag(args, "--strategy", strategy);
        addFlag(args, "--cash", cash);
        addFlag(args, "--costs", costs);
        addFlag(args, "--fee", fee);
        addFlag(args, "--benchmark-fee", benchmarkFee);
        addFlag(args, "--narrative", narrative);

        CompareArguments arguments = CompareArguments.parse(args);

        InstrumentDimensionRepository instruments = new InstrumentDimensionRepository(dataSource);
        StrategyDimensionRepository strategies = new StrategyDimensionRepository(dataSource);
        ReportRunFactRepository reports = new ReportRunFactRepository(dataSource);

        Instrument benchmarkInstrument = resolveInstrument(arguments);
        long benchmarkInstrumentId = instruments.findOrCreate(
                benchmarkInstrument.symbol(), benchmarkInstrument.currency(), benchmarkInstrument.assetClass().name());
        long strategyId = strategies.findOrCreate(arguments.strategyId());
        String candidateSymbolsCsv = String.join(",", arguments.candidateSymbols());
        String costModel = CacheKeys.costModelName(arguments.costs());
        String narrativeSource = arguments.narrative().name().toLowerCase(Locale.ROOT);
        String paramsJson = CacheKeys.canonicalReportParamsJson(arguments, objectMapper);
        String timeframeId = arguments.timeframe().id();

        Optional<ReportRunRecord> cached = reports.findByKey(
                benchmarkInstrumentId, strategyId, candidateSymbolsCsv, arguments.initialCash(), costModel,
                narrativeSource, paramsJson, timeframeId);
        if (cached.isPresent()) {
            return ReportResponse.fromCached(
                    cached.get(), arguments.strategyId(), arguments.candidateSymbols(), arguments.benchmarkSymbol(),
                    readRows(cached.get().tableJson()));
        }

        CompareRunner.Outcome outcome = CompareRunner.run(arguments);
        List<ReportRowResponse> rows = outcome.table().rows().stream().map(ReportRowResponse::from).toList();
        ReportRunRecord inserted = reports.insert(
                benchmarkInstrumentId, strategyId, candidateSymbolsCsv, arguments.initialCash(), costModel,
                narrativeSource, paramsJson, timeframeId, writeRows(rows), outcome.narrative());

        return ReportResponse.from(outcome, inserted.id(), false);
    }

    /** Resolves the benchmark the same way {@link CompareRunner#run} would, so the cache key names the instrument it actually ran against. */
    private static Instrument resolveInstrument(CompareArguments arguments) {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(arguments.dataDirectory());
        return provider.available().stream()
                .filter(candidate -> candidate.symbol().equalsIgnoreCase(arguments.benchmarkSymbol()))
                .findFirst()
                .orElseThrow(() -> new MarketDataException(
                        "unknown symbol '" + arguments.benchmarkSymbol() + "'; available: "
                                + provider.available().stream().map(Instrument::symbol).toList()));
    }

    private String writeRows(List<ReportRowResponse> rows) {
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize report rows to JSON", e);
        }
    }

    private List<ReportRowResponse> readRows(String tableJson) {
        try {
            return objectMapper.readValue(tableJson, new TypeReference<List<ReportRowResponse>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize persisted report rows", e);
        }
    }

    private static void addFlag(List<String> args, String flag, Object value) {
        if (value != null) {
            args.add(flag);
            args.add(String.valueOf(value));
        }
    }
}
