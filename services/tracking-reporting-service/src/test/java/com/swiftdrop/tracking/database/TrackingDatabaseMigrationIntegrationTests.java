package com.swiftdrop.tracking.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class TrackingDatabaseMigrationIntegrationTests {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void flywayAppliedEveryTrackingMigration() {
        List<String> versions = jdbcClient.sql("""
                        SELECT version
                        FROM tracking_reporting.flyway_schema_history
                        WHERE success = true
                        ORDER BY installed_rank
                        """)
                .query((resultSet, rowNumber) -> resultSet.getString("version"))
                .list();

        assertThat(versions).containsExactly("1", "2");
    }

    @Test
    void migrationsCreatedTheOwnedTablesConstraintsAndIndexes() {
        List<String> tables = jdbcClient.sql("""
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'tracking_reporting'
                          AND table_name IN ('tracking_records', 'tracking_history', 'processed_events')
                        ORDER BY table_name
                        """)
                .query((resultSet, rowNumber) -> resultSet.getString("table_name"))
                .list();
        List<String> constraints = jdbcClient.sql("""
                        SELECT constraint_name
                        FROM information_schema.table_constraints
                        WHERE constraint_schema = 'tracking_reporting'
                          AND constraint_name IN (
                              'uq_tracking_records_tracking_number',
                              'fk_tracking_history_record',
                              'processed_events_pkey')
                        ORDER BY constraint_name
                        """)
                .query((resultSet, rowNumber) -> resultSet.getString("constraint_name"))
                .list();
        List<String> indexes = jdbcClient.sql("""
                        SELECT indexname
                        FROM pg_indexes
                        WHERE schemaname = 'tracking_reporting'
                          AND indexname IN (
                              'idx_tracking_records_status_updated',
                              'idx_tracking_history_parcel_timeline',
                              'idx_processed_events_processed_at',
                              'idx_tracking_history_event_type_time')
                        ORDER BY indexname
                        """)
                .query((resultSet, rowNumber) -> resultSet.getString("indexname"))
                .list();

        assertThat(tables).containsExactly(
                "processed_events",
                "tracking_history",
                "tracking_records");
        assertThat(constraints).containsExactly(
                "fk_tracking_history_record",
                "processed_events_pkey",
                "uq_tracking_records_tracking_number");
        assertThat(indexes).containsExactly(
                "idx_processed_events_processed_at",
                "idx_tracking_history_event_type_time",
                "idx_tracking_history_parcel_timeline",
                "idx_tracking_records_status_updated");
    }
}
