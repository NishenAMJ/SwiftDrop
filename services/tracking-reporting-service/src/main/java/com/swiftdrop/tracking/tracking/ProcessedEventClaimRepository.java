package com.swiftdrop.tracking.tracking;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventClaimRepository {

    private final JdbcClient jdbcClient;

    public ProcessedEventClaimRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean tryClaim(UUID eventId, String eventType) {
        int inserted = jdbcClient.sql("""
                        INSERT INTO tracking_reporting.processed_events (event_id, event_type)
                        VALUES (:eventId, :eventType)
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .update();
        return inserted == 1;
    }
}
