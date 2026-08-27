package io.github.williamhuang1261.qrp.report;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.DoubleSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.engine.BacktestResult;
import io.github.williamhuang1261.qrp.engine.PerformanceMetrics;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * No test here talks to a real Ollama server -- the endpoint is always an
 * unreachable local port, so these run offline and prove the fallback path
 * rather than the (untestable-without-network) happy path.
 */
class OllamaNarrativeGeneratorTest {

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

    private static BacktestResult resultFor(BarSeries series, double[] equity) {
        double grossCagr = handCagr(equity, series);
        PerformanceMetrics metrics = new PerformanceMetrics(
                equity[0], equity[equity.length - 1], equity[equity.length - 1] / equity[0] - 1.0,
                grossCagr, 1.0, 0.5, 0.05, 3, 1.0);
        return new BacktestResult(series, DoubleSeries.of(equity), DoubleSeries.of(equity), List.of(), metrics);
    }

    private static FundComparisonTable simpleTable() {
        BarSeries series = series("FUND", 30);
        BacktestResult candidateResult = resultFor(series, linearEquity(30, 100_000.0, 0.05));
        FundProfile candidate = new FundProfile("Fund A", series.instrument(), ManagementFeeModel.none());
        BacktestResult benchResult = resultFor(series, linearEquity(30, 100_000.0, 0.03));
        FundProfile benchmark = new FundProfile("Benchmark", series.instrument(), ManagementFeeModel.none());
        return FundComparisonTable.of(List.of(candidate), List.of(candidateResult), benchmark, benchResult);
    }

    @Test
    void anUnreachableEndpointFallsBackToTheTemplateInsteadOfThrowing() {
        // Port 1 is a reserved low port nothing listens on; the connection is
        // refused essentially immediately rather than hanging.
        OllamaNarrativeGenerator generator = new OllamaNarrativeGenerator(
                URI.create("http://localhost:1"), "llama3.2", Duration.ofSeconds(2));
        FundComparisonTable table = simpleTable();

        String narrative = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> generator.narrate(table));

        assertTrue(narrative.startsWith(TemplateNarrativeGenerator.LABEL_PREFIX),
                "expected a template-labelled fallback, got: " + narrative);
        assertTrue(narrative.contains("Fund A"), "fallback narrative should still name the leader: " + narrative);
    }

    @Test
    void defaultEndpointDoesNotThrowEitherWhenNoLocalServerIsRunning() {
        // Exercises the zero-arg constructor (the default localhost:11434 endpoint)
        // without assuming a server is or is not actually running there.
        OllamaNarrativeGenerator generator = new OllamaNarrativeGenerator();
        FundComparisonTable table = simpleTable();

        String narrative = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> generator.narrate(table));

        assertTrue(narrative.startsWith(OllamaNarrativeGenerator.LABEL_PREFIX)
                        || narrative.startsWith(TemplateNarrativeGenerator.LABEL_PREFIX),
                "narrative must be labelled one way or the other: " + narrative);
    }
}
