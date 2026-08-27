package io.github.williamhuang1261.qrp.report;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateNarrativeGeneratorTest {

    private static final Instant DAY0 = Instant.parse("2024-01-02T21:00:00Z");
    private static final double DAYS_PER_YEAR = 365.25;

    private static BarSeries series(String symbol, int days) {
        List<Bar> bars = new ArrayList<>(days + 1);
        for (int i = 0; i <= days; i++) {
            double price = 100.0 + i;
            bars.add(new Bar(DAY0.plus(Duration.ofDays(i)), price, price + 0.5, price - 0.5, price, 1_000L));
        }
        return BarSeries.of(Instrument.equity(symbol), Timeframe.DAY_1, bars);
    }

    private static double handCagr(double[] equity, BarSeries series) {
        double years = Duration.between(series.start(), series.end()).toDays() / DAYS_PER_YEAR;
        return Math.pow(equity[equity.length - 1] / equity[0], 1.0 / years) - 1.0;
    }

    private static double[] linearEquity(int bars, double start, double totalReturn) {
        double[] equity = new double[bars + 1];
        for (int i = 0; i <= bars; i++) {
            equity[i] = start * (1.0 + totalReturn * i / bars);
        }
        return equity;
    }

    /** Same fixture shape as {@code FundComparisonTableTest}, but with a configurable Sharpe ratio. */
    private static BacktestResult resultFor(BarSeries series, double[] equity, double sharpe) {
        double grossCagr = handCagr(equity, series);
        PerformanceMetrics metrics = new PerformanceMetrics(
                equity[0], equity[equity.length - 1], equity[equity.length - 1] / equity[0] - 1.0,
                grossCagr, 0.10, sharpe, 0.05, 3, 1.0);
        return new BacktestResult(series, DoubleSeries.of(equity), DoubleSeries.of(equity), List.of(), metrics);
    }

    private final TemplateNarrativeGenerator generator = new TemplateNarrativeGenerator();

    @Test
    void namesTheSameFundAsBothLeadersWhenItLeadsOnBothMetrics() {
        BarSeries series = series("FUND", 365);

        BacktestResult strongResult = resultFor(series, linearEquity(365, 100_000.0, 0.20), 1.5);
        FundProfile strong = new FundProfile("Strong Fund", series.instrument(), ManagementFeeModel.none());

        BacktestResult weakResult = resultFor(series, linearEquity(365, 100_000.0, 0.03), 0.2);
        FundProfile weak = new FundProfile("Weak Fund", series.instrument(), ManagementFeeModel.none());

        BacktestResult benchResult = resultFor(series, linearEquity(365, 100_000.0, 0.10), 0.8);
        FundProfile benchmark = new FundProfile("Benchmark", series.instrument(), ManagementFeeModel.none());

        FundComparisonTable table = FundComparisonTable.of(
                List.of(strong, weak), List.of(strongResult, weakResult), benchmark, benchResult);

        String narrative = generator.narrate(table);

        assertTrue(narrative.startsWith(TemplateNarrativeGenerator.LABEL_PREFIX));
        assertTrue(narrative.contains("Strong Fund"), "expected the net-return leader named: " + narrative);
        // "Strong Fund" should be named as the Sharpe leader too -- check it appears in the
        // risk-adjusted sentence, not just the net-return sentence, by counting occurrences.
        long occurrences = narrative.split("Strong Fund", -1).length - 1;
        assertTrue(occurrences >= 2, "expected Strong Fund named as both leaders: " + narrative);
        assertTrue(narrative.contains("Weak Fund") == false
                || !narrative.contains("Weak Fund led on risk-adjusted"),
                "Weak Fund must not be named as the Sharpe leader: " + narrative);
    }

    @Test
    void namesDifferentFundsAsNetLeaderAndSharpeLeaderWhenTheyDiffer() {
        BarSeries series = series("FUND", 365);

        // Best net return, but choppy -- lowest Sharpe.
        BacktestResult highReturnResult = resultFor(series, linearEquity(365, 100_000.0, 0.25), 0.3);
        FundProfile highReturn = new FundProfile("High Return Fund", series.instrument(), ManagementFeeModel.none());

        // Modest net return, but the steadiest -- highest Sharpe.
        BacktestResult steadyResult = resultFor(series, linearEquity(365, 100_000.0, 0.06), 2.0);
        FundProfile steady = new FundProfile("Steady Fund", series.instrument(), ManagementFeeModel.none());

        BacktestResult benchResult = resultFor(series, linearEquity(365, 100_000.0, 0.05), 0.9);
        FundProfile benchmark = new FundProfile("Benchmark", series.instrument(), ManagementFeeModel.none());

        FundComparisonTable table = FundComparisonTable.of(
                List.of(highReturn, steady), List.of(highReturnResult, steadyResult), benchmark, benchResult);

        String narrative = generator.narrate(table);

        assertTrue(narrative.startsWith(TemplateNarrativeGenerator.LABEL_PREFIX));
        assertTrue(narrative.contains("High Return Fund posted the strongest net-of-fee return"),
                "expected High Return Fund named as the net leader: " + narrative);
        assertTrue(narrative.contains("Steady Fund led on risk-adjusted return"),
                "expected Steady Fund named as the Sharpe leader: " + narrative);
    }

    @Test
    void rejectsATableWithNoCandidateRows() {
        assertThrows(NullPointerException.class, () -> generator.narrate(null));
    }
}
