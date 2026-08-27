package io.github.williamhuang1261.qrp.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.AssetClass;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.engine.CostModel;
import io.github.williamhuang1261.qrp.engine.ExecutionModel;
import io.github.williamhuang1261.qrp.engine.MarketOpenExecutionModel;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End to end: the repo's bundled synthetic series, composed through the
 * unmodified single-instrument {@link io.github.williamhuang1261.qrp.engine.BacktestEngine}
 * per instrument.
 *
 * <p>Like {@code BacktestIntegrationTest} in {@code qrp-engine}, the golden-run
 * numbers here are a characterisation of the current implementation, captured
 * once from a real run and pinned as a tripwire — not a claim that these are
 * "correct" numbers in any absolute sense.
 */
class PortfolioBacktestEngineTest {

    private static final double CURRENCY_TOLERANCE = 1e-6;
    private static final double RATIO_TOLERANCE = 1e-9;
    private static final int MOMENTUM_LOOKBACK = 20;
    private static final int COVARIANCE_LOOKBACK = 60;

    private static List<BarSeries> series;
    private static List<DoubleSeries> momentumViews;

    @BeforeAll
    static void loadSyntheticSeries() {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(Path.of("..", "data", "sample"));
        BarSeries syna = provider.loadAll(Instrument.equity("SYNA"), Timeframe.DAY_1);
        BarSeries synb = provider.loadAll(Instrument.equity("SYNB"), Timeframe.DAY_1);
        BarSeries synetf = provider.loadAll(new Instrument("SYNETF", "USD", AssetClass.ETF), Timeframe.DAY_1);
        series = List.of(syna, synb, synetf);
        momentumViews = series.stream().map(PortfolioBacktestEngineTest::momentum).toList();
    }

    /** A trailing N-bar return: the simplest thing an {@code Indicator} could hand the optimizer as a "view". */
    private static DoubleSeries momentum(BarSeries s) {
        double[] closes = s.closes();
        double[] values = new double[closes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = i < MOMENTUM_LOOKBACK
                    ? Double.NaN
                    : closes[i] / closes[i - MOMENTUM_LOOKBACK] - 1.0;
        }
        return DoubleSeries.of(values);
    }

    private static PortfolioBacktestResult runRiskParity() {
        ExecutionModel execution = new MarketOpenExecutionModel(CostModel.retail());
        PortfolioConstraints constraints = PortfolioConstraints.longOnly(0.5, Double.MAX_VALUE);
        return PortfolioBacktestEngine.run(
                series,
                momentumViews,
                PortfolioBacktestEngine.RebalanceFrequency.MONTHLY,
                COVARIANCE_LOOKBACK,
                new EqualRiskContributionOptimizer(),
                constraints,
                execution,
                100_000.0);
    }

    @Test
    @DisplayName("risk parity on SYNA/SYNB/SYNETF, monthly rebalance, reproduces its recorded result exactly")
    void goldenRun() {
        PortfolioBacktestResult result = runRiskParity();

        assertEquals(504, result.equityCurve().size());
        assertEquals(List.of("SYNA", "SYNB", "SYNETF"), result.instruments());

        // Pinned from a real run of this exact configuration; see class javadoc.
        assertEquals(74_144.6669622472, result.finalEquity(), CURRENCY_TOLERANCE);
        assertEquals(1.6774971376, result.totalTurnover(), RATIO_TOLERANCE);

        double[] averageWeights = result.averageWeights();
        assertEquals(0.3014250836, averageWeights[0], RATIO_TOLERANCE);
        assertEquals(0.2015439923, averageWeights[1], RATIO_TOLERANCE);
        assertEquals(0.4970309241, averageWeights[2], RATIO_TOLERANCE);

        // 504 bars, 60-bar covariance lookback, 21-bar monthly rebalance: floor((504-60)/21) + 1.
        assertEquals(22, result.rebalances().size());
    }

