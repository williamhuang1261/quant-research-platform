package io.github.williamhuang1261.qrp.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.Bar;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class PriceBarFactRepositoryTest {

    private final DataSource dataSource = WarehouseDataSourceFactory.create();
    private final InstrumentDimensionRepository instruments = new InstrumentDimensionRepository(dataSource);
    private final PriceBarFactRepository prices = new PriceBarFactRepository(dataSource);

    @Test
    void rangeQueryReturnsExactlyTheBarsInsideTheHalfOpenInterval() {
        long instrumentId = instruments.findOrCreate(TestSymbols.unique("TS-"), "USD", "EQUITY");
        Instant day1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant day2 = Instant.parse("2026-01-02T00:00:00Z");
        Instant day3 = Instant.parse("2026-01-03T00:00:00Z");
        prices.upsert(instrumentId, "1d", bar(day1));
        prices.upsert(instrumentId, "1d", bar(day2));
        prices.upsert(instrumentId, "1d", bar(day3));

        List<Bar> result = prices.range(instrumentId, "1d", day1, day3);

        assertEquals(2, result.size(), "day3 sits at the exclusive upper bound and must not be included");
        assertEquals(day1, result.get(0).timestamp());
        assertEquals(day2, result.get(1).timestamp());
    }

    @Test
    void upsertIsIdempotentUnderTheSameKey() {
        long instrumentId = instruments.findOrCreate(TestSymbols.unique("TS-"), "USD", "EQUITY");
        Instant ts = Instant.parse("2026-02-01T00:00:00Z");
        prices.upsert(instrumentId, "1d", new Bar(ts, 10.0, 11.0, 9.0, 10.5, 1000));
        prices.upsert(instrumentId, "1d", new Bar(ts, 10.0, 12.0, 9.0, 11.5, 2000));

        List<Bar> result = prices.range(instrumentId, "1d", ts, ts.plusSeconds(1));

        assertEquals(1, result.size(), "a second upsert at the same key must replace, not duplicate, the row");
        assertTrue(result.get(0).close() == 11.5, "the second write's values must win");
    }

    private static Bar bar(Instant timestamp) {
        return new Bar(timestamp, 100.0, 101.0, 99.0, 100.5, 500);
    }
}
