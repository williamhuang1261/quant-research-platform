package io.github.williamhuang1261.qrp.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.williamhuang1261.qrp.core.AssetClass;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Params;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.indicators.RelativeStrengthIndex;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A real, honest measurement: does a 14-period RSI, generated into a
 * cross-sectional forecast by {@link CrossSectionalSignalGenerator}, have any
 * actual predictive relationship with the repo's bundled synthetic series?
 *
 * <p>Like {@code BacktestIntegrationTest} in {@code qrp-engine} and
 * {@code PortfolioBacktestEngineTest} in {@code qrp-portfolio}, the numbers
 * here are a characterisation of the current implementation on the current
 * synthetic data, captured once from a real run and pinned as a tripwire —
 * not a claim of a proven-predictive signal. RSI on three geometric Brownian
 * series has no reason to carry real information, and the pinned result below
 * says exactly that: a small mean IC and a large p-value. Reporting a weak
 * result honestly is the point of {@link SignalSignificance} — the module
 * this test defends is a "test whether a signal is real" tool, not a
 * "produce a significant signal" tool, and it must be equally willing to say
 * a signal is not real.
 */
class CrossSectionalSignalGeneratorGoldenRunTest {

    private static final int RSI_PERIOD = 14;
    private static final int FORWARD_HORIZON_BARS = 5;
    private static final double TARGET_SPREAD = 0.02;

    private static List<BarSeries> series;

    @BeforeAll
    static void loadSyntheticSeries() {
        CsvMarketDataProvider provider = CsvMarketDataProvider.ofDirectory(Path.of("..", "data", "sample"));
        BarSeries syna = provider.loadAll(Instrument.equity("SYNA"), Timeframe.DAY_1);
        BarSeries synb = provider.loadAll(Instrument.equity("SYNB"), Timeframe.DAY_1);
        BarSeries synetf = provider.loadAll(new Instrument("SYNETF", "USD", AssetClass.ETF), Timeframe.DAY_1);
        series = List.of(syna, synb, synetf);
    }

    @Test
    @DisplayName("RSI's cross-sectional forecast on SYNA/SYNB/SYNETF reproduces its recorded IC and significance exactly")
    void goldenRun() {
        List<DoubleSeries> forecasts = CrossSectionalSignalGenerator.generate(
                series, new RelativeStrengthIndex(), Params.of("period", RSI_PERIOD), TARGET_SPREAD);

        int barCount = series.get(0).size();
        int n = series.size();
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
                double signalValue = forecasts.get(i).get(t);
                double forwardValue = ForwardReturns.forwardReturn(closes[i], t, FORWARD_HORIZON_BARS);
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
        SignalSignificance significance = SignalSignificance.of(icSeries);

        assertEquals(485, icSeries.length);
        assertEquals(0.027835051546391754, significance.meanIc(), 1e-9);
        assertEquals(0.03101985685242787, significance.standardError(), 1e-9);
        assertEquals(0.8973301095105845, significance.zStatistic(), 1e-9);
        assertEquals(0.36954279439231374, significance.pValue(), 1e-9);
        org.junit.jupiter.api.Assertions.assertFalse(significance.isSignificant(0.05),
                "RSI on synthetic geometric Brownian series should not carry a real signal; z="
                        + significance.zStatistic() + " p=" + significance.pValue());
    }
}
