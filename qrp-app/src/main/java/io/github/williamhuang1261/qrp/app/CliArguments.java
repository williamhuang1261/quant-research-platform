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
        long seed) {

    private static final String DEFAULT_DATA = "data/sample";
    private static final int DEFAULT_PATHS = 2_000;
    private static final int DEFAULT_BLOCK = 20;
    private static final double DEFAULT_LEVEL = 0.95;
    private static final long DEFAULT_SEED = 20260825L;

    public static String usage() {
        return """
                usage: qrp run [options]
                       qrp workbench [options]
                       qrp list

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

                workbench takes the same options as run and opens the JavaFX
                window; --snapshot <file> renders it to a PNG instead.

                list prints the indicators, strategies, compute engines and
                instruments visible on the classpath and in the data directory.""";
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
                default -> throw new IllegalArgumentException("unknown option: " + flag);
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
                paths, block, level, seed);
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
