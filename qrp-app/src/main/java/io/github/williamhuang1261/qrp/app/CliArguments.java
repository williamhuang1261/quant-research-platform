package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.engine.CostModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed {@code run} arguments.
 *
 * <p>Hand-rolled rather than pulled from a CLI library. The grammar is a dozen
 * flags, and a dependency here would be one more thing to justify in a project
 * whose point is that its dependencies are few and open.
 */
public record CliArguments(
        Path dataDirectory,
        String symbol,
        Timeframe timeframe,
        String strategyId,
        Params params,
        CostModel costs,
        double initialCash,
        int monteCarloPaths,
        int blockSize,
        double confidenceLevel,
        long seed,
        ExecutionKind execution,
        double lobSpreadFraction,
        double lobOffsetLevels,
        int lobLevels,
        double lobDepthFraction) {

    private static final String DEFAULT_DATA = "data/sample";
    private static final int DEFAULT_PATHS = 2_000;
    private static final int DEFAULT_BLOCK = 20;
    private static final double DEFAULT_LEVEL = 0.95;
    private static final long DEFAULT_SEED = 20260825L;

    /**
     * Defaults for {@link io.github.williamhuang1261.qrp.engine.LimitOrderBookExecutionModel},
     * mirrored here rather than referenced across the module boundary so the
     * CLI's usage text and the record's fields stay one obvious source of truth
     * for what {@code --execution=lob} does with no other flags.
     */
    private static final double DEFAULT_LOB_SPREAD = 0.5;
    private static final double DEFAULT_LOB_OFFSET = 1.0;
    private static final int DEFAULT_LOB_LEVELS = 5;
    private static final double DEFAULT_LOB_DEPTH = 0.1;

    /** Which {@code ExecutionModel} a run fills against. */
    public enum ExecutionKind {
        MARKET_OPEN("market-open"),
        LOB("lob");

        private final String id;

        ExecutionKind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static ExecutionKind fromId(String raw) {
            return switch (raw) {
                case "market-open" -> MARKET_OPEN;
                case "lob" -> LOB;
                default -> throw new IllegalArgumentException(
                        "--execution expects market-open or lob, got: " + raw);
            };
        }
    }

    public static String usage() {
        return """
                usage: qrp run [options]
                       qrp workbench [options]
                       qrp list
                       qrp options [options]
                       qrp compare [options]
                       qrp portfolio [options]

                run options
                  --data <dir>          market data directory      (default: data/sample)
                  --symbol <ticker>     instrument to test          (default: SYNA)
                  --timeframe <id>      1m 5m 15m 1h 1d 1w          (default: 1d)
                  --strategy <id>       strategy id                 (default: sma-crossover)
                  --param k=v           strategy parameter, repeatable
                  --cash <amount>       starting capital            (default: 100000)
                  --costs none|retail   cost model                  (default: retail)
                  --paths <n>           Monte Carlo paths, 0 to skip (default: 2000)
                  --block <n>           bootstrap block size        (default: 20)
                  --level <0..1>        confidence level            (default: 0.95)
                  --seed <n>            resampling seed             (default: 20260825)
                  --execution <id>      market-open|lob             (default: market-open)
                  --lob-spread <frac>   lob book spread as a fraction of bar range (default: 0.5)
                  --lob-offset <levels> lob limit offset, in half-spreads     (default: 1.0)
                  --lob-levels <n>      lob synthetic price levels per side   (default: 5)
                  --lob-depth <frac>    lob visible depth as a fraction of bar volume (default: 0.1)

                workbench takes the same options as run and opens the JavaFX
                window; --snapshot <file> renders it to a PNG instead.

                list prints the indicators, strategies, compute engines and
                instruments visible on the classpath and in the data directory.

                options prices a chain, fits its volatility surface and runs the
                no-arbitrage diagnostics.

                compare runs the same strategy over several funds and a passive
                benchmark and prints a one-page, fee-adjusted comparison table.

                portfolio rebalances several instruments through a
                PortfolioOptimizer (--optimizer=mean-variance|risk-parity) on a
                schedule (--rebalance=monthly|weekly) and prints target weights,
                realized risk contribution and turnover.""";
    }

    /** @throws IllegalArgumentException with a usable message on any bad input */
    public static CliArguments parse(List<String> arguments) {
        Path data = Path.of(DEFAULT_DATA);
        String symbol = "SYNA";
        Timeframe timeframe = Timeframe.DAY_1;
        String strategy = "sma-crossover";
        List<String> rawParams = new ArrayList<>();
        CostModel costs = CostModel.retail();
        double cash = 100_000.0;
        int paths = DEFAULT_PATHS;
        int block = DEFAULT_BLOCK;
        double level = DEFAULT_LEVEL;
        long seed = DEFAULT_SEED;
        ExecutionKind execution = ExecutionKind.MARKET_OPEN;
        double lobSpread = DEFAULT_LOB_SPREAD;
        double lobOffset = DEFAULT_LOB_OFFSET;
        int lobLevels = DEFAULT_LOB_LEVELS;
        double lobDepth = DEFAULT_LOB_DEPTH;

        for (int i = 0; i < arguments.size(); i++) {
            String flag = arguments.get(i);
            switch (flag) {
                case "--data" -> data = Path.of(value(arguments, ++i, flag));
                case "--symbol" -> symbol = value(arguments, ++i, flag);
                case "--timeframe" -> timeframe = Timeframe.fromId(value(arguments, ++i, flag));
                case "--strategy" -> strategy = value(arguments, ++i, flag);
                case "--param" -> rawParams.add(value(arguments, ++i, flag));
                case "--cash" -> cash = number(value(arguments, ++i, flag), flag);
                case "--costs" -> costs = costModel(value(arguments, ++i, flag));
                case "--paths" -> paths = (int) number(value(arguments, ++i, flag), flag);
                case "--block" -> block = (int) number(value(arguments, ++i, flag), flag);
                case "--level" -> level = number(value(arguments, ++i, flag), flag);
                case "--seed" -> seed = (long) number(value(arguments, ++i, flag), flag);
                case "--execution" -> execution = ExecutionKind.fromId(value(arguments, ++i, flag));
                case "--lob-spread" -> lobSpread = number(value(arguments, ++i, flag), flag);
                case "--lob-offset" -> lobOffset = number(value(arguments, ++i, flag), flag);
                case "--lob-levels" -> lobLevels = (int) number(value(arguments, ++i, flag), flag);
                case "--lob-depth" -> lobDepth = number(value(arguments, ++i, flag), flag);
                default -> {
                    // "--execution=lob" is accepted alongside the space-separated
                    // "--execution lob" that every other flag uses: it is the form
                    // most CLI docs (and this project's own plan) write inline, and
                    // rejecting it here would be a needless surprise for that one flag.
                    if (flag.startsWith("--execution=")) {
                        execution = ExecutionKind.fromId(flag.substring("--execution=".length()));
                    } else {
                        throw new IllegalArgumentException("unknown option: " + flag);
                    }
                }
            }
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

        if (paths < 0) {
            throw new IllegalArgumentException("--paths must not be negative, got: " + paths);
        }
        return new CliArguments(data, symbol, timeframe, strategy, params, costs, cash,
                paths, block, level, seed, execution, lobSpread, lobOffset, lobLevels, lobDepth);
    }

    /**
     * The reference strategy's windows, so {@code qrp run} works with no flags.
     * Any other strategy states its own parameters, since nothing here can guess
     * what a plugin expects.
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
}
