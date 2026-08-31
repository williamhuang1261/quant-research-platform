package io.github.williamhuang1261.qrp.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class StrategyDimensionRepositoryTest {

    private final DataSource dataSource = WarehouseDataSourceFactory.create();
    private final StrategyDimensionRepository repository = new StrategyDimensionRepository(dataSource);

    @Test
    void findOrCreateIsIdempotentForTheSameName() {
        String name = "test-strategy-" + UUID.randomUUID();

        long first = repository.findOrCreate(name);
        long second = repository.findOrCreate(name);

        assertEquals(first, second);
    }
}
