package com.swiftdrop.parcel_pricing_service.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Brings databases created before the migration renumbering in line with the current files.
 *
 * <p>Old layout (databases created before PR #49): V7 = booking payment context,
 * V8 = add cancellation fields. Current files: V7 = add price multiplier column,
 * V8/V9 = booking payment context, V10 = add cancellation fields, V11 = booking lifecycle.
 *
 * <p>For an old database the history rows are renumbered to the versions those changes now
 * have, Flyway's repair aligns the stored checksums with the files, and the missing V7 and V9
 * run out of order (V9 is an idempotent copy of V8). No table data is touched. A database that
 * already follows the current layout, or a new one, is migrated normally.
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);
    private static final String HISTORY = "parcel_pricing.flyway_schema_history";

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            boolean legacyLayout = false;
            try (Connection conn = flyway.getConfiguration().getDataSource().getConnection();
                 Statement stmt = conn.createStatement()) {
                if (historyExists(stmt)) {
                    int deleted = stmt.executeUpdate(
                            "DELETE FROM " + HISTORY + " WHERE version = '3' AND checksum = -1851546735");
                    if (deleted > 0) log.info("Removed stale Flyway history entry for version 3.");
                    legacyLayout = renumberLegacyHistory(stmt);
                }
            } catch (Exception e) {
                log.warn("Pre-migration history check skipped: {}", e.getMessage());
            }

            if (legacyLayout) {
                Flyway upgrade = Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .outOfOrder(true)
                        .load();
                upgrade.repair();
                upgrade.migrate();
                log.info("Database upgraded from the old migration numbering.");
            } else {
                flyway.migrate();
            }
        };
    }

    private static boolean historyExists(Statement stmt) throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT to_regclass('" + HISTORY + "') IS NOT NULL")) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    /** @return true when the old layout was found and renumbered */
    private static boolean renumberLegacyHistory(Statement stmt) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT "
                + "count(*) FILTER (WHERE version = '7' AND description = 'booking payment context'), "
                + "count(*) FILTER (WHERE version = '7'), "
                + "count(*) FILTER (WHERE version = '10') FROM " + HISTORY)) {
            rs.next();
            boolean oldLayout = rs.getInt(1) > 0;
            // A previous upgrade that stopped half way: renumbered, but V7 not applied yet.
            boolean unfinishedUpgrade = rs.getInt(2) == 0 && rs.getInt(3) > 0;
            if (unfinishedUpgrade) {
                log.info("Finishing an earlier upgrade from the old migration numbering.");
                return true;
            }
            if (!oldLayout) return false;
        }
        // Order matters: move the old V8 out of the way before the old V7 takes number 8.
        stmt.executeUpdate("UPDATE " + HISTORY + " SET version = '10' "
                + "WHERE version = '8' AND description = 'add cancellation fields'");
        stmt.executeUpdate("UPDATE " + HISTORY + " SET version = '8' "
                + "WHERE version = '7' AND description = 'booking payment context'");
        log.info("Renumbered Flyway history from the old layout (V7 -> V8, V8 -> V10).");
        return true;
    }
}
