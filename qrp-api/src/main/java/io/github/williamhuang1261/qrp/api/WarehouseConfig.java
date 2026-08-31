package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.warehouse.WarehouseDataSourceFactory;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one Spring-aware seam onto {@code qrp-warehouse}: a migrated
 * {@link DataSource} bean built by {@link WarehouseDataSourceFactory}, which
 * decides embedded-vs-external per its own rules. Every repository is a
 * plain constructor call over this bean, not a second framework layer.
 */
@Configuration
class WarehouseConfig {

    @Bean
    DataSource warehouseDataSource() {
        return WarehouseDataSourceFactory.create();
    }
}
