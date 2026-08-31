package io.github.williamhuang1261.qrp.warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Reads and writes {@code fact_report_run}: the same cache-key shape as
 * {@link BacktestRunFactRepository}, applied to
 * {@code GET /api/reports/compare}. A cache hit here also means the
 * narrative generator (including an opt-in Ollama call) is never invoked a
 * second time for the same request. {@code paramsJson} carries everything
 * beyond the fixed columns that determines the report's numbers -- the
 * strategy's own params plus both fee rates -- the same reasoning
 * {@link BacktestRunFactRepository} already applies to LOB's tuning knobs.
 */
public final class ReportRunFactRepository {

    private static final String SELECT_COLUMNS =
            "id, benchmark_instrument_id, strategy_id, candidate_symbols_csv, cash, cost_model, "
            + "narrative_source, params_json, timeframe, table_json, narrative, created_at";

    private static final String FIND_BY_KEY = """
            SELECT %s FROM fact_report_run
            WHERE benchmark_instrument_id = ? AND strategy_id = ? AND candidate_symbols_csv = ?
                AND cash = ? AND cost_model = ? AND narrative_source = ? AND params_json = ? AND timeframe = ?
            """.formatted(SELECT_COLUMNS);

    private static final String FIND_BY_ID = """
            SELECT %s FROM fact_report_run WHERE id = ?
            """.formatted(SELECT_COLUMNS);

    private static final String INSERT = """
            INSERT INTO fact_report_run (
                benchmark_instrument_id, strategy_id, candidate_symbols_csv, cash, cost_model,
                narrative_source, params_json, timeframe, table_json, narrative
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public ReportRunFactRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public Optional<ReportRunRecord> findByKey(
            long benchmarkInstrumentId, long strategyId, String candidateSymbolsCsv, double cash,
            String costModel, String narrativeSource, String paramsJson, String timeframe) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_KEY)) {
            statement.setLong(1, benchmarkInstrumentId);
            statement.setLong(2, strategyId);
            statement.setString(3, candidateSymbolsCsv);
            statement.setDouble(4, cash);
            statement.setString(5, costModel);
            statement.setString(6, narrativeSource);
            statement.setString(7, paramsJson);
            statement.setString(8, timeframe);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new WarehouseException("failed to look up report run by cache key", e);
        }
    }

    public Optional<ReportRunRecord> findById(long id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new WarehouseException("failed to look up report run " + id, e);
        }
    }

    public ReportRunRecord insert(
            long benchmarkInstrumentId, long strategyId, String candidateSymbolsCsv, double cash,
            String costModel, String narrativeSource, String paramsJson, String timeframe,
            String tableJson, String narrative) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, benchmarkInstrumentId);
            statement.setLong(2, strategyId);
            statement.setString(3, candidateSymbolsCsv);
            statement.setDouble(4, cash);
            statement.setString(5, costModel);
            statement.setString(6, narrativeSource);
            statement.setString(7, paramsJson);
            statement.setString(8, timeframe);
            statement.setString(9, tableJson);
            statement.setString(10, narrative);
            statement.executeUpdate();
            long id;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
            return findById(id).orElseThrow(() -> new WarehouseException("insert did not persist row " + id, null));
        } catch (SQLException e) {
            throw new WarehouseException("failed to insert report run", e);
        }
    }

    private static ReportRunRecord map(ResultSet resultSet) throws SQLException {
        return new ReportRunRecord(
                resultSet.getLong("id"),
                resultSet.getLong("benchmark_instrument_id"),
                resultSet.getLong("strategy_id"),
                resultSet.getString("candidate_symbols_csv"),
                resultSet.getDouble("cash"),
                resultSet.getString("cost_model"),
                resultSet.getString("narrative_source"),
                resultSet.getString("params_json"),
                resultSet.getString("timeframe"),
                resultSet.getString("table_json"),
                resultSet.getString("narrative"),
                resultSet.getTimestamp("created_at").toInstant());
    }
}
