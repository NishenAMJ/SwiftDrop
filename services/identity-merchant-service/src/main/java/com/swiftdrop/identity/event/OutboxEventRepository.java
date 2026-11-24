package com.swiftdrop.identity.event;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * The oldest unpublished events, locked until the calling transaction ends. Rows another
     * service instance is already sending are skipped, so two instances never send the same batch.
     */
    @Query(value = """
            SELECT * FROM identity_merchant.outbox_events
            WHERE published_at IS NULL
            ORDER BY occurred_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockOldestUnpublished(int limit);
}
