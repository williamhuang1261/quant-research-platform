package io.github.williamhuang1261.qrp.warehouse;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class WarehouseDataSourceFactoryTest {

    @Test
    void createsAMigratedDataSourceBackedByARealPostgresServer() throws Exception {
        DataSource dataSource = WarehouseDataSourceFactory.create();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT version()")) {
            resultSet.next();
            String version = resultSet.getString(1);
            assertTrue(version.contains("PostgreSQL"), "expected a real PostgreSQL server, got: " + version);
        }
    }

    @Test
    void migrationCreatedEveryTable() throws Exception {
        DataSource dataSource = WarehouseDataSourceFactory.create();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' ORDER BY table_name
                        """)) {
            java.util.List<String> tables = new java.util.ArrayList<>();
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
            assertTrue(tables.containsAll(java.util.List.of(
                    "dim_instrument", "dim_strategy", "fact_price_bar", "fact_backtest_run", "fact_report_run")),
                    "expected the full schema, got: " + tables);
        }
    }
}
