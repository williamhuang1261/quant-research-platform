package io.github.williamhuang1261.qrp.warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** Finds or creates a row in {@code dim_strategy}, keyed by name. Same upsert shape as {@link InstrumentDimensionRepository}. */
public final class StrategyDimensionRepository {

    private static final String UPSERT = """
            INSERT INTO dim_strategy (name)
            VALUES (?)
            ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
            RETURNING id
            """;

    private static final String FIND_NAME_BY_ID = "SELECT name FROM dim_strategy WHERE id = ?";

    private final DataSource dataSource;

    public StrategyDimensionRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public long findOrCreate(String name) {
        Objects.requireNonNull(name, "name");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("id");
            }
        } catch (SQLException e) {
            throw new WarehouseException("failed to find-or-create strategy '" + name + "'", e);
        }
    }

    /** Reverses {@link #findOrCreate}: the name a persisted fact row's {@code strategy_id} points at. */
    public Optional<String> findNameById(long id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_NAME_BY_ID)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getString("name")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new WarehouseException("failed to look up strategy name for id " + id, e);
        }
    }
}
