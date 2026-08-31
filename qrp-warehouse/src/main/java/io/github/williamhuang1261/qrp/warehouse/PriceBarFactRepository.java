package io.github.williamhuang1261.qrp.warehouse;

import io.github.williamhuang1261.qrp.core.Bar;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Writes and range-queries {@code fact_price_bar}.
 *
 * <p>{@link #upsert} is idempotent under the same
 * {@code (instrument_id, timeframe, ts)} key the backfill loader relies on to
 * be safely re-run on every {@code qrp-api} startup. {@link #range} answers
 * exactly the query the table's unique constraint already indexes.
 */
public final class PriceBarFactRepository {

    private static final String UPSERT = """
            INSERT INTO fact_price_bar (instrument_id, timeframe, ts, open, high, low, close, volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, timeframe, ts) DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                volume = EXCLUDED.volume
            """;

    private static final String RANGE = """
            SELECT ts, open, high, low, close, volume
            FROM fact_price_bar
            WHERE instrument_id = ? AND timeframe = ? AND ts >= ? AND ts < ?
            ORDER BY ts
            """;

    private final DataSource dataSource;

    public PriceBarFactRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void upsert(long instrumentId, String timeframeId, Bar bar) {
        Objects.requireNonNull(timeframeId, "timeframeId");
        Objects.requireNonNull(bar, "bar");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setLong(1, instrumentId);
            statement.setString(2, timeframeId);
            statement.setTimestamp(3, Timestamp.from(bar.timestamp()));
            statement.setDouble(4, bar.open());
            statement.setDouble(5, bar.high());
            statement.setDouble(6, bar.low());
            statement.setDouble(7, bar.close());
            statement.setLong(8, bar.volume());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new WarehouseException("failed to upsert price bar at " + bar.timestamp(), e);
        }
    }

    /** Bars in {@code [fromInclusive, toExclusive)}, ordered by timestamp -- the range a caller-facing endpoint hands back directly. */
    public List<Bar> range(long instrumentId, String timeframeId, Instant fromInclusive, Instant toExclusive) {
        Objects.requireNonNull(timeframeId, "timeframeId");
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(toExclusive, "toExclusive");
        List<Bar> bars = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(RANGE)) {
            statement.setLong(1, instrumentId);
            statement.setString(2, timeframeId);
            statement.setTimestamp(3, Timestamp.from(fromInclusive));
            statement.setTimestamp(4, Timestamp.from(toExclusive));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    bars.add(new Bar(
                            resultSet.getTimestamp("ts").toInstant(),
                            resultSet.getDouble("open"),
                            resultSet.getDouble("high"),
                            resultSet.getDouble("low"),
                            resultSet.getDouble("close"),
                            resultSet.getLong("volume")));
                }
            }
        } catch (SQLException e) {
            throw new WarehouseException("failed to range-query price bars", e);
        }
        return bars;
    }
}
