package com.swiftdrop.parcel_pricing_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.Statement;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try (Connection conn = flyway.getConfiguration().getDataSource().getConnection();
                 Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate(
                    "DELETE FROM parcel_pricing.flyway_schema_history WHERE version = '3' AND checksum = -1851546735"
                );
                if (deleted > 0) {
                    log.info("Cleaned stale Flyway history entry for version 3 (pre-renumbering checksum).");
                }
            } catch (Exception e) {
                log.debug("Pre-migration cleanup skipped: {}", e.getMessage());
            }
            flyway.migrate();
        };
    }
}
