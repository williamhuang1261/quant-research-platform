package io.github.williamhuang1261.qrp.warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Finds or creates a row in {@code dim_instrument}, keyed by symbol.
 *
 * <p>Implemented as a single upsert ({@code INSERT ... ON CONFLICT ... DO
 * UPDATE ... RETURNING id}) rather than a select-then-insert: one round trip,
 * and no race between two callers upserting the same symbol concurrently.
 */
public final class InstrumentDimensionRepository {

    private static final String UPSERT = """
            INSERT INTO dim_instrument (symbol, currency, asset_class)
            VALUES (?, ?, ?)
            ON CONFLICT (symbol) DO UPDATE SET
                currency = EXCLUDED.currency,
                asset_class = EXCLUDED.asset_class
            RETURNING id
            """;

    private final DataSource dataSource;

    public InstrumentDimensionRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public long findOrCreate(String symbol, String currency, String assetClass) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(assetClass, "assetClass");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, symbol);
            statement.setString(2, currency);
            statement.setString(3, assetClass);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("id");
            }
        } catch (SQLException e) {
            throw new WarehouseException("failed to find-or-create instrument '" + symbol + "'", e);
        }
    }
}
