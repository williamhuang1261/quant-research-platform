package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.engine.CostModel;
import io.github.williamhuang1261.qrp.portfolio.PortfolioBacktestEngine.RebalanceFrequency;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed {@code portfolio} command arguments.
 *
 * <p>Kept separate from {@link CliArguments} and {@link CompareArguments} for
 * the same reason those two are separate from each other: multi-instrument
 * rebalancing (which optimizer, how often, how much history the covariance
 * estimate is built from) has nothing to do with a single-instrument
 * backtest's or a fund comparison's flags.
 */
public record PortfolioArguments(
        Path dataDirectory,
        List<String> symbols,
        OptimizerKind optimizer,
        RebalanceFrequency rebalance,
        int covarianceLookbackBars,
        CostModel costs,
        double initialCash,
        double maxWeight,
        double maxTurnover,
        double riskAversion,
        String signalIndicatorId,
        int signalPeriod,
        double signalSpread) {

    /** Which {@link io.github.williamhuang1261.qrp.portfolio.PortfolioOptimizer} allocates the run. */
    public enum OptimizerKind {
        MEAN_VARIANCE("mean-variance"),
        RISK_PARITY("risk-parity");

        private final String id;

        OptimizerKind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static OptimizerKind fromId(String raw) {
            return switch (raw) {
                case "mean-variance" -> MEAN_VARIANCE;
                case "risk-parity" -> RISK_PARITY;
                default -> throw new IllegalArgumentException(
                        "--optimizer expects mean-variance or risk-parity, got: " + raw);
            };
        }
    }

    private static final String DEFAULT_DATA = "data/sample";
    private static final List<String> DEFAULT_SYMBOLS = List.of("SYNA", "SYNB", "SYNETF");
    private static final OptimizerKind DEFAULT_OPTIMIZER = OptimizerKind.RISK_PARITY;
    private static final RebalanceFrequency DEFAULT_REBALANCE = RebalanceFrequency.MONTHLY;
    private static final int DEFAULT_LOOKBACK = 60;
    private static final double DEFAULT_MAX_WEIGHT = 0.5;
    private static final double DEFAULT_RISK_AVERSION = 5.0;
    private static final int DEFAULT_SIGNAL_PERIOD = 14;
    private static final double DEFAULT_SIGNAL_SPREAD = 0.02;

    public PortfolioArguments {
        if (symbols == null || symbols.size() < 2) {
            throw new IllegalArgumentException(
                    "at least two symbols are required for a portfolio, got: "
                            + (symbols == null ? 0 : symbols.size()));
        }
        symbols = List.copyOf(symbols);
    }

    public static String usageSuffix() {
        return """

                portfolio options
                  --data <dir>          market data directory      (default: data/sample)
                  --symbol <ticker>     instrument, repeatable, at least two (default: SYNA, SYNB, SYNETF)
                  --optimizer <id>      mean-variance|risk-parity   (default: risk-parity)
                  --rebalance <id>      monthly|weekly              (default: monthly)
                  --lookback <bars>     covariance trailing window, bars (default: 60)
                  --cash <amount>       starting capital            (default: 100000)
                  --costs none|retail   cost model                  (default: retail)
                  --max-weight <frac>   per-instrument cap, (0, 1]   (default: 0.5)
                  --turnover <cap>      max turnover per rebalance, or "none" (default: none)
                  --risk-aversion <n>   mean-variance lambda, ignored by risk-parity (default: 5.0)
                  --signal <id>         indicator id driving the view (e.g. rsi), default: flat 20-bar momentum
                  --signal-period <n>   the signal indicator's period parameter (default: 14)
                  --signal-spread <f>   forecast gap top vs. bottom rank, when --signal is set (default: 0.02)""";
    }

    /** @throws IllegalArgumentException with a usable message on any bad input */
    public static PortfolioArguments parse(List<String> arguments) {
        Path data = Path.of(DEFAULT_DATA);
        List<String> symbols = new ArrayList<>();
        OptimizerKind optimizer = DEFAULT_OPTIMIZER;
        RebalanceFrequency rebalance = DEFAULT_REBALANCE;
        int lookback = DEFAULT_LOOKBACK;
        CostModel costs = CostModel.retail();
        double cash = 100_000.0;
        double maxWeight = DEFAULT_MAX_WEIGHT;
        double maxTurnover = Double.MAX_VALUE;
        double riskAversion = DEFAULT_RISK_AVERSION;
        String signalIndicatorId = null;
        int signalPeriod = DEFAULT_SIGNAL_PERIOD;
        double signalSpread = DEFAULT_SIGNAL_SPREAD;

        for (int i = 0; i < arguments.size(); i++) {
            String flag = arguments.get(i);
            switch (flag) {
                case "--data" -> data = Path.of(value(arguments, ++i, flag));
                case "--symbol" -> symbols.add(value(arguments, ++i, flag));
                case "--optimizer" -> optimizer = OptimizerKind.fromId(value(arguments, ++i, flag));
                case "--rebalance" -> rebalance = rebalanceFrequency(value(arguments, ++i, flag));
                case "--lookback" -> lookback = (int) number(value(arguments, ++i, flag), flag);
                case "--cash" -> cash = number(value(arguments, ++i, flag), flag);
                case "--costs" -> costs = costModel(value(arguments, ++i, flag));
                case "--max-weight" -> maxWeight = number(value(arguments, ++i, flag), flag);
                case "--turnover" -> maxTurnover = turnover(value(arguments, ++i, flag), flag);
                case "--risk-aversion" -> riskAversion = number(value(arguments, ++i, flag), flag);
                case "--signal" -> signalIndicatorId = value(arguments, ++i, flag);
                case "--signal-period" -> signalPeriod = (int) number(value(arguments, ++i, flag), flag);
                case "--signal-spread" -> signalSpread = number(value(arguments, ++i, flag), flag);
                default -> {
                    // Same inline "--flag=value" accommodation CliArguments makes for
                    // --execution: --optimizer=risk-parity is the form this project's own
                    // plan writes, so it is accepted alongside the space-separated form
                    // every other flag uses.
                    if (flag.startsWith("--optimizer=")) {
                        optimizer = OptimizerKind.fromId(flag.substring("--optimizer=".length()));
                    } else if (flag.startsWith("--rebalance=")) {
                        rebalance = rebalanceFrequency(flag.substring("--rebalance=".length()));
                    } else if (flag.startsWith("--signal=")) {
                        signalIndicatorId = flag.substring("--signal=".length());
                    } else {
                        throw new IllegalArgumentException("unknown option: " + flag);
                    }
                }
            }
        }

        if (symbols.isEmpty()) {
            symbols.addAll(DEFAULT_SYMBOLS);
        }
        if (lookback < 2) {
            throw new IllegalArgumentException("--lookback must be at least 2, got: " + lookback);
        }
        if (signalPeriod < 1) {
            throw new IllegalArgumentException("--signal-period must be at least 1, got: " + signalPeriod);
        }
        if (!(signalSpread > 0.0)) {
            throw new IllegalArgumentException("--signal-spread must be positive, got: " + signalSpread);
        }

        return new PortfolioArguments(
                data, symbols, optimizer, rebalance, lookback, costs, cash, maxWeight, maxTurnover, riskAversion,
                signalIndicatorId, signalPeriod, signalSpread);
    }

    private static RebalanceFrequency rebalanceFrequency(String raw) {
        return switch (raw) {
            case "monthly" -> RebalanceFrequency.MONTHLY;
            case "weekly" -> RebalanceFrequency.WEEKLY;
            default -> throw new IllegalArgumentException(
                    "--rebalance expects monthly or weekly, got: " + raw);
        };
    }

    private static double turnover(String raw, String flag) {
        return raw.equals("none") ? Double.MAX_VALUE : number(raw, flag);
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
