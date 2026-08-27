package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.engine.CostModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed {@code compare} command arguments.
 *
 * <p>Kept separate from {@link CliArguments} and {@link OptionsArguments} for
 * the same reason those two are separate from each other: this command's flags
 * (which symbols play which role, what each side's management fee is) have
 * nothing to do with a single-instrument backtest's flags, and folding them
 * together would make every command's usage text longer for no reader's
 * benefit.
 */
public record CompareArguments(
        Path dataDirectory,
        List<String> candidateSymbols,
        String benchmarkSymbol,
        Timeframe timeframe,
        String strategyId,
        Params params,
        CostModel costs,
        double initialCash,
        double candidateFeeRate,
        double benchmarkFeeRate,
        NarrativeSource narrative) {

    /** Which {@link io.github.williamhuang1261.qrp.report.NarrativeGenerator} the report's closing paragraph uses. */
    public enum NarrativeSource {
        TEMPLATE,
        OLLAMA
    }

    private static final String DEFAULT_DATA = "data/sample";
    private static final List<String> DEFAULT_CANDIDATES = List.of("SYNA", "SYNB");
    private static final String DEFAULT_BENCHMARK = "SYNETF";
    private static final String DEFAULT_STRATEGY = "sma-crossover";
    private static final NarrativeSource DEFAULT_NARRATIVE = NarrativeSource.TEMPLATE;

    /**
     * A 2% MER stands in for an actively managed fund; {@code SYNETF} plays the
     * passive benchmark at 9 bps, in line with what a real index ETF actually
     * charges. Neither number claims to be anyone's real fee schedule -- see the
     * module README on {@link io.github.williamhuang1261.qrp.report.ManagementFeeModel}.
     */
    private static final double DEFAULT_CANDIDATE_FEE = 0.02;

    private static final double DEFAULT_BENCHMARK_FEE = 0.0009;

    public CompareArguments {
        if (candidateSymbols == null || candidateSymbols.isEmpty()) {
            throw new IllegalArgumentException("at least one candidate symbol is required");
        }
        candidateSymbols = List.copyOf(candidateSymbols);
    }

    public static String usageSuffix() {
        return """

                compare options
                  --data <dir>          market data directory      (default: data/sample)
                  --symbol <ticker>     candidate fund, repeatable  (default: SYNA, SYNB)
                  --benchmark <ticker>  passive benchmark           (default: SYNETF)
                  --timeframe <id>      1m 5m 15m 1h 1d 1w          (default: 1d)
                  --strategy <id>       strategy id                 (default: sma-crossover)
                  --param k=v           strategy parameter, repeatable
                  --cash <amount>       starting capital            (default: 100000)
                  --costs none|retail   cost model                  (default: retail)
                  --fee <rate>          candidate MER, as a fraction (default: 0.02)
                  --benchmark-fee <rate> benchmark MER, as a fraction (default: 0.0009)
                  --narrative <id>      template|ollama                (default: template)""";
    }

    /** @throws IllegalArgumentException with a usable message on any bad input */
    public static CompareArguments parse(List<String> arguments) {
        Path data = Path.of(DEFAULT_DATA);
        List<String> candidates = new ArrayList<>();
        String benchmark = DEFAULT_BENCHMARK;
        Timeframe timeframe = Timeframe.DAY_1;
        String strategy = DEFAULT_STRATEGY;
        List<String> rawParams = new ArrayList<>();
        CostModel costs = CostModel.retail();
        double cash = 100_000.0;
        double candidateFee = DEFAULT_CANDIDATE_FEE;
        double benchmarkFee = DEFAULT_BENCHMARK_FEE;
        NarrativeSource narrative = DEFAULT_NARRATIVE;

        for (int i = 0; i < arguments.size(); i++) {
            String flag = arguments.get(i);
            switch (flag) {
                case "--data" -> data = Path.of(value(arguments, ++i, flag));
                case "--symbol" -> candidates.add(value(arguments, ++i, flag));
                case "--benchmark" -> benchmark = value(arguments, ++i, flag);
                case "--timeframe" -> timeframe = Timeframe.fromId(value(arguments, ++i, flag));
                case "--strategy" -> strategy = value(arguments, ++i, flag);
                case "--param" -> rawParams.add(value(arguments, ++i, flag));
                case "--cash" -> cash = number(value(arguments, ++i, flag), flag);
                case "--costs" -> costs = costModel(value(arguments, ++i, flag));
                case "--fee" -> candidateFee = number(value(arguments, ++i, flag), flag);
                case "--benchmark-fee" -> benchmarkFee = number(value(arguments, ++i, flag), flag);
                case "--narrative" -> narrative = narrativeSource(value(arguments, ++i, flag));
                default -> throw new IllegalArgumentException("unknown option: " + flag);
            }
        }

        if (candidates.isEmpty()) {
            candidates.addAll(DEFAULT_CANDIDATES);
        }

        Params params = defaultsFor(strategy);
        for (String raw : rawParams) {
            int equals = raw.indexOf('=');
            if (equals <= 0 || equals == raw.length() - 1) {
                throw new IllegalArgumentException("--param expects key=value, got: " + raw);
            }
            params = params.with(raw.substring(0, equals).trim(),
                    number(raw.substring(equals + 1).trim(), "--param " + raw));
        }

        return new CompareArguments(
                data, candidates, benchmark, timeframe, strategy, params, costs, cash, candidateFee, benchmarkFee,
                narrative);
    }

    /**
     * The reference strategy's windows, so {@code qrp compare} works with no
     * flags -- same defaults as {@link CliArguments#parse}, since a candidate
     * run through {@code compare} should behave identically to the same symbol
     * run through {@code run}.
     */
    private static Params defaultsFor(String strategyId) {
        return strategyId.equals("sma-crossover")
                ? Params.of("fast", 20).with("slow", 50)
                : Params.empty();
    }

    private static String value(List<String> arguments, int index, String flag) {
        if (index >= arguments.size()) {
            throw new IllegalArgumentException(flag + " needs a value");
        }
        return arguments.get(index);
    }

    private static double number(String raw, String flag) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " expects a number, got: " + raw);
        }
    }

    private static CostModel costModel(String name) {
        return switch (name) {
            case "none" -> CostModel.none();
            case "retail" -> CostModel.retail();
            default -> throw new IllegalArgumentException(
                    "--costs expects none or retail, got: " + name);
        };
    }

    private static NarrativeSource narrativeSource(String name) {
        return switch (name) {
            case "template" -> NarrativeSource.TEMPLATE;
            case "ollama" -> NarrativeSource.OLLAMA;
            default -> throw new IllegalArgumentException(
                    "--narrative expects template or ollama, got: " + name);
        };
    }
}
