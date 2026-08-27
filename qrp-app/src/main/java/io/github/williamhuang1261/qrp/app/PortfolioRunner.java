package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.MarketDataException;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.PluginRegistry;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.core.spi.Indicator;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.engine.ExecutionModel;
import io.github.williamhuang1261.qrp.engine.MarketOpenExecutionModel;
import io.github.williamhuang1261.qrp.portfolio.EqualRiskContributionOptimizer;
import io.github.williamhuang1261.qrp.portfolio.MeanVarianceOptimizer;
import io.github.williamhuang1261.qrp.portfolio.PortfolioBacktestEngine;
import io.github.williamhuang1261.qrp.portfolio.PortfolioBacktestResult;
import io.github.williamhuang1261.qrp.portfolio.PortfolioConstraints;
import io.github.williamhuang1261.qrp.portfolio.PortfolioOptimizer;
import io.github.williamhuang1261.qrp.signals.CrossSectionalSignalGenerator;
import io.github.williamhuang1261.qrp.signals.ForwardReturns;
import io.github.williamhuang1261.qrp.signals.InformationCoefficient;
import io.github.williamhuang1261.qrp.signals.SignalSignificance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs a multi-instrument portfolio backtest through {@link
 * PortfolioBacktestEngine}, wiring the CLI's {@link PortfolioArguments} to the
 * module's own types.
 *
 * <p>The per-instrument "view" the optimizer allocates against is, by
 * default, a plain trailing N-bar momentum computed here, the same
 * construction {@code PortfolioBacktestEngineTest}'s golden run uses -- the
 * simplest thing an {@code Indicator} could hand the optimizer, not a claim
 * that momentum is a good signal. When {@code --signal} names a real
 * indicator id instead, the view comes from {@link
 * CrossSectionalSignalGenerator} and the run also reports that signal's
 * information coefficient and significance ({@link #scoreSignal}), so a
 * reviewer sees whether the forecast driving the allocation is statistically
 * real before trusting the backtest it produced. {@link
 * EqualRiskContributionOptimizer} (risk parity) ignores the view entirely
 * either way, since it allocates by risk contribution rather than expected
 * return; only {@link MeanVarianceOptimizer} uses it.
 */
public final class PortfolioRunner {

    private static final int MOMENTUM_LOOKBACK_BARS = 20;

    /** The horizon a generated signal's forward-looking accuracy is scored against, in bars. */
    private static final int SIGNAL_FORWARD_HORIZON_BARS = 5;

    /** One completed portfolio run: the result plus what produced it, for the formatter. */
    public record Outcome(
            PortfolioBacktestResult result,
            String optimizerId,
            PortfolioArguments.OptimizerKind optimizerKind,
            PortfolioBacktestEngine.RebalanceFrequency rebalance,
            double initialCash,
            Optional<SignalReport> signalReport) {
    }

    /** How well a {@code --signal}-generated view actually predicted what happened next. */
    public record SignalReport(String indicatorId, int periods, SignalSignificance significance) {
    }

    private PortfolioRunner() {
    }

    public static Outcome run(PortfolioArguments arguments) {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(arguments.dataDirectory());

        List<BarSeries> series = new ArrayList<>();
        for (String symbol : arguments.symbols()) {
            Instrument instrument = resolve(provider, symbol);
            series.add(provider.loadAll(instrument, Timeframe.DAY_1));
        }

        List<DoubleSeries> views;
        Optional<SignalReport> signalReport;
        if (arguments.signalIndicatorId() == null) {
            views = series.stream().map(PortfolioRunner::momentum).toList();
            signalReport = Optional.empty();
        } else {
            Indicator indicator = PluginRegistry.load(Indicator.class, Indicator::id)
                    .require(arguments.signalIndicatorId());
            Params params = Params.of("period", arguments.signalPeriod());
            views = CrossSectionalSignalGenerator.generate(series, indicator, params, arguments.signalSpread());
            signalReport = Optional.of(scoreSignal(arguments.signalIndicatorId(), series, views));
        }

        PortfolioOptimizer optimizer = optimizer(arguments);
        PortfolioConstraints constraints = PortfolioConstraints.longOnly(
                arguments.maxWeight(), arguments.maxTurnover());
        ExecutionModel execution = new MarketOpenExecutionModel(arguments.costs());

        PortfolioBacktestResult result = PortfolioBacktestEngine.run(
                series, views, arguments.rebalance(), arguments.covarianceLookbackBars(),
                optimizer, constraints, execution, arguments.initialCash());

        return new Outcome(result, optimizer.id(), arguments.optimizer(), arguments.rebalance(),
                arguments.initialCash(), signalReport);
    }

    /**
     * Scores a generated signal's own information coefficient against what
     * actually happened {@link #SIGNAL_FORWARD_HORIZON_BARS} bars later,
     * across every bar where every instrument's forecast and forward return
     * are both defined -- the same measurement {@code
     * CrossSectionalSignalGeneratorGoldenRunTest} pins for RSI on the bundled
     * synthetic series.
     */
    private static SignalReport scoreSignal(String indicatorId, List<BarSeries> series, List<DoubleSeries> views) {
        int n = series.size();
        int barCount = series.get(0).size();
        double[][] closes = new double[n][];
        for (int i = 0; i < n; i++) {
            closes[i] = series.get(i).closes();
        }

        List<double[]> signalPeriods = new ArrayList<>();
        List<double[]> forwardReturnPeriods = new ArrayList<>();
        for (int t = 0; t < barCount; t++) {
            double[] signalCross = new double[n];
            double[] forwardCross = new double[n];
            boolean usable = true;
            for (int i = 0; i < n; i++) {
                double signalValue = views.get(i).get(t);
                double forwardValue = ForwardReturns.forwardReturn(closes[i], t, SIGNAL_FORWARD_HORIZON_BARS);
                if (Double.isNaN(signalValue) || Double.isNaN(forwardValue)) {
                    usable = false;
                    break;
                }
                signalCross[i] = signalValue;
                forwardCross[i] = forwardValue;
            }
            if (usable) {
                signalPeriods.add(signalCross);
                forwardReturnPeriods.add(forwardCross);
            }
        }

        double[][] signals = signalPeriods.toArray(new double[0][]);
        double[][] forwardReturns = forwardReturnPeriods.toArray(new double[0][]);
        double[] icSeries = InformationCoefficient.perPeriod(signals, forwardReturns);
        return new SignalReport(indicatorId, icSeries.length, SignalSignificance.of(icSeries));
    }

    private static PortfolioOptimizer optimizer(PortfolioArguments arguments) {
        return switch (arguments.optimizer()) {
            case MEAN_VARIANCE -> new MeanVarianceOptimizer(arguments.riskAversion());
            case RISK_PARITY -> new EqualRiskContributionOptimizer();
        };
    }

    /** A trailing N-bar return: the simplest thing an {@code Indicator} could hand the optimizer as a "view". */
    private static DoubleSeries momentum(BarSeries s) {
        double[] closes = s.closes();
        double[] values = new double[closes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = i < MOMENTUM_LOOKBACK_BARS
                    ? Double.NaN
                    : closes[i] / closes[i - MOMENTUM_LOOKBACK_BARS] - 1.0;
        }
        return DoubleSeries.of(values);
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
