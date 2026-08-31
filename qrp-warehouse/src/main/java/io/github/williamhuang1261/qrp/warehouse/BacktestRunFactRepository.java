package io.github.williamhuang1261.qrp.warehouse;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Reads and writes {@code fact_backtest_run}: the table
 * {@code RunController} checks before recomputing a backtest, and writes
 * through to after one runs. {@link #findByKey} is the cache lookup;
 * {@link #insert} is the write-through; {@link #findById} backs
 * {@code GET /api/runs/{id}}.
 */
public final class BacktestRunFactRepository {

    private static final String SELECT_COLUMNS =
            "id, instrument_id, strategy_id, params_json, cash, cost_model, execution_model, engine_id, "
            + "initial_equity, final_equity, total_return, cagr, annualised_volatility, sharpe, max_drawdown, "
            + "trades, time_in_market, equity_curve, created_at";

    private static final String FIND_BY_KEY = """
            SELECT %s FROM fact_backtest_run
            WHERE instrument_id = ? AND strategy_id = ? AND params_json = ?
                AND cash = ? AND cost_model = ? AND execution_model = ?
            """.formatted(SELECT_COLUMNS);

    private static final String FIND_BY_ID = """
            SELECT %s FROM fact_backtest_run WHERE id = ?
            """.formatted(SELECT_COLUMNS);

    private static final String INSERT = """
            INSERT INTO fact_backtest_run (
                instrument_id, strategy_id, params_json, cash, cost_model, execution_model, engine_id,
                initial_equity, final_equity, total_return, cagr, annualised_volatility, sharpe, max_drawdown,
                trades, time_in_market, equity_curve
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public BacktestRunFactRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public Optional<BacktestRunRecord> findByKey(
            long instrumentId, long strategyId, String paramsJson, double cash, String costModel, String executionModel) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_KEY)) {
            statement.setLong(1, instrumentId);
            statement.setLong(2, strategyId);
            statement.setString(3, paramsJson);
            statement.setDouble(4, cash);
            statement.setString(5, costModel);
            statement.setString(6, executionModel);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new WarehouseException("failed to look up backtest run by cache key", e);
        }
    }

    public Optional<BacktestRunRecord> findById(long id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new WarehouseException("failed to look up backtest run " + id, e);
        }
    }

    public BacktestRunRecord insert(
            long instrumentId, long strategyId, String paramsJson, double cash, String costModel,
            String executionModel, String engineId, double initialEquity, double finalEquity,
            double totalReturn, double cagr, double annualisedVolatility, double sharpe, double maxDrawdown,
            int trades, double timeInMarket, double[] equityCurve) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, instrumentId);
            statement.setLong(2, strategyId);
            statement.setString(3, paramsJson);
            statement.setDouble(4, cash);
            statement.setString(5, costModel);
            statement.setString(6, executionModel);
            statement.setString(7, engineId);
            statement.setDouble(8, initialEquity);
            statement.setDouble(9, finalEquity);
            statement.setDouble(10, totalReturn);
            statement.setDouble(11, cagr);
            statement.setDouble(12, annualisedVolatility);
            statement.setDouble(13, sharpe);
            statement.setDouble(14, maxDrawdown);
            statement.setInt(15, trades);
            statement.setDouble(16, timeInMarket);
            statement.setArray(17, toSqlArray(connection, equityCurve));
            statement.executeUpdate();
            long id;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
            return findById(id).orElseThrow(() -> new WarehouseException("insert did not persist row " + id, null));
        } catch (SQLException e) {
            throw new WarehouseException("failed to insert backtest run", e);
        }
    }

    private static Array toSqlArray(Connection connection, double[] values) throws SQLException {
        Double[] boxed = new Double[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return connection.createArrayOf("float8", boxed);
    }

    private static BacktestRunRecord map(ResultSet resultSet) throws SQLException {
        return new BacktestRunRecord(
                resultSet.getLong("id"),
                resultSet.getLong("instrument_id"),
                resultSet.getLong("strategy_id"),
                resultSet.getString("params_json"),
                resultSet.getDouble("cash"),
                resultSet.getString("cost_model"),
                resultSet.getString("execution_model"),
                resultSet.getString("engine_id"),
                resultSet.getDouble("initial_equity"),
                resultSet.getDouble("final_equity"),
                resultSet.getDouble("total_return"),
                resultSet.getDouble("cagr"),
                resultSet.getDouble("annualised_volatility"),
                resultSet.getDouble("sharpe"),
                resultSet.getDouble("max_drawdown"),
                resultSet.getInt("trades"),
                resultSet.getDouble("time_in_market"),
                toDoubleArray(resultSet.getArray("equity_curve")),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private static double[] toDoubleArray(Array sqlArray) throws SQLException {
        Object[] boxed = (Object[]) sqlArray.getArray();
        double[] values = new double[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            values[i] = ((Number) boxed[i]).doubleValue();
        }
        return values;
    }
}