    @Test
    @DisplayName("every mark-to-market equity value stays positive across the whole run")
    void equityCurveStaysPositive() {
        PortfolioBacktestResult result = runRiskParity();
        for (int i = 0; i < result.equityCurve().size(); i++) {
            assertTrue(result.equityCurve().get(i) > 0.0, "equity went non-positive at bar " + i);
        }
    }

    @Test
    @DisplayName("risk parity balances realized volatility contribution more evenly than an equal-weight benchmark")
    void riskParityIsMoreBalancedThanEqualWeight() {
        ExecutionModel execution = new MarketOpenExecutionModel(CostModel.retail());
        PortfolioConstraints constraints = PortfolioConstraints.longOnly(0.5, Double.MAX_VALUE);

        PortfolioBacktestResult riskParity = PortfolioBacktestEngine.run(
                series, momentumViews, PortfolioBacktestEngine.RebalanceFrequency.MONTHLY, COVARIANCE_LOOKBACK,
                new EqualRiskContributionOptimizer(), constraints, execution, 100_000.0);

        PortfolioBacktestResult equalWeight = PortfolioBacktestEngine.run(
                series, momentumViews, PortfolioBacktestEngine.RebalanceFrequency.MONTHLY, COVARIANCE_LOOKBACK,
                new EqualWeightOptimizer(), constraints, execution, 100_000.0);

        double riskParityDispersion = coefficientOfVariation(riskParity.averageRiskContribution());
        double equalWeightDispersion = coefficientOfVariation(equalWeight.averageRiskContribution());

        // Pinned from a real run: risk parity's coefficient of variation across instruments'
        // average realized risk contribution is ~0.082, versus ~0.694 for equal weight — about
        // 8x more balanced.
        assertEquals(0.0818923507, riskParityDispersion, 1e-9);
        assertEquals(0.6937821334, equalWeightDispersion, 1e-9);
        assertTrue(riskParityDispersion < equalWeightDispersion,
                "risk parity's risk-contribution dispersion (" + riskParityDispersion
                        + ") should be lower than equal weight's (" + equalWeightDispersion + ")");
    }

    /** Standard deviation over mean of an array: a scale-free measure of how balanced it is. */
    private static double coefficientOfVariation(double[] values) {
        double mean = 0.0;
        for (double v : values) {
            mean += v;
        }
        mean /= values.length;

        double sumSquares = 0.0;
        for (double v : values) {
            double deviation = v - mean;
            sumSquares += deviation * deviation;
        }
        double stdDev = Math.sqrt(sumSquares / values.length);
        return stdDev / mean;
    }

    @Test
    @DisplayName("rejects instruments with mismatched bar counts")
    void rejectsMismatchedSeries() {
        BarSeries shortOne = series.get(0).slice(0, 100);
        List<BarSeries> mismatched = List.of(shortOne, series.get(1), series.get(2));

        assertThrows(IllegalArgumentException.class, () -> PortfolioBacktestEngine.run(
                mismatched, momentumViews, PortfolioBacktestEngine.RebalanceFrequency.MONTHLY, COVARIANCE_LOOKBACK,
                new EqualRiskContributionOptimizer(), PortfolioConstraints.longOnly(0.5, Double.MAX_VALUE),
                new MarketOpenExecutionModel(CostModel.retail()), 100_000.0));
    }

    /** A trivial fixed-weight optimizer used only to benchmark risk parity's balance against. */
    private static final class EqualWeightOptimizer implements PortfolioOptimizer {
        @Override
        public String id() {
            return "equal-weight";
        }

        @Override
        public double[] optimize(
                double[] expectedReturns,
                double[][] covariance,
                double[] previousWeights,
                PortfolioConstraints constraints) {
            int n = expectedReturns.length;
            double[] weights = new double[n];
            double each = constraints.leverage() / n;
            java.util.Arrays.fill(weights, each);
            return weights;
        }
    }
}
