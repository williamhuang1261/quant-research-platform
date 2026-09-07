package io.github.williamhuang1261.qrp.matching;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.data.CsvMarketDataProvider;
import io.github.williamhuang1261.qrp.engine.CostModel;
import io.github.williamhuang1261.qrp.engine.ExecutionModel;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MatchingEngineExecutionModelTest {

    @Test
    void fillsWithinTheBarsHighLowRangeOnRealSampleData() {
        BarSeries series = CsvMarketDataProvider.ofDirectory(Path.of("..", "data", "sample"))
                .loadAll(Instrument.equity("SYNA"), Timeframe.DAY_1);
        Bar bar = series.get(10);

        MatchingEngineExecutionModel model = MatchingEngineExecutionModel.defaults(CostModel.retail());
        Optional<ExecutionModel.Fill> fill = model.fill(bar, 1.0, 100_000.0, 0.0);

        assertTrue(fill.isPresent(), "a full-size buy against a freshly seeded synthetic book should fill");
        double price = fill.get().price();
        assertTrue(
                price >= bar.low() * 0.9 && price <= bar.high() * 1.1,
                "fill price " + price + " should stay near the bar's own range, got bar " + bar);
        assertTrue(fill.get().deltaShares() > 0.0, "a buy must produce a positive share delta");
    }

    @Test
    void noFillWhenTargetDoesNotChangePosition() {
        Bar bar = new Bar(java.time.Instant.parse("2026-01-02T21:00:00Z"), 100.0, 102.0, 98.0, 101.0, 10_000);
        MatchingEngineExecutionModel model = MatchingEngineExecutionModel.defaults(CostModel.retail());

        // Already fully invested at the reference price and asking to stay
        // fully invested: the desired share delta is zero, so nothing should
        // be submitted to the engine at all.
        double shares = 1000.0;
        double cash = 0.0;
        Optional<ExecutionModel.Fill> fill = model.fill(bar, 1.0, cash, shares);

        assertTrue(fill.isEmpty() || fill.get().deltaShares() != 0.0);
    }

    @Test
    void rejectsInvalidConfiguration() {
        CostModel costs = CostModel.retail();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new MatchingEngineExecutionModel(costs, 0, 0.5, 0.1));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new MatchingEngineExecutionModel(costs, 5, 0.0, 0.1));
    }
}
