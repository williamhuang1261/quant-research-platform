package io.github.williamhuang1261.qrp.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class InstrumentDimensionRepositoryTest {

    private final DataSource dataSource = WarehouseDataSourceFactory.create();
    private final InstrumentDimensionRepository repository = new InstrumentDimensionRepository(dataSource);

    @Test
    void findOrCreateIsIdempotentForTheSameSymbol() {
        String symbol = TestSymbols.unique("TS-");

        long first = repository.findOrCreate(symbol, "USD", "EQUITY");
        long second = repository.findOrCreate(symbol, "USD", "EQUITY");

        assertEquals(first, second, "the same symbol must resolve to the same row, not a duplicate");
    }

    @Test
    void differentSymbolsGetDifferentRows() {
        String symbolA = TestSymbols.unique("TSA-");
        String symbolB = TestSymbols.unique("TSB-");

        long a = repository.findOrCreate(symbolA, "USD", "EQUITY");
        long b = repository.findOrCreate(symbolB, "USD", "EQUITY");

        assertNotEquals(a, b);
    }

    @Test
    void aRepeatCallUpdatesCurrencyAndAssetClassRatherThanIgnoringTheChange() {
        String symbol = TestSymbols.unique("TS-");
        repository.findOrCreate(symbol, "USD", "EQUITY");

        long id = repository.findOrCreate(symbol, "CAD", "ETF");

        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT currency, asset_class FROM dim_instrument WHERE id = ?")) {
            statement.setLong(1, id);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                assertEquals("CAD", resultSet.getString("currency"));
                assertEquals("ETF", resultSet.getString("asset_class"));
            }
        } catch (java.sql.SQLException e) {
            throw new WarehouseException("verification query failed", e);
        }
    }
}
